using System.Text.Json;
using System.Text.Json.Nodes;

namespace WorkHubBridge;

public sealed class EmailImporter(
    ProjectResolver projects,
    DailyNoteService dailyNotes,
    NotionUpsertService upserts,
    string dataSourceId,
    TimeZoneInfo businessTimeZone)
{
    public async Task ImportAsync(JsonElement record, CancellationToken cancellationToken)
    {
        var sourceId = SourceKeyFactory.ForRecord(record);
        var received = record.GetProperty("receivedAt").GetDateTimeOffset();
        var workDate = DateOnly.FromDateTime(TimeZoneInfo.ConvertTime(received, businessTimeZone).DateTime);
        var projectIds = new List<string>();
        foreach (var number in record.GetProperty("projectNumbers").EnumerateArray())
            projectIds.Add(await projects.ResolveAsync(number.GetString()!, cancellationToken));
        var dailyNoteId = await dailyNotes.EnsureAsync(workDate, cancellationToken);
        var (body, status) = EmailBodyCleaner.Clean(record.TryGetProperty("body", out var bodyValue) ? bodyValue.GetString() : null, record.GetProperty("bodyStatus").GetString());
        var properties = new JsonObject
        {
            ["Subject"] = NotionProperties.Title(record.GetProperty("subject").GetString() ?? "(No subject)"),
            ["Received"] = NotionProperties.Date(received.ToString("O")),
            ["Work Date"] = NotionProperties.Date(workDate.ToString("yyyy-MM-dd")),
            ["Daily Note"] = NotionProperties.Relation([dailyNoteId]),
            ["From"] = NotionProperties.Text(record.GetProperty("from").GetString() ?? string.Empty),
            ["To / CC"] = NotionProperties.Text(Recipients(record)),
            ["Preview"] = NotionProperties.Text((record.GetProperty("preview").GetString() ?? string.Empty)[..Math.Min(1900, (record.GetProperty("preview").GetString() ?? string.Empty).Length)]),
            ["Outlook Folder"] = NotionProperties.Text(record.GetProperty("outlookFolder").GetString() ?? string.Empty),
            ["Has Attachments"] = NotionProperties.Checkbox(record.TryGetProperty("hasAttachments", out var attachments) && attachments.GetBoolean()),
            ["Attachment Names"] = NotionProperties.Text(AttachmentNames(record)),
            ["Body Status"] = NotionProperties.Select(status),
            ["Conversation ID"] = NotionProperties.Text(record.TryGetProperty("conversationId", out var conversation) ? conversation.GetString() ?? string.Empty : string.Empty),
            ["Source ID"] = NotionProperties.Text(sourceId),
            ["Last Synced"] = NotionProperties.Date(DateTimeOffset.UtcNow.ToString("O"))
        };
        var children = string.IsNullOrEmpty(body) ? new JsonArray() : PlainTextBlocks(NotionMarkdownEscaper.Escape(body));
        var pageId = await upserts.UpsertAsync(dataSourceId, sourceId, properties, children, cancellationToken);
        await upserts.MergeRelationAsync(pageId, "Projects", projectIds, cancellationToken);
        await dailyNotes.MergeProjectsAsync(dailyNoteId, projectIds, cancellationToken);
    }

    private static string Recipients(JsonElement record) => string.Join(", ", new[] { "to", "cc" }
        .Where(name => record.TryGetProperty(name, out _))
        .SelectMany(name => record.GetProperty(name).EnumerateArray().Select(value => value.GetString()))
        .Where(value => !string.IsNullOrWhiteSpace(value)));

    private static string AttachmentNames(JsonElement record) => record.TryGetProperty("attachmentNames", out var names)
        ? string.Join(", ", names.EnumerateArray().Select(value => value.GetString())) : string.Empty;

    private static JsonArray PlainTextBlocks(string value)
    {
        var blocks = new JsonArray();
        for (var offset = 0; offset < value.Length; offset += 1_900)
        {
            var chunk = value.Substring(offset, Math.Min(1_900, value.Length - offset));
            blocks.Add(new JsonObject
            {
                ["object"] = "block",
                ["type"] = "paragraph",
                ["paragraph"] = new JsonObject { ["rich_text"] = new JsonArray(new JsonObject { ["type"] = "text", ["text"] = new JsonObject { ["content"] = chunk } }) }
            });
        }
        return blocks;
    }
}

