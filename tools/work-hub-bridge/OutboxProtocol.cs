using System.Text.Json;
using System.Text.Json.Nodes;
using Json.Schema;

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
    private readonly JsonSchema envelopeSchema;
    private readonly JsonSchema emailSchema;
    private readonly JsonSchema timeSchema;

    public EnvelopeValidator(string? contractDirectory = null)
    {
        var root = contractDirectory ?? Path.Combine(AppContext.BaseDirectory, "contracts", "work-hub", "v1");
        envelopeSchema = Load(root, "envelope.schema.json");
        emailSchema = Load(root, "email-record.schema.json");
        timeSchema = Load(root, "time-record.schema.json");
    }

    public OutboxEnvelope Read(string path)
    {
        var rawEnvelope = File.ReadAllText(path);
        Validate(envelopeSchema, JsonNode.Parse(rawEnvelope), "envelope");
        var envelope = JsonSerializer.Deserialize<OutboxEnvelope>(rawEnvelope, JsonOptions.Default)
            ?? throw new InvalidDataException("Envelope is empty.");
        if (envelope.SchemaVersion != 1 || !Guid.TryParse(envelope.TransferId, out _)) throw new InvalidDataException("Unsupported or malformed envelope.");
        foreach (var record in envelope.Records)
        {
            var type = record.TryGetProperty("type", out var typeValue) ? typeValue.GetString() : null;
            Validate(type == "email" ? emailSchema : timeSchema, JsonNode.Parse(record.GetRawText()), $"{type ?? "unknown"} record");
            var raw = record.GetRawText();
            foreach (var forbidden in new[] { "latitude", "longitude", "address", "radius", "route", "speed", "homeLocation" })
                if (raw.Contains($"\"{forbidden}\"", StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException($"Privacy contract rejected forbidden field {forbidden}.");
        }
        return envelope;
    }

    private static JsonSchema Load(string root, string name)
    {
        var path = Path.Combine(root, name);
        if (!File.Exists(path)) throw new InvalidDataException($"Missing Work Hub contract {name}.");
        return JsonSchema.FromText(File.ReadAllText(path));
    }

    private static void Validate(JsonSchema schema, JsonNode? value, string label)
    {
        if (value is null) throw new InvalidDataException($"Invalid {label}: empty JSON.");
        var results = schema.Evaluate(value, new EvaluationOptions { OutputFormat = OutputFormat.List, RequireFormatValidation = true });
        if (!results.IsValid) throw new InvalidDataException($"Invalid {label}: payload does not match the v1 contract.");
    }
}

public static class SourceKeyFactory
{
    public static string Email(string mailboxKey, string internetMessageId)
    {
        var bytes = System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes($"{mailboxKey.Trim()}\n{internetMessageId.Trim()}"));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }

    public static string ForRecord(JsonElement record)
    {
        if (record.GetProperty("type").GetString() != "email") return record.GetProperty("sourceId").GetString()!;
        var generated = Email(record.GetProperty("mailboxKey").GetString()!, record.GetProperty("internetMessageId").GetString()!);
        if (record.TryGetProperty("sourceId", out var supplied) && !string.Equals(supplied.GetString(), generated, StringComparison.Ordinal))
            throw new InvalidDataException("Email Source ID does not match its mailbox and Internet Message ID.");
        return generated;
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
