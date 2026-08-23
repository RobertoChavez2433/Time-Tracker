param([string]$InstallDirectory = "$env:LOCALAPPDATA\WorkHubBridge")
$ErrorActionPreference = "Stop"
$project = Join-Path $PSScriptRoot "WorkHubBridge.csproj"
dotnet publish $project -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -o $InstallDirectory
$example = Join-Path $PSScriptRoot "bridge.settings.example.json"
$settings = Join-Path $InstallDirectory "bridge.settings.json"
if (-not (Test-Path -LiteralPath $settings)) { Copy-Item -LiteralPath $example -Destination $settings }
$executable = Join-Path $InstallDirectory "WorkHubBridge.exe"
& $executable --set-token
$action = "`"$executable`" `"$settings`""
schtasks.exe /Create /TN "Time Tracker Work Hub Bridge" /SC ONLOGON /TR $action /RL LIMITED /F | Out-Null
Write-Host "Installed. Edit $settings, then sign out/in or run the executable once to start the bridge."