public sealed class TimeImporter(
    NotionClient notion,
    ProjectResolver projects,
    DailyNoteService dailyNotes,
    NotionUpsertService upserts,
    string timeRecordsDataSourceId)
{
    public async Task ImportAsync(JsonElement record, CancellationToken cancellationToken)
    {
        var type = record.GetProperty("type").GetString();
        if (type == "dailySummary") { await ImportDailyAsync(record, cancellationToken); return; }
        if (type != "projectDay") throw new InvalidDataException($"Unsupported record type {type}.");
        var sourceId = record.GetProperty("sourceId").GetString()!;
        var projectNumber = record.GetProperty("projectNumber").GetString()!;
        var projectId = await projects.ResolveAsync(projectNumber, cancellationToken);
        await projects.EnsureWorksiteAsync(projectId, record.GetProperty("workLocationId").GetString()!, cancellationToken);
        var date = DateOnly.Parse(record.GetProperty("date").GetString()!);
        var dailyId = await dailyNotes.EnsureAsync(date, cancellationToken);
        await dailyNotes.MergeProjectsAsync(dailyId, [projectId], cancellationToken);
        var properties = new JsonObject
        {
            ["Record"] = NotionProperties.Title($"{date:yyyy-MM-dd} — {projectNumber} — {record.GetProperty("siteLabel").GetString()}"),
            ["Date"] = NotionProperties.Date(date.ToString("yyyy-MM-dd")),
            ["Project"] = NotionProperties.Relation([projectId]),
            ["Daily Note"] = NotionProperties.Relation([dailyId]),
            ["Project Number"] = NotionProperties.Text(projectNumber),
            ["Worksite ID"] = NotionProperties.Text(record.GetProperty("workLocationId").GetString()!),
            ["Worksite"] = NotionProperties.Text(record.GetProperty("siteLabel").GetString()!),
            ["First Arrival"] = NotionProperties.Date(record.GetProperty("firstArrival").GetString()!),
            ["Last Departure"] = NotionProperties.Date(record.GetProperty("lastDeparture").GetString()!),
            ["Onsite Minutes"] = NotionProperties.Number(record.GetProperty("onsiteMinutes").GetInt64()),
            ["Visit Count"] = NotionProperties.Number(record.GetProperty("visitCount").GetInt32()),
            ["Status"] = NotionProperties.Select(ToTitle(record.GetProperty("status").GetString()!)),
            ["Source ID"] = NotionProperties.Text(sourceId),
            ["Last Synced"] = NotionProperties.Date(DateTimeOffset.UtcNow.ToString("O"))
        };
        await upserts.UpsertAsync(timeRecordsDataSourceId, sourceId, properties, null, cancellationToken);
    }

    private async Task ImportDailyAsync(JsonElement record, CancellationToken cancellationToken)
    {
        var date = DateOnly.Parse(record.GetProperty("date").GetString()!);
        var pageId = await dailyNotes.EnsureAsync(date, cancellationToken);
        var summary = $"Work {record.GetProperty("totalWorkMinutes")}m · Drive {record.GetProperty("driveMinutes")}m · Site {record.GetProperty("siteMinutes")}m · Idle {record.GetProperty("idleMinutes")}m · Review {record.GetProperty("needsReviewMinutes")}m · Miles {record.GetProperty("drivenMiles")}";
        var properties = new JsonObject
        {
            ["Time Summary"] = NotionProperties.Text(summary),
            ["Total Work Minutes"] = NotionProperties.Number(record.GetProperty("totalWorkMinutes").GetInt64()),
            ["Drive Minutes"] = NotionProperties.Number(record.GetProperty("driveMinutes").GetInt64()),
            ["Site Minutes"] = NotionProperties.Number(record.GetProperty("siteMinutes").GetInt64()),
            ["Idle Minutes"] = NotionProperties.Number(record.GetProperty("idleMinutes").GetInt64()),
            ["Needs Review Minutes"] = NotionProperties.Number(record.GetProperty("needsReviewMinutes").GetInt64()),
            ["Miles"] = NotionProperties.Number(double.Parse(record.GetProperty("drivenMiles").ToString(), System.Globalization.CultureInfo.InvariantCulture)),
            ["Time Status"] = NotionProperties.Select(ToTitle(record.GetProperty("status").GetString()!)),
            ["Last Time Sync"] = NotionProperties.Date(DateTimeOffset.UtcNow.ToString("O"))
        };
        await notion.SendAsync(HttpMethod.Patch, $"/v1/pages/{pageId}", new JsonObject { ["properties"] = properties }, cancellationToken);
    }

    private static string ToTitle(string value) => string.Join(' ', value.Split('_').Select(part => char.ToUpperInvariant(part[0]) + part[1..]));
}
