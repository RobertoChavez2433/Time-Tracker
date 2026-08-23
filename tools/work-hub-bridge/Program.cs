using WorkHubBridge;

if (args.Contains("--set-token", StringComparer.OrdinalIgnoreCase))
{
    Console.Write("Notion token: ");
    var token = SecretConsole.Read();
    WindowsCredentialStore.Write("TimeTracker.WorkHub.Notion", token);
    Console.WriteLine("Token saved in Windows Credential Manager.");
    return 0;
}
return await BridgeHost.RunAsync(args, CancellationToken.None);
