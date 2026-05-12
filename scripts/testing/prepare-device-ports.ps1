param(
    [string]$DeviceId,
    [int]$StatePort = 4958,
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

$existingStateForwards = @(& adb forward --list | Where-Object { $_ -match "\btcp:$StatePort\b" })
foreach ($forward in $existingStateForwards) {
    $serial = ($forward -split "\s+")[0]
    if (-not [string]::IsNullOrWhiteSpace($serial)) {
        & adb -s $serial forward --remove "tcp:$StatePort" 2>$null | Out-Null
    }
}

$connectedDevices = @(& adb devices | Select-String -Pattern "^\S+\s+device$" | ForEach-Object {
        ($_.Line -split "\s+")[0]
    })
foreach ($device in $connectedDevices) {
    $existingLogReverses = @(& adb -s $device reverse --list 2>$null | Where-Object { $_ -match "\btcp:$LogPort\b" })
    foreach ($reverse in $existingLogReverses) {
        & adb -s $device reverse --remove "tcp:$LogPort" 2>$null | Out-Null
    }
}

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
