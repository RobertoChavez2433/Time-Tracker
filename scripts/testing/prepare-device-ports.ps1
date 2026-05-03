param(
    [string]$DeviceId,
    [int]$StatePort = 4948,
    [int]$LogPort = 3947
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is required and was not found on PATH."
}

function Resolve-DeviceId {
    param([string]$RequestedDeviceId)

    if (-not [string]::IsNullOrWhiteSpace($RequestedDeviceId)) {
        return $RequestedDeviceId
    }

    $devices = @(& adb devices | Select-String -Pattern "^\S+\s+device$" | ForEach-Object {
            ($_.Line -split "\s+")[0]
        })
    if ($devices.Count -ne 1) {
        throw "Pass -DeviceId. Found $($devices.Count) connected adb devices."
    }
    return [string]$devices[0]
}

$resolvedDeviceId = Resolve-DeviceId -RequestedDeviceId $DeviceId

& adb -s $resolvedDeviceId forward "tcp:$StatePort" "tcp:$StatePort" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to forward state endpoint port $StatePort for $resolvedDeviceId."
}

& adb -s $resolvedDeviceId reverse "tcp:$LogPort" "tcp:$LogPort" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Failed to reverse debug log port $LogPort for $resolvedDeviceId."
}

Write-Host "ADB ports ready for $resolvedDeviceId."
Write-Host "State endpoint: host tcp:$StatePort -> device tcp:$StatePort"
Write-Host "Debug logs: device tcp:$LogPort -> host tcp:$LogPort"
