using System.Net;
using System.Text;
using System.Text.Json;
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
