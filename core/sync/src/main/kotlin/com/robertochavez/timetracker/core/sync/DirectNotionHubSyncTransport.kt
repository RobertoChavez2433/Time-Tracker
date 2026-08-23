package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectNotionHubSyncTransport @Inject constructor(
    private val settingsStore: HubSyncSettingsStore,
    private val tokenStore: NotionTokenStore,
    private val clock: Clock,
) : HubSyncTransport {
    override suspend fun transfer(records: List<HubSyncRecordEntity>): HubTransferResult = withContext(Dispatchers.IO) {
        val token = tokenStore.read() ?: error("Enter a Notion integration token first.")
        val settings = settingsStore.current()
        require(settings.transportKind == HubTransportKind.DIRECT_NOTION) { "Direct Notion fallback is not enabled." }
        val client = NotionHttpClient(token)
        client.validateDataSource(
            settings.dailyLogDatabaseId,
            mapOf(
                "Entry" to "title",
                "Date" to "date",
                "Projects" to "relation",
                "Time Summary" to "rich_text",
                "Total Work Minutes" to "number",
                "Drive Minutes" to "number",
                "Site Minutes" to "number",
                "Idle Minutes" to "number",
                "Needs Review Minutes" to "number",
                "Miles" to "number",
                "Time Status" to "select",
                "Last Time Sync" to "date",
            ),
        )
        client.validateDataSource(
            settings.projectsDatabaseId,
            mapOf("Project Number" to "rich_text", "Time Tracker Worksite ID" to "rich_text"),
        )
        client.validateDataSource(
            settings.timeRecordsDatabaseId,
            mapOf(
                "Record" to "title",
                "Project" to "relation",
                "Daily Note" to "relation",
                "Source ID" to "rich_text",
            ),
        )
        val accepted = mutableSetOf<String>()
        val rejected = mutableMapOf<String, String>()
        records.forEach { record ->
            runCatching {
                val payload = JSONObject(record.deterministicJson)
                when (record.payloadType) {
                    HubPayloadType.DAILY_SUMMARY.name -> upsertDailySummary(client, settings, payload)
                    HubPayloadType.PROJECT_DAY.name -> upsertProjectDay(client, settings, payload)
                    else -> error("Direct Notion sync only accepts Time Tracker summary payloads.")
                }
            }.onSuccess {
                accepted += record.sourceId
            }.onFailure { error ->
                rejected[record.sourceId] = error.message.orEmpty().replace(Regex("[\\r\\n]+"), " ").take(240)
            }
        }
        HubTransferResult(UUID.randomUUID().toString(), accepted, rejected)
    }

    private suspend fun upsertDailySummary(client: NotionHttpClient, settings: HubSyncSettings, payload: JSONObject) {
        require(settings.dailyLogDatabaseId.isNotBlank()) { "Daily Log data source ID is required." }
        val date = payload.getString("date")
        val pageId = client.findPageByDate(settings.dailyLogDatabaseId, "Date", date)
            ?: client.createPage(
                settings.dailyLogDatabaseId,
                JSONObject()
                    .put("Entry", title("$date — Daily Notes"))
                    .put("Date", date(date)),
                applyDefaultTemplate = true,
            )
        client.updatePage(
            pageId,
            JSONObject()
                .put("Time Summary", richText(timeSummary(payload)))
                .put("Total Work Minutes", number(payload.getLong("totalWorkMinutes")))
                .put("Drive Minutes", number(payload.getLong("driveMinutes")))
                .put("Site Minutes", number(payload.getLong("siteMinutes")))
                .put("Idle Minutes", number(payload.getLong("idleMinutes")))
                .put("Needs Review Minutes", number(payload.getLong("needsReviewMinutes")))
                .put("Miles", number(payload.getString("drivenMiles").toDouble()))
                .put(
                    "Time Status",
                    select(
                        if (java.time.LocalDate.parse(date) ==
                            java.time.LocalDate.now(clock)
                        ) {
                            "Provisional"
                        } else {
                            "Final"
                        },
                    ),
                )
                .put("Last Time Sync", date(java.time.Instant.now(clock).toString())),
        )
    }

    private suspend fun upsertProjectDay(client: NotionHttpClient, settings: HubSyncSettings, payload: JSONObject) {
        require(settings.timeRecordsDatabaseId.isNotBlank()) { "Time Records data source ID is required." }
        require(settings.projectsDatabaseId.isNotBlank()) { "Projects data source ID is required." }
        val sourceId = payload.getString("sourceId")
        val projectNumber = payload.getString("projectNumber")
        val projectId = client.findPageByText(settings.projectsDatabaseId, "Project Number", projectNumber)
            ?: error("Unknown project number $projectNumber. Nothing was sent to Notion.")
        client.ensureTextProperty(projectId, "Time Tracker Worksite ID", payload.getString("workLocationId"))
        val dailyNoteId = client.findPageByDate(settings.dailyLogDatabaseId, "Date", payload.getString("date"))
            ?: client.createPage(
                settings.dailyLogDatabaseId,
                JSONObject()
                    .put("Entry", title("${payload.getString("date")} — Daily Notes"))
                    .put("Date", date(payload.getString("date"))),
                applyDefaultTemplate = true,
            )
        client.mergeRelation(dailyNoteId, "Projects", setOf(projectId))
        val properties = JSONObject()
            .put("Record", title("${payload.getString("date")} — $projectNumber — ${payload.getString("siteLabel")}"))
            .put("Date", date(payload.getString("date")))
            .put("Project", relation(projectId))
            .put("Daily Note", relation(dailyNoteId))
            .put("Project Number", richText(projectNumber))
            .put("Worksite ID", richText(payload.getString("workLocationId")))
            .put("Worksite", richText(payload.getString("siteLabel")))
            .put("First Arrival", date(payload.getString("firstArrival")))
            .put("Last Departure", date(payload.getString("lastDeparture")))
            .put("Onsite Minutes", number(payload.getLong("onsiteMinutes")))
            .put("Visit Count", number(payload.getInt("visitCount")))
            .put("Status", select(payload.getString("status").toNotionStatus()))
            .put("Source ID", richText(sourceId))
            .put("Last Synced", date(java.time.Instant.now(clock).toString()))
        val existing = client.findPageByText(settings.timeRecordsDatabaseId, "Source ID", sourceId)
        if (existing == null) client.createPage(settings.timeRecordsDatabaseId, properties) else client.updatePage(existing, properties)
    }

    private fun timeSummary(value: JSONObject): String = buildString {
        append("Work ${value.getLong("totalWorkMinutes")}m · Drive ${value.getLong("driveMinutes")}m")
        append(" · Site ${value.getLong("siteMinutes")}m · Idle ${value.getLong("idleMinutes")}m")
        append(" · Review ${value.getLong("needsReviewMinutes")}m · Miles ${value.getString("drivenMiles")}")
    }

    private fun title(value: String) = JSONObject().put("title", richTextArray(value))
    private fun richText(value: String) = JSONObject().put("rich_text", richTextArray(value))
    private fun richTextArray(value: String) =
        JSONArray().put(JSONObject().put("type", "text").put("text", JSONObject().put("content", value)))
    private fun date(value: String) = JSONObject().put("date", JSONObject().put("start", value))
    private fun number(value: Number) = JSONObject().put("number", value)
    private fun select(value: String) = JSONObject().put("select", JSONObject().put("name", value))
    private fun relation(pageId: String) = JSONObject().put("relation", JSONArray().put(JSONObject().put("id", pageId)))
    private fun String.toNotionStatus() = split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

internal class NotionHttpClient(private val token: String, private val baseUrl: String = "https://api.notion.com") {
    private val httpClient = OkHttpClient()
    suspend fun findPageByText(dataSourceId: String, property: String, value: String): String? =
        findPage(dataSourceId, JSONObject().put("property", property).put("rich_text", JSONObject().put("equals", value)))

    suspend fun findPageByDate(dataSourceId: String, property: String, value: String): String? =
        findPage(dataSourceId, JSONObject().put("property", property).put("date", JSONObject().put("equals", value)))

    private suspend fun findPage(dataSourceId: String, filter: JSONObject): String? {
        val body = JSONObject().put("filter", filter).put("page_size", 2)
        val results = request("POST", "/v1/data_sources/$dataSourceId/query", body).getJSONArray("results")
        check(results.length() <= 1) { "Duplicate Notion record found for ${filter.getString("property")}." }
        return results.optJSONObject(0)?.optString("id")
    }

    suspend fun mergeRelation(pageId: String, property: String, incomingIds: Set<String>) {
        val page = request("GET", "/v1/pages/$pageId", null)
        val relationProperty = page.getJSONObject("properties").getJSONObject(property)
        check(!relationProperty.optBoolean("has_more")) { "$property contains too many relations to merge safely." }
        val existingIds = relationProperty.optJSONArray("relation")?.let { values ->
            (0 until values.length()).map { values.getJSONObject(it).getString("id") }
        }.orEmpty()
        val merged = (existingIds + incomingIds).distinct()
        updatePage(
            pageId,
            JSONObject().put(
                property,
                JSONObject().put(
                    "relation",
                    JSONArray(merged.map { JSONObject().put("id", it) }),
                ),
            ),
        )
    }

    suspend fun ensureTextProperty(pageId: String, property: String, expected: String) {
        val page = request("GET", "/v1/pages/$pageId", null)
        val richText = page.getJSONObject("properties").getJSONObject(property).optJSONArray("rich_text")
        val current = richText?.let { values ->
            (0 until values.length()).joinToString("") { values.getJSONObject(it).optString("plain_text") }
        }.orEmpty()
        check(current.isBlank() || current == expected) {
            "Project is already linked to a different Time Tracker worksite."
        }
        if (current.isBlank()) updatePage(pageId, JSONObject().put(property, richText(expected)))
    }

    suspend fun validateDataSource(dataSourceId: String, required: Map<String, String>) {
        require(dataSourceId.isNotBlank()) { "Notion data source IDs are required." }
        val properties = request("GET", "/v1/data_sources/$dataSourceId", null).getJSONObject("properties")
        required.forEach { (name, type) ->
            check(properties.optJSONObject(name)?.optString("type") == type) {
                "Notion data source $dataSourceId requires $name ($type)."
            }
        }
    }

    suspend fun createPage(dataSourceId: String, properties: JSONObject, applyDefaultTemplate: Boolean = false): String {
        val body = JSONObject()
            .put("parent", JSONObject().put("type", "data_source_id").put("data_source_id", dataSourceId))
            .put("properties", properties)
        if (applyDefaultTemplate) {
            body.put("template", JSONObject().put("type", "default").put("timezone", "America/New_York"))
        }
        return request("POST", "/v1/pages", body).getString("id")
    }

    suspend fun updatePage(pageId: String, properties: JSONObject) {
        request("PATCH", "/v1/pages/$pageId", JSONObject().put("properties", properties))
    }

    internal suspend fun request(method: String, path: String, body: JSONObject?): JSONObject {
        var attempt = 0
        while (true) {
            attempt++
            val result = try {
                executeRequest(method, path, body)
            } catch (error: IOException) {
                if (attempt >= MAX_HTTP_ATTEMPTS) throw error
                waitBeforeRetry(attempt, null)
                continue
            }
            if (result.successful) return result.json()
            if (!result.retryable || attempt >= MAX_HTTP_ATTEMPTS) error(result.errorMessage())
            waitBeforeRetry(attempt, result.retryAfterSeconds)
        }
    }

    private fun executeRequest(method: String, path: String, body: JSONObject?): NotionResponse {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", "2026-03-11")
            .method(method, body?.toString()?.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return httpClient.newCall(request).execute().use { result ->
            NotionResponse(
                code = result.code,
                body = result.body?.string().orEmpty(),
                retryAfterSeconds = result.header("Retry-After")?.toLongOrNull(),
            )
        }
    }

    private suspend fun waitBeforeRetry(attempt: Int, retryAfterSeconds: Long?) {
        val seconds = retryAfterSeconds ?: (1L shl attempt)
        delay(seconds.coerceAtMost(MAX_RETRY_SECONDS) * 1_000)
    }

    private fun richText(value: String) = JSONObject().put(
        "rich_text",
        JSONArray().put(JSONObject().put("type", "text").put("text", JSONObject().put("content", value))),
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_HTTP_ATTEMPTS = 5
        const val MAX_RETRY_SECONDS = 30L
    }
}

private data class NotionResponse(val code: Int, val body: String, val retryAfterSeconds: Long?) {
    val successful get() = code in 200..299
    val retryable get() = code == 429 || code >= 500
    fun json() = if (body.isBlank()) JSONObject() else JSONObject(body)
    fun errorMessage(): String {
        val message = runCatching { JSONObject(body).optString("message") }
            .getOrNull().orEmpty().ifBlank { "request rejected" }
        return "Notion request failed ($code): $message"
    }
}
