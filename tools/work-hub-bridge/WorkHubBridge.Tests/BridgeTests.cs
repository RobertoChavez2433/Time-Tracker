using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using WorkHubBridge;
using Xunit;

namespace WorkHubBridge.Tests;

public sealed class BridgeTests
{
    [Fact]
    public void Source_key_is_deterministic_and_mailbox_scoped()
    {
        Assert.Equal(SourceKeyFactory.Email("mailbox", "<id@example>"), SourceKeyFactory.Email("mailbox", "<id@example>"));
        Assert.NotEqual(SourceKeyFactory.Email("one", "<id@example>"), SourceKeyFactory.Email("two", "<id@example>"));
    }

    [Fact]
    public void Source_key_is_derived_by_bridge_and_rejects_mismatch()
    {
        using var valid = JsonDocument.Parse("""{"type":"email","mailboxKey":"work@example.com","internetMessageId":"<id@example>"}""");
        Assert.Equal(SourceKeyFactory.Email("work@example.com", "<id@example>"), SourceKeyFactory.ForRecord(valid.RootElement));
        using var invalid = JsonDocument.Parse("""{"type":"email","mailboxKey":"work@example.com","internetMessageId":"<id@example>","sourceId":"wrong"}""");
        Assert.Throws<InvalidDataException>(() => SourceKeyFactory.ForRecord(invalid.RootElement));
    }

    [Fact]
    public void Email_cleaner_handles_protected_and_truncated_messages()
    {
        Assert.Equal("Protected", EmailBodyCleaner.Clean("secret", "protected").Status);
        var result = EmailBodyCleaner.Clean(new string('x', 100_100), "available");
        Assert.Equal("Truncated", result.Status);
        Assert.Equal(100_000, result.Body.Length);
    }

    [Fact]
    public void Envelope_validator_rejects_location_details()
    {
        var directory = Directory.CreateTempSubdirectory();
        try
        {
            var path = Path.Combine(directory.FullName, "payload.json");
            File.WriteAllText(path, $$"""{"schemaVersion":1,"transferId":"{{Guid.NewGuid()}}","createdAt":"2026-08-20T12:00:00Z","records":[{"sourceId":"day:v1:2026-08-20","type":"dailySummary","latitude":1}]}""");
            Assert.Throws<InvalidDataException>(() => new EnvelopeValidator().Read(path));
        }
        finally { directory.Delete(true); }
    }

    [Fact]
    public void Envelope_validator_accepts_contract_valid_email_without_supplied_source_id()
    {
        var directory = Directory.CreateTempSubdirectory();
        try
        {
            var path = Path.Combine(directory.FullName, "payload.json");
            File.WriteAllText(path, $$"""{"schemaVersion":1,"transferId":"{{Guid.NewGuid()}}","createdAt":"2026-08-20T12:00:00Z","records":[{"type":"email","mailboxKey":"work","projectNumbers":["TEST-001"],"outlookFolder":"Projects/Test","internetMessageId":"<id@example>","receivedAt":"2026-08-20T12:00:00Z","from":"sender@example.com","to":["me@example.com"],"subject":"Test","preview":"Preview","bodyStatus":"available"}]}""");
            var envelope = new EnvelopeValidator().Read(path);
            Assert.Single(envelope.Records);
            Assert.Equal(SourceKeyFactory.Email("work", "<id@example>"), SourceKeyFactory.ForRecord(envelope.Records[0]));
        }
        finally { directory.Delete(true); }
    }

    [Fact]
    public async Task Project_resolver_rejects_unknown_project()
    {
        using var client = Client(_ => Json(HttpStatusCode.OK, "{\"results\":[]}"));
        await Assert.ThrowsAsync<InvalidDataException>(() => new ProjectResolver(client, "projects").ResolveAsync("UNKNOWN", CancellationToken.None));
    }

    [Fact]
    public async Task Notion_client_retries_rate_limit_without_logging_body()
    {
        var calls = 0;
        using var client = Client(_ => ++calls == 1
            ? Json(HttpStatusCode.TooManyRequests, "{\"message\":\"slow down\"}", retryAfter: "0")
            : Json(HttpStatusCode.OK, "{\"results\":[]}"));
        await Assert.ThrowsAsync<InvalidDataException>(() =>
            new ProjectResolver(client, "projects").ResolveAsync("UNKNOWN", CancellationToken.None));
        Assert.Equal(2, calls);
    }

