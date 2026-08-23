package com.robertochavez.timetracker.core.sync

import com.robertochavez.timetracker.core.database.entity.HubSyncRecordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
        val accepted = mutableSetOf<String>()
        records.forEach { record ->
            val payload = JSONObject(record.deterministicJson)
            when (record.payloadType) {
                HubPayloadType.DAILY_SUMMARY.name -> upsertDailySummary(client, settings, payload)
                HubPayloadType.PROJECT_DAY.name -> upsertProjectDay(client, settings, payload)
                else -> error("Direct Notion sync only accepts Time Tracker summary payloads.")
            }
            accepted += record.sourceId
        }
        HubTransferResult(UUID.randomUUID().toString(), accepted)
    }

    private suspend fun upsertDailySummary(client: NotionHttpClient, settings: HubSyncSettings, payload: JSONObject) {
        require(settings.dailyLogDatabaseId.isNotBlank()) { "Daily Log data source ID is required." }
        val date = payload.getString("date")
        val pageId = client.findPage(settings.dailyLogDatabaseId, "Day Key", date)
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
                .put("Day Key", richText(date))
                .put("Time Summary", richText(timeSummary(payload)))
                .put("Total Work", number(payload.getLong("totalWorkMinutes")))
                .put("Drive", number(payload.getLong("driveMinutes")))
                .put("Site", number(payload.getLong("siteMinutes")))
                .put("Idle", number(payload.getLong("idleMinutes")))
                .put("Needs Review", number(payload.getLong("needsReviewMinutes")))
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
        val projectId = client.findPage(settings.projectsDatabaseId, "Project Number", projectNumber)
            ?: error("Unknown project number $projectNumber. Nothing was sent to Notion.")
        val dailyNoteId = client.findPage(settings.dailyLogDatabaseId, "Day Key", payload.getString("date"))
            ?: error("The Daily Note must be synchronized before its project time record.")
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
            .put("Status", select(payload.getString("status").replaceFirstChar(Char::uppercase)))
            .put("Source ID", richText(sourceId))
            .put("Last Synced", date(java.time.Instant.now(clock).toString()))
        val existing = client.findPage(settings.timeRecordsDatabaseId, "Source ID", sourceId)
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
}

private class NotionHttpClient(private val token: String) {
    suspend fun findPage(dataSourceId: String, property: String, value: String): String? {
        val body = JSONObject().put(
            "filter",
            JSONObject().put("property", property).put("rich_text", JSONObject().put("equals", value)),
        ).put("page_size", 2)
        val results = request("POST", "/v1/data_sources/$dataSourceId/query", body).getJSONArray("results")
        check(results.length() <= 1) { "Duplicate $property '$value' found in Notion." }
        return results.optJSONObject(0)?.optString("id")
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

    private suspend fun request(method: String, path: String, body: JSONObject): JSONObject {
        var attempt = 0
        while (true) {
            attempt++
            val connection = URL("https://api.notion.com$path").openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Notion-Version", "2026-03-11")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (code in 200..299) return JSONObject(response)
            if ((code == 429 || code >= 500) && attempt < 5) {
                val retrySeconds = connection.getHeaderField("Retry-After")?.toLongOrNull() ?: (1L shl attempt)
                delay(retrySeconds.coerceAtMost(30) * 1_000)
                continue
            }
            error("Notion request failed ($code): ${JSONObject(response).optString("message", "request rejected")}")
        }
    }
}
