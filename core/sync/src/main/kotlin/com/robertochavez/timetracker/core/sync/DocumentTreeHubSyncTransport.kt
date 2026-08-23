package com.robertochavez.timetracker.core.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentTreeHubSyncTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: HubSyncSettingsStore,
    private val clock: Clock,
) : HubSyncTransport {
    override suspend fun transfer(records: List<HubSyncRecordEntity>): HubTransferResult = withContext(Dispatchers.IO) {
        require(records.isNotEmpty()) { "No records to transfer." }
        val root = configuredRoot()
        val incoming = root.directory("Incoming").directory("Time")
        val transferId = UUID.randomUUID().toString()
        val envelope = JSONObject()
            .put("schemaVersion", 1)
            .put("transferId", transferId)
            .put("createdAt", Instant.now(clock).toString())
            .put("records", JSONArray(records.map { JSONObject(it.deterministicJson) }))
            .toString()
        val temporary = incoming.createFile(MIME_JSON, ".$transferId.json.partial")
            ?: error("The selected document provider could not create the outbox file.")
        context.contentResolver.openOutputStream(temporary.uri, "wt")!!.bufferedWriter().use { it.write(envelope) }
        check(temporary.renameTo("$transferId.json")) { "The selected provider cannot atomically rename outbox files." }
        HubTransferResult(transferId, emptySet())
    }

    override suspend fun readReceipts(): List<HubReceipt> = withContext(Dispatchers.IO) {
        val receiptDirectory = configuredRoot().directory("Receipts")
        receiptDirectory.listFiles().filter { it.isFile && it.name?.endsWith(".json") == true }.mapNotNull { file ->
            runCatching {
                val json = context.contentResolver.openInputStream(file.uri)!!.bufferedReader().use { JSONObject(it.readText()) }
                val accepted = json.optJSONArray("acceptedSourceIds").strings().toSet()
                val rejected = buildMap {
                    val values = json.optJSONObject("rejected") ?: JSONObject()
                    values.keys().forEach { key -> put(key, values.optString(key, "Rejected by bridge")) }
                }
                HubReceipt(
                    transferId = json.getString("transferId"),
                    acceptedSourceIds = accepted,
                    rejected = rejected,
                    receivedAt = Instant.parse(json.getString("createdAt")),
                ).also { file.delete() }
            }.getOrNull()
        }
    }

    @Suppress("ReturnCount")
    fun runBasicProviderCheck(): Boolean {
        val root = runCatching { configuredRoot() }.getOrNull() ?: return false
        val probe = root.createFile(MIME_JSON, ".work-hub-provider-test.partial") ?: return false
        val wrote = runCatching {
            context.contentResolver.openOutputStream(probe.uri, "wt")!!.use { it.write("{}".toByteArray()) }
            probe.renameTo(".work-hub-provider-test.json")
        }.getOrDefault(false)
        val readable =
            wrote && runCatching { context.contentResolver.openInputStream(probe.uri)?.use { it.read() } != null }.getOrDefault(false)
        probe.delete()
        return wrote && readable
    }

    private fun configuredRoot(): DocumentFile {
        val uri = settingsStore.current().outboxTreeUri?.let(Uri::parse) ?: error("Choose the Work Hub Outbox folder first.")
        return DocumentFile.fromTreeUri(context, uri)?.takeIf { it.canRead() && it.canWrite() }
            ?: error("The Work Hub Outbox folder permission is unavailable.")
    }

    private fun DocumentFile.directory(name: String): DocumentFile = findFile(name) ?: createDirectory(name)
        ?: error("Could not create $name in the Work Hub Outbox.")

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map(::getString)

    private companion object {
        const val MIME_JSON = "application/json"
    }
}
