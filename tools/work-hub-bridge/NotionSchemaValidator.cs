using System.Text.Json.Nodes;

namespace WorkHubBridge;

public sealed class NotionSchemaValidator(NotionClient notion)
{
    public async Task ValidateAsync(BridgeSettings settings, CancellationToken cancellationToken)
    {
        await ValidateDataSourceAsync(settings.ProjectsDataSourceId, new Dictionary<string, string>
        {
            ["Project Number"] = "rich_text",
            ["Time Tracker Worksite ID"] = "rich_text",
        }, cancellationToken);
        await ValidateDataSourceAsync(settings.DailyLogDataSourceId, new Dictionary<string, string>
        {
            ["Entry"] = "title",
            ["Date"] = "date",
            ["Projects"] = "relation",
            ["Time Summary"] = "rich_text",
            ["Total Work Minutes"] = "number",
            ["Drive Minutes"] = "number",
            ["Site Minutes"] = "number",
            ["Idle Minutes"] = "number",
            ["Needs Review Minutes"] = "number",
            ["Miles"] = "number",
            ["Time Status"] = "select",
            ["Last Time Sync"] = "date",
        }, cancellationToken);
        await ValidateDataSourceAsync(settings.ProjectEmailsDataSourceId, new Dictionary<string, string>
        {
            ["Subject"] = "title",
            ["Received"] = "date",
            ["Work Date"] = "date",
            ["Projects"] = "relation",
            ["Daily Note"] = "relation",
            ["Source ID"] = "rich_text",
        }, cancellationToken);
        await ValidateDataSourceAsync(settings.TimeRecordsDataSourceId, new Dictionary<string, string>
        {
            ["Record"] = "title",
            ["Date"] = "date",
            ["Project"] = "relation",
            ["Daily Note"] = "relation",
            ["Source ID"] = "rich_text",
        }, cancellationToken);
    }

    private async Task ValidateDataSourceAsync(
        string dataSourceId,
        IReadOnlyDictionary<string, string> required,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(dataSourceId)) throw new InvalidDataException("All four Notion data source IDs are required.");
        var source = await notion.SendAsync(HttpMethod.Get, $"/v1/data_sources/{dataSourceId}", null, cancellationToken);
        var properties = source["properties"]?.AsObject() ?? throw new InvalidDataException($"Notion data source {dataSourceId} has no properties.");
        foreach (var (name, type) in required)
        {
            if (properties[name] is not JsonObject property || property["type"]?.GetValue<string>() != type)
                throw new InvalidDataException($"Notion data source {dataSourceId} requires {name} ({type}).");
        }
    }
}
