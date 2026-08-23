using System.Text.Json;

namespace WorkHubBridge;

public sealed class BridgeStateStore
{
    private readonly string path;
    private readonly Dictionary<string, string> mappings;

    public BridgeStateStore(string path)
    {
        this.path = path;
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        mappings = File.Exists(path)
            ? JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(path), JsonOptions.Default) ?? []
            : [];
    }

    public string? Get(string sourceId) => mappings.GetValueOrDefault(sourceId);

    public async Task SetAsync(string sourceId, string notionPageId, CancellationToken cancellationToken)
    {
        mappings[sourceId] = notionPageId;
        var temporary = path + ".tmp";
        await File.WriteAllTextAsync(temporary, JsonSerializer.Serialize(mappings, JsonOptions.Default), cancellationToken);
        File.Move(temporary, path, true);
    }
}
