using System.Text.Json;

namespace WorkHubBridge;

public sealed class BridgeHost
{
    public static async Task<int> RunAsync(string[] args, CancellationToken cancellationToken)
    {
        var configurationPath = args.FirstOrDefault() ?? Path.Combine(AppContext.BaseDirectory, "bridge.settings.json");
        if (!File.Exists(configurationPath))
        {
            Console.Error.WriteLine("Missing bridge.settings.json. Copy bridge.settings.example.json and set the synced outbox and Notion data source IDs.");
            return 2;
        }
        var settings = JsonSerializer.Deserialize<BridgeSettings>(await File.ReadAllTextAsync(configurationPath, cancellationToken), JsonOptions.Default)
            ?? throw new InvalidDataException("Bridge settings are invalid.");
        var token = WindowsCredentialStore.Read("TimeTracker.WorkHub.Notion")
            ?? throw new InvalidOperationException("Notion token is missing from Windows Credential Manager target TimeTracker.WorkHub.Notion.");
        using var notion = new NotionClient(token);
        await new NotionSchemaValidator(notion).ValidateAsync(settings, cancellationToken);
        var scanner = new OutboxScanner(settings.OutboxRoot);
        var validator = new EnvelopeValidator();
        var state = new BridgeStateStore(Path.Combine(settings.StateDirectory, "bridge-state.json"));
        var resolver = new ProjectResolver(notion, settings.ProjectsDataSourceId);
        var dailyNotes = new DailyNoteService(notion, settings.DailyLogDataSourceId);
        var upserts = new NotionUpsertService(notion, state);
        var businessTimeZone = TimeZoneInfo.FindSystemTimeZoneById(settings.BusinessTimeZone);
        var emailImporter = new EmailImporter(resolver, dailyNotes, upserts, settings.ProjectEmailsDataSourceId, businessTimeZone);
        var timeImporter = new TimeImporter(notion, resolver, dailyNotes, upserts, settings.TimeRecordsDataSourceId);
        var receipts = new ReceiptWriter(settings.OutboxRoot);
        var archive = new ArchiveManager(settings.OutboxRoot);

        do
        {
            foreach (var file in scanner.Scan())
            {
                var accepted = new List<string>();
                var rejected = new Dictionary<string, string>(StringComparer.Ordinal);
                string transferId = Path.GetFileNameWithoutExtension(file);
                try
                {
                    var envelope = validator.Read(file);
                    transferId = envelope.TransferId;
                    foreach (var record in envelope.Records)
                    {
                        try
                        {
                            if (record.GetProperty("type").GetString() == "email") await emailImporter.ImportAsync(record, cancellationToken);
                            else await timeImporter.ImportAsync(record, cancellationToken);
                            accepted.Add(SourceKeyFactory.ForRecord(record));
                        }
                        catch (Exception error) when (error is InvalidDataException or InvalidOperationException)
                        {
                            rejected[SafeSourceId(record)] = LogSanitizer.Sanitize(error.Message);
                        }
                    }
                    await receipts.WriteAsync(transferId, accepted, rejected, cancellationToken);
                    archive.Move(file, rejected.Count == 0 ? "Processed" : "Failed");
                }
                catch (Exception error)
                {
                    Console.Error.WriteLine($"{Path.GetFileName(file)}: {LogSanitizer.Sanitize(error.Message)}");
                    if (error is InvalidDataException) archive.Move(file, "Failed");
                }
            }
            archive.DeleteProcessedOlderThan(TimeSpan.FromDays(7));
            if (args.Contains("--once", StringComparer.OrdinalIgnoreCase)) break;
            await Task.Delay(TimeSpan.FromSeconds(30), cancellationToken);
        } while (!cancellationToken.IsCancellationRequested);
        return 0;
    }

    private static string SafeSourceId(JsonElement record)
    {
        try { return SourceKeyFactory.ForRecord(record); }
        catch { return record.TryGetProperty("sourceId", out var sourceId) ? sourceId.GetString() ?? "unknown" : "unknown"; }
    }
}

public sealed record BridgeSettings(
    string OutboxRoot,
    string StateDirectory,
    string ProjectsDataSourceId,
    string DailyLogDataSourceId,
    string ProjectEmailsDataSourceId,
    string TimeRecordsDataSourceId,
    string BusinessTimeZone = "America/New_York");

internal static class JsonOptions
{
    public static readonly JsonSerializerOptions Default = new(JsonSerializerDefaults.Web) { WriteIndented = true };
}

internal static class LogSanitizer
{
    public static string Sanitize(string value)
    {
        var sanitized = string.Join(' ', value.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries));
        return sanitized[..Math.Min(240, sanitized.Length)];
    }
}