    [Fact]
    public async Task Notion_client_retries_server_errors()
    {
        var calls = 0;
        using var client = Client(_ => ++calls == 1
            ? Json(HttpStatusCode.ServiceUnavailable, "{\"message\":\"temporary\"}")
            : Json(HttpStatusCode.OK, "{\"results\":[]}"));
        await Assert.ThrowsAsync<InvalidDataException>(() =>
            new ProjectResolver(client, "projects").ResolveAsync("UNKNOWN", CancellationToken.None));
        Assert.Equal(2, calls);
    }

    [Fact]
    public async Task Daily_note_query_uses_Date_instead_of_formula_Day_Key()
    {
        JsonObject? query = null;
        using var client = Client(request =>
        {
            query = ReadBody(request);
            return Json(HttpStatusCode.OK, "{\"results\":[{\"id\":\"daily\"}]}");
        });
        var id = await new DailyNoteService(client, "daily-log").EnsureAsync(new DateOnly(2026, 8, 20), CancellationToken.None);
        Assert.Equal("daily", id);
        Assert.Equal("Date", query!["filter"]!["property"]!.GetValue<string>());
        Assert.Equal("2026-08-20", query["filter"]!["date"]!["equals"]!.GetValue<string>());
    }

    [Fact]
    public async Task Daily_summary_writes_live_Notion_property_names()
    {
        JsonObject? patch = null;
        using var client = Client(request =>
        {
            if (request.Method == HttpMethod.Post) return Json(HttpStatusCode.OK, "{\"results\":[{\"id\":\"daily\"}]}");
            patch = ReadBody(request);
            return Json(HttpStatusCode.OK, "{}");
        });
        var temporary = Directory.CreateTempSubdirectory();
        try
        {
            var resolver = new ProjectResolver(client, "projects");
            var daily = new DailyNoteService(client, "daily-log");
            var upserts = new NotionUpsertService(client, new BridgeStateStore(Path.Combine(temporary.FullName, "state.json")));
            var importer = new TimeImporter(client, resolver, daily, upserts, "time-records");
            using var document = JsonDocument.Parse("""{"sourceId":"day:v1:2026-08-20","type":"dailySummary","date":"2026-08-20","totalWorkMinutes":480,"driveMinutes":80,"siteMinutes":95,"idleMinutes":20,"needsReviewMinutes":0,"drivenMiles":"12.4"}""");
            await importer.ImportAsync(document.RootElement, CancellationToken.None);
            var properties = patch!["properties"]!.AsObject();
            Assert.True(properties.ContainsKey("Total Work Minutes"));
            Assert.True(properties.ContainsKey("Drive Minutes"));
            Assert.False(properties.ContainsKey("Total Work"));
            Assert.False(properties.ContainsKey("Day Key"));
        }
        finally { temporary.Delete(true); }
    }

