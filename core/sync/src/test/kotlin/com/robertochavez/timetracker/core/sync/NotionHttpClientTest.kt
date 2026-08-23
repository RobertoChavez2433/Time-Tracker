package com.robertochavez.timetracker.core.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotionHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: NotionHttpClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = NotionHttpClient("test-token", server.url("").toString().removeSuffix("/"))
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `date lookup uses Date property rather than formula Day Key`() = runBlocking {
        server.enqueue(json("{\"results\":[{\"id\":\"daily\"}]}"))
        assertEquals("daily", client.findPageByDate("daily-log", "Date", "2026-08-23"))
        val request = server.takeRequest()
        val body = JSONObject(request.body.readUtf8())
        assertEquals("Date", body.getJSONObject("filter").getString("property"))
        assertEquals("2026-08-23", body.getJSONObject("filter").getJSONObject("date").getString("equals"))
    }

    @Test fun `relation merge preserves manual projects`() = runBlocking {
        server.enqueue(json("{\"properties\":{\"Projects\":{\"relation\":[{\"id\":\"manual\"}],\"has_more\":false}}}"))
        server.enqueue(json("{}"))
        client.mergeRelation("daily", "Projects", setOf("automatic"))
        server.takeRequest()
        val patch = JSONObject(server.takeRequest().body.readUtf8())
        val relations = patch.getJSONObject("properties").getJSONObject("Projects").getJSONArray("relation")
        val ids = (0 until relations.length()).map { relations.getJSONObject(it).getString("id") }.toSet()
        assertEquals(setOf("manual", "automatic"), ids)
    }

    @Test fun `Notion retries 429 using retry after`() = runBlocking {
        server.enqueue(json("{\"message\":\"slow down\"}", 429).addHeader("Retry-After", "0"))
        server.enqueue(json("{\"results\":[]}"))
        assertEquals(null, client.findPageByText("projects", "Project Number", "TEST-001"))
        assertEquals(2, server.requestCount)
        assertTrue(server.takeRequest().getHeader("Authorization")!!.startsWith("Bearer "))
    }

    private fun json(body: String, code: Int = 200) = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
