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
            try
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
            catch (Exception error) when (attempt < 5 && error is HttpRequestException or TaskCanceledException)
            {
                await Task.Delay(TimeSpan.FromSeconds(Math.Min(30, 1 << attempt)), cancellationToken);
            }
        }
        throw new InvalidOperationException("Notion retry limit reached.");
    }

    public void Dispose()
    {
        if (ownsClient) http.Dispose();
    }

    public async Task<IReadOnlySet<string>> RelationIdsAsync(string pageId, string property, CancellationToken cancellationToken)
    {
        var page = await SendAsync(HttpMethod.Get, $"/v1/pages/{pageId}", null, cancellationToken);
        var relation = page["properties"]?[property] ?? throw new InvalidDataException($"Notion property {property} is missing.");
        if (relation["has_more"]?.GetValue<bool>() == true)
            throw new InvalidDataException($"Notion relation {property} is too large to merge safely.");
        return relation["relation"]!.AsArray().Select(value => value!["id"]!.GetValue<string>()).ToHashSet(StringComparer.Ordinal);
    }

    public async Task MergeRelationAsync(string pageId, string property, IEnumerable<string> incomingIds, CancellationToken cancellationToken)
    {
        var merged = (await RelationIdsAsync(pageId, property, cancellationToken)).ToHashSet(StringComparer.Ordinal);
        merged.UnionWith(incomingIds);
        await SendAsync(
            HttpMethod.Patch,
            $"/v1/pages/{pageId}",
            new JsonObject { ["properties"] = new JsonObject { [property] = NotionProperties.Relation(merged) } },
            cancellationToken);
    }

    public async Task<string> RichTextAsync(string pageId, string property, CancellationToken cancellationToken)
    {
        var page = await SendAsync(HttpMethod.Get, $"/v1/pages/{pageId}", null, cancellationToken);
        var values = page["properties"]?[property]?["rich_text"]?.AsArray()
            ?? throw new InvalidDataException($"Notion property {property} is missing.");
        return string.Concat(values.Select(value => value?["plain_text"]?.GetValue<string>() ?? string.Empty));
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

    public async Task EnsureWorksiteAsync(string projectId, string worksiteId, CancellationToken cancellationToken)
    {
        var current = await notion.RichTextAsync(projectId, "Time Tracker Worksite ID", cancellationToken);
        if (!string.IsNullOrWhiteSpace(current) && !string.Equals(current, worksiteId, StringComparison.Ordinal))
            throw new InvalidDataException("Project is already linked to a different Time Tracker worksite.");
        if (string.IsNullOrWhiteSpace(current))
            await notion.SendAsync(HttpMethod.Patch, $"/v1/pages/{projectId}", new JsonObject
            {
                ["properties"] = new JsonObject { ["Time Tracker Worksite ID"] = NotionProperties.Text(worksiteId) }
            }, cancellationToken);
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

    internal static JsonObject DateFilter(string property, string value) => new()
    {
        ["property"] = property,
        ["date"] = new JsonObject { ["equals"] = value }
    };
}

public sealed class DailyNoteService(NotionClient notion, string dataSourceId)
{
    public async Task<string> EnsureAsync(DateOnly date, CancellationToken cancellationToken)
    {
        var dateText = date.ToString("yyyy-MM-dd");
        var query = new JsonObject { ["filter"] = ProjectResolver.DateFilter("Date", dateText), ["page_size"] = 2 };
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

    public Task MergeProjectsAsync(string dailyNoteId, IEnumerable<string> projectIds, CancellationToken cancellationToken) =>
        notion.MergeRelationAsync(dailyNoteId, "Projects", projectIds, cancellationToken);
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
