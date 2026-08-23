using System.Text.Json.Nodes;

namespace WorkHubBridge;

public sealed class NotionUpsertService(NotionClient notion, BridgeStateStore state)
{
    public Task MergeRelationAsync(string pageId, string property, IEnumerable<string> ids, CancellationToken cancellationToken) =>
        notion.MergeRelationAsync(pageId, property, ids, cancellationToken);

    public async Task<string> UpsertAsync(
        string dataSourceId,
        string sourceId,
        JsonObject properties,
        JsonArray? children,
        CancellationToken cancellationToken)
    {
        var pageId = state.Get(sourceId) ?? await FindAsync(dataSourceId, sourceId, cancellationToken);
        if (pageId is null)
        {
            var body = new JsonObject
            {
                ["parent"] = new JsonObject { ["type"] = "data_source_id", ["data_source_id"] = dataSourceId },
                ["properties"] = properties
            };
            if (children is not null && children.Count > 0) body["children"] = children;
            pageId = (await notion.SendAsync(HttpMethod.Post, "/v1/pages", body, cancellationToken))["id"]!.GetValue<string>();
        }
        else
        {
            await notion.SendAsync(HttpMethod.Patch, $"/v1/pages/{pageId}", new JsonObject { ["properties"] = properties }, cancellationToken);
            if (children is not null)
            {
                var existing = await notion.SendAsync(HttpMethod.Get, $"/v1/blocks/{pageId}/children?page_size=100", null, cancellationToken);
                foreach (var block in existing["results"]!.AsArray())
                    await notion.SendAsync(HttpMethod.Delete, $"/v1/blocks/{block!["id"]!.GetValue<string>()}", null, cancellationToken);
                if (children.Count > 0)
                    await notion.SendAsync(HttpMethod.Patch, $"/v1/blocks/{pageId}/children", new JsonObject { ["children"] = children }, cancellationToken);
            }
        }
        await state.SetAsync(sourceId, pageId, cancellationToken);
        return pageId;
    }

    private async Task<string?> FindAsync(string dataSourceId, string sourceId, CancellationToken cancellationToken)
    {
        var body = new JsonObject { ["filter"] = ProjectResolver.RichTextFilter("Source ID", sourceId), ["page_size"] = 2 };
        var results = (await notion.SendAsync(HttpMethod.Post, $"/v1/data_sources/{dataSourceId}/query", body, cancellationToken))["results"]!.AsArray();
        if (results.Count > 1) throw new InvalidDataException($"Duplicate Source ID {sourceId}.");
        return results.Count == 0 ? null : results[0]!["id"]!.GetValue<string>();
    }
}

public static class NotionMarkdownEscaper
{
    public static string Escape(string value) => value
        .Replace("\\", "\\\\", StringComparison.Ordinal)
        .Replace("*", "\\*", StringComparison.Ordinal)
        .Replace("_", "\\_", StringComparison.Ordinal)
        .Replace("`", "\\`", StringComparison.Ordinal)
        .Replace("[", "\\[", StringComparison.Ordinal);
}

public static class EmailBodyCleaner
{
    private static readonly string[] ReplyMarkers = ["\nFrom:", "\n-----Original Message-----", "\nOn "];

    public static (string Body, string Status) Clean(string? body, string? declaredStatus)
    {
        if (string.Equals(declaredStatus, "protected", StringComparison.OrdinalIgnoreCase)) return (string.Empty, "Protected");
        if (string.IsNullOrWhiteSpace(body)) return (string.Empty, "Missing");
        var newest = body.Replace("\r\n", "\n", StringComparison.Ordinal).Trim();
        var cut = ReplyMarkers.Select(marker => newest.IndexOf(marker, StringComparison.OrdinalIgnoreCase)).Where(index => index > 0).DefaultIfEmpty(newest.Length).Min();
        newest = newest[..cut].Trim();
        if (newest.Length > 100_000) return (newest[..100_000], "Truncated");
        return (newest, "Available");
    }
}