    [Fact]
    public async Task Email_import_uses_business_date_and_merges_existing_relations()
    {
        var patches = new List<(string Path, JsonObject Body)>();
        var queryCount = 0;
        using var client = Client(request =>
        {
            var path = request.RequestUri!.AbsolutePath;
            if (request.Method == HttpMethod.Post && path.Contains("data_sources", StringComparison.Ordinal))
            {
                queryCount++;
                return queryCount switch
                {
                    1 or 2 => Json(HttpStatusCode.OK, queryCount == 1 ? "{\"results\":[{\"id\":\"project\"}]}" : "{\"results\":[{\"id\":\"daily\"}]}"),
                    _ => Json(HttpStatusCode.OK, "{\"results\":[]}"),
                };
            }
            if (request.Method == HttpMethod.Post && path == "/v1/pages") return Json(HttpStatusCode.OK, "{\"id\":\"email\"}");
            if (request.Method == HttpMethod.Get && path.EndsWith("/email", StringComparison.Ordinal))
                return Json(HttpStatusCode.OK, "{\"properties\":{\"Projects\":{\"relation\":[{\"id\":\"existing-project\"}],\"has_more\":false}}}");
            if (request.Method == HttpMethod.Get && path.EndsWith("/daily", StringComparison.Ordinal))
                return Json(HttpStatusCode.OK, "{\"properties\":{\"Projects\":{\"relation\":[{\"id\":\"manual-project\"}],\"has_more\":false}}}");
            if (request.Method == HttpMethod.Patch)
            {
                patches.Add((path, ReadBody(request)));
                return Json(HttpStatusCode.OK, "{}");
            }
            throw new InvalidOperationException($"Unexpected request {request.Method} {path}");
        });
        var temporary = Directory.CreateTempSubdirectory();
        try
        {
            var daily = new DailyNoteService(client, "daily-log");
            var upserts = new NotionUpsertService(client, new BridgeStateStore(Path.Combine(temporary.FullName, "state.json")));
            var importer = new EmailImporter(new ProjectResolver(client, "projects"), daily, upserts, "emails", TimeZoneInfo.FindSystemTimeZoneById("America/New_York"));
            using var document = JsonDocument.Parse("""{"type":"email","mailboxKey":"work","projectNumbers":["TEST-001"],"outlookFolder":"Projects/Test","internetMessageId":"<id@example>","receivedAt":"2026-08-21T02:30:00Z","from":"sender@example.com","to":["me@example.com"],"cc":[],"subject":"Test","preview":"Preview","body":"Newest body","bodyStatus":"available","hasAttachments":false,"attachmentNames":[]}""");
            await importer.ImportAsync(document.RootElement, CancellationToken.None);
            var create = patches.Count == 2;
            Assert.True(create);
            Assert.Contains(patches, value => value.Path == "/v1/pages/email" && RelationIds(value.Body, "Projects").SetEquals(["existing-project", "project"]));
            Assert.Contains(patches, value => value.Path == "/v1/pages/daily" && RelationIds(value.Body, "Projects").SetEquals(["manual-project", "project"]));
        }
        finally { temporary.Delete(true); }
    }

    [Fact]
    public async Task Existing_source_id_is_updated_without_duplicate_creation()
    {
        var createCalls = 0;
        using var client = Client(request =>
        {
            if (request.Method == HttpMethod.Post)
                return Json(HttpStatusCode.OK, request.RequestUri!.AbsolutePath == "/v1/pages" ? (++createCalls > 0 ? "{\"id\":\"new\"}" : "{}") : "{\"results\":[{\"id\":\"existing\"}]}");
            return Json(HttpStatusCode.OK, "{}");
        });
        var temporary = Directory.CreateTempSubdirectory();
        try
        {
            var service = new NotionUpsertService(client, new BridgeStateStore(Path.Combine(temporary.FullName, "state.json")));
            var id = await service.UpsertAsync("emails", "source", new JsonObject(), null, CancellationToken.None);
            Assert.Equal("existing", id);
            Assert.Equal(0, createCalls);
        }
        finally { temporary.Delete(true); }
    }

    [Fact]
    public async Task Startup_schema_validation_rejects_property_drift()
    {
        using var client = Client(_ => Json(HttpStatusCode.OK, "{\"properties\":{}}"));
        var settings = new BridgeSettings("outbox", "state", "projects", "daily", "emails", "time");
        var error = await Assert.ThrowsAsync<InvalidDataException>(() =>
            new NotionSchemaValidator(client).ValidateAsync(settings, CancellationToken.None));
        Assert.Contains("Project Number", error.Message, StringComparison.Ordinal);
    }

    private static JsonObject ReadBody(HttpRequestMessage request) =>
        JsonNode.Parse(request.Content!.ReadAsStringAsync().GetAwaiter().GetResult())!.AsObject();

    private static HashSet<string> RelationIds(JsonObject body, string property) =>
        body["properties"]![property]!["relation"]!.AsArray().Select(value => value!["id"]!.GetValue<string>()).ToHashSet(StringComparer.Ordinal);

    private static NotionClient Client(Func<HttpRequestMessage, HttpResponseMessage> response) =>
        new("test-token", new HttpClient(new StubHandler(response)));

    private static HttpResponseMessage Json(HttpStatusCode status, string value, string? retryAfter = null)
    {
        var response = new HttpResponseMessage(status) { Content = new StringContent(value, Encoding.UTF8, "application/json") };
        if (retryAfter is not null) response.Headers.TryAddWithoutValidation("Retry-After", retryAfter);
        return response;
    }

    private sealed class StubHandler(Func<HttpRequestMessage, HttpResponseMessage> response) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            Task.FromResult(response(request));
    }
}
