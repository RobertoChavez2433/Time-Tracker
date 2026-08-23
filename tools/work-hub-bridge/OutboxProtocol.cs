using System.Text.Json;

namespace WorkHubBridge;

public sealed record OutboxEnvelope(int SchemaVersion, string TransferId, DateTimeOffset CreatedAt, IReadOnlyList<JsonElement> Records);

public sealed class OutboxScanner(string root)
{
    public IEnumerable<string> Scan() => new[] { Path.Combine(root, "Incoming", "Email"), Path.Combine(root, "Incoming", "Time") }
        .Where(Directory.Exists)
        .SelectMany(path => Directory.EnumerateFiles(path, "*.json", SearchOption.TopDirectoryOnly))
        .Where(path => !Path.GetFileName(path).StartsWith('.'))
        .OrderBy(File.GetCreationTimeUtc);
}

public sealed class EnvelopeValidator
{
    public OutboxEnvelope Read(string path)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        var envelope = JsonSerializer.Deserialize<OutboxEnvelope>(stream, JsonOptions.Default)
            ?? throw new InvalidDataException("Envelope is empty.");
        if (envelope.SchemaVersion != 1 || !Guid.TryParse(envelope.TransferId, out _)) throw new InvalidDataException("Unsupported or malformed envelope.");
        foreach (var record in envelope.Records)
        {
            if (!record.TryGetProperty("sourceId", out var sourceId) || string.IsNullOrWhiteSpace(sourceId.GetString()))
                throw new InvalidDataException("Record sourceId is required.");
            var raw = record.GetRawText();
            foreach (var forbidden in new[] { "latitude", "longitude", "address", "radius", "route", "speed", "homeLocation" })
                if (raw.Contains($"\"{forbidden}\"", StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException($"Privacy contract rejected forbidden field {forbidden}.");
        }
        return envelope;
    }
}

public static class SourceKeyFactory
{
    public static string Email(string mailboxKey, string internetMessageId)
    {
        var bytes = System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes($"{mailboxKey.Trim()}\n{internetMessageId.Trim()}"));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }
}

public sealed class ReceiptWriter(string root)
{
    public async Task WriteAsync(string transferId, IReadOnlyList<string> accepted, IReadOnlyDictionary<string, string> rejected, CancellationToken cancellationToken)
    {
        var directory = Directory.CreateDirectory(Path.Combine(root, "Receipts")).FullName;
        var temporary = Path.Combine(directory, $".{transferId}.json.partial");
        var final = Path.Combine(directory, $"{transferId}.json");
        var receipt = new { schemaVersion = 1, transferId, createdAt = DateTimeOffset.UtcNow, acceptedSourceIds = accepted, rejected };
        await File.WriteAllTextAsync(temporary, JsonSerializer.Serialize(receipt, JsonOptions.Default), cancellationToken);
        File.Move(temporary, final, true);
    }
}

public sealed class ArchiveManager(string root)
{
    public void Move(string file, string destinationName)
    {
        var directory = Directory.CreateDirectory(Path.Combine(root, destinationName)).FullName;
        File.Move(file, Path.Combine(directory, Path.GetFileName(file)), true);
    }

    public void DeleteProcessedOlderThan(TimeSpan retention)
    {
        var directory = Path.Combine(root, "Processed");
        if (!Directory.Exists(directory)) return;
        foreach (var file in Directory.EnumerateFiles(directory).Where(path => DateTime.UtcNow - File.GetLastWriteTimeUtc(path) > retention)) File.Delete(file);
    }
}
