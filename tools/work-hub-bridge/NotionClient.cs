using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace WorkHubBridge;

public sealed class NotionClient : IDisposable
{
    private readonly HttpClient http;
    private readonly bool ownsClient;

    public NotionClient(string token, HttpClient? client = null)
    {
        http = client ?? new HttpClient();
        ownsClient = client is null;
        http.BaseAddress = new Uri("https://api.notion.com");
        http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
        http.DefaultRequestHeaders.Add("Notion-Version", "2026-03-11");
    }

    public async Task<JsonObject> SendAsync(HttpMethod method, string path, JsonObject? body, CancellationToken cancellationToken)
    {
        for (var attempt = 1; attempt <= 5; attempt++)
        {
            using var request = new HttpRequestMessage(method, path) { Content = body is null ? null : new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json") };
            using var response = await http.SendAsync(request, cancellationToken);
            var responseText = await response.Content.ReadAsStringAsync(cancellationToken);
            if (response.IsSuccessStatusCode) return JsonNode.Parse(responseText)?.AsObject() ?? [];
            if ((response.StatusCode == HttpStatusCode.TooManyRequests || (int)response.StatusCode >= 500) && attempt < 5)
            {
                var wait = response.Headers.RetryAfter?.Delta ?? TimeSpan.FromSeconds(Math.Min(30, 1 << attempt));
                await Task.Delay(wait, cancellationToken);
                continue;
            }
            var message = JsonNode.Parse(responseText)?["message"]?.GetValue<string>() ?? response.ReasonPhrase ?? "request rejected";
            throw new InvalidOperationException($"Notion request failed ({(int)response.StatusCode}): {message}");
        }
        throw new InvalidOperationException("Notion retry limit reached.");
    }

    public void Dispose()
    {
        if (ownsClient) http.Dispose();
    }
}

public sealed class ProjectResolver(NotionClient notion, string dataSourceId)
{
    public async Task<string> ResolveAsync(string projectNumber, CancellationToken cancellationToken)
    {
        var normalized = projectNumber.Trim().ToUpperInvariant();
        var results = await QueryAsync("Project Number", normalized, cancellationToken);
        if (results.Count == 0) throw new InvalidDataException($"Unknown project number {normalized}.");
        if (results.Count > 1) throw new InvalidDataException($"Duplicate project number {normalized}.");
        return results[0]!["id"]!.GetValue<string>();
    }

    private async Task<JsonArray> QueryAsync(string property, string value, CancellationToken cancellationToken)
    {
        var body = new JsonObject { ["filter"] = RichTextFilter(property, value), ["page_size"] = 2 };
        return (await notion.SendAsync(HttpMethod.Post, $"/v1/data_sources/{dataSourceId}/query", body, cancellationToken))["results"]!.AsArray();
    }

    internal static JsonObject RichTextFilter(string property, string value) => new()
    {
        ["property"] = property,
        ["rich_text"] = new JsonObject { ["equals"] = value }
    };
}

public sealed class DailyNoteService(NotionClient notion, string dataSourceId)
{
    public async Task<string> EnsureAsync(DateOnly date, CancellationToken cancellationToken)
    {
        var dateText = date.ToString("yyyy-MM-dd");
        var query = new JsonObject { ["filter"] = ProjectResolver.RichTextFilter("Day Key", dateText), ["page_size"] = 2 };
        var results = (await notion.SendAsync(HttpMethod.Post, $"/v1/data_sources/{dataSourceId}/query", query, cancellationToken))["results"]!.AsArray();
        if (results.Count > 1) throw new InvalidDataException($"Duplicate Daily Notes for {dateText}.");
        if (results.Count == 1) return results[0]!["id"]!.GetValue<string>();
        var body = new JsonObject
        {
            ["parent"] = new JsonObject { ["type"] = "data_source_id", ["data_source_id"] = dataSourceId },
            ["properties"] = new JsonObject { ["Entry"] = NotionProperties.Title($"{dateText} — Daily Notes"), ["Date"] = NotionProperties.Date(dateText) },
            ["template"] = new JsonObject { ["type"] = "default", ["timezone"] = "America/New_York" }
        };
        return (await notion.SendAsync(HttpMethod.Post, "/v1/pages", body, cancellationToken))["id"]!.GetValue<string>();
    }
}

internal static class NotionProperties
{
    public static JsonObject Title(string value) => new() { ["title"] = TextArray(value) };
    public static JsonObject Text(string value) => new() { ["rich_text"] = TextArray(value) };
    public static JsonObject Date(string value) => new() { ["date"] = new JsonObject { ["start"] = value } };
    public static JsonObject Number(double value) => new() { ["number"] = value };
    public static JsonObject Select(string value) => new() { ["select"] = new JsonObject { ["name"] = value } };
    public static JsonObject Checkbox(bool value) => new() { ["checkbox"] = value };
    public static JsonObject Relation(IEnumerable<string> ids) => new() { ["relation"] = new JsonArray(ids.Select(id => (JsonNode)new JsonObject { ["id"] = id }).ToArray()) };
    private static JsonArray TextArray(string value) => new(new JsonObject { ["type"] = "text", ["text"] = new JsonObject { ["content"] = value } });
}
