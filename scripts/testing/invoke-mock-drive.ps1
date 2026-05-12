param(
    [string]$DeviceId,
    [string]$Package = "com.robertochavez.timetracker.debug",
    [int]$StatePort = 4958,
    [ValidateSet("Endpoint", "Provider")]
    [string]$Mode = "Endpoint",
    [double]$StartLat = 42.64517,
    [double]$StartLng = -85.27639,
    [double]$HomeRadiusMeters = 120,
    [double]$DistanceMeters = 3218.688,
    [long]$DurationSeconds = 240,
    [int]$PointCount = 8,
    [double]$AccuracyMeters = 5,
    [int]$StepDelayMillis = 6000,
    [string]$Provider = "gps",
    [string]$RunId,
    [string]$ActorId = "local",
    [switch]$NoLaunch,
    [switch]$KeepProvider
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb is required and was not found on PATH."
}

if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = "mock-drive-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
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

function Invoke-Adb {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = @(& adb @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0 -and -not $AllowFailure) {
        throw "adb $($Arguments -join ' ') failed: $($output -join [Environment]::NewLine)"
    }
    return $output
}

function Format-Invariant {
    param([double]$Value)

    return $Value.ToString("0.########", [System.Globalization.CultureInfo]::InvariantCulture)
}

function New-MockDriveQuery {
    return @{
        runId = $RunId
        actorId = $ActorId
        startLat = (Format-Invariant $StartLat)
        startLng = (Format-Invariant $StartLng)
        homeRadiusMeters = (Format-Invariant $HomeRadiusMeters)
        distanceMeters = (Format-Invariant $DistanceMeters)
        durationSeconds = [string]$DurationSeconds
        pointCount = [string]$PointCount
        accuracyMeters = (Format-Invariant $AccuracyMeters)
    }
}

function Invoke-StateEndpoint {
    param(
        [string]$Path,
        [hashtable]$Query
    )

    $queryString = ($Query.GetEnumerator() | Sort-Object Key | ForEach-Object {
            "$([System.Uri]::EscapeDataString([string]$_.Key))=$([System.Uri]::EscapeDataString([string]$_.Value))"
        }) -join "&"
    $uri = "http://127.0.0.1:${StatePort}${Path}?${queryString}"
    return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 20
}

function New-DrivePoints {
    $pointTotal = [Math]::Max(2, $PointCount)
    $startEpochMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $metersPerDegreeLatitude = 111320.0

    for ($index = 0; $index -lt $pointTotal; $index++) {
        $fraction = $index / ($pointTotal - 1)
        $latitude = $StartLat + (($DistanceMeters * $fraction) / $metersPerDegreeLatitude)
        [pscustomobject]@{
            Latitude = $latitude
            Longitude = $StartLng
            Accuracy = $AccuracyMeters
            Time = [long]($startEpochMillis + ($DurationSeconds * 1000 * $fraction))
        }
    }
}

$resolvedDeviceId = Resolve-DeviceId -RequestedDeviceId $DeviceId
Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "forward", "tcp:$StatePort", "tcp:$StatePort") | Out-Null

if (-not $NoLaunch) {
    Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "monkey", "-p", $Package, "-c", "android.intent.category.LAUNCHER", "1") | Out-Null
    Start-Sleep -Seconds 2
}

$query = New-MockDriveQuery

if ($Mode -eq "Endpoint") {
    $response = Invoke-StateEndpoint -Path "/testing/inject-mock-drive" -Query $query
    $response.proof | ConvertTo-Json -Depth 8
    return
}

$prepared = Invoke-StateEndpoint -Path "/testing/prepare-mock-drive" -Query $query
Write-Host "Prepared mock drive session:"
$prepared.proof | ConvertTo-Json -Depth 8

try {
    Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "appops", "set", "2000", "android:mock_location", "allow") -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "cmd", "location", "providers", "remove-test-provider", $Provider) -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "cmd", "location", "set-location-enabled", "true") -AllowFailure | Out-Null
    Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "cmd", "location", "providers", "add-test-provider", $Provider) | Out-Null
    Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "cmd", "location", "providers", "set-test-provider-enabled", $Provider, "true") | Out-Null

    foreach ($point in New-DrivePoints) {
        $lat = Format-Invariant $point.Latitude
        $lng = Format-Invariant $point.Longitude
        $accuracy = Format-Invariant $point.Accuracy
        Invoke-Adb -Arguments @(
            "-s",
            $resolvedDeviceId,
            "shell",
            "cmd",
            "location",
            "providers",
            "set-test-provider-location",
            $Provider,
            "--location",
            "$lat,$lng",
            "--accuracy",
            $accuracy,
            "--time",
            [string]$point.Time
        ) | Out-Null
        Start-Sleep -Milliseconds $StepDelayMillis
    }
} finally {
    if (-not $KeepProvider) {
        Invoke-Adb -Arguments @("-s", $resolvedDeviceId, "shell", "cmd", "location", "providers", "remove-test-provider", $Provider) -AllowFailure | Out-Null
    }
}

$finished = Invoke-StateEndpoint -Path "/testing/finish-mock-drive" -Query @{
    runId = $RunId
    actorId = $ActorId
}
Write-Host "Finished mock drive session:"
$finished.proof | ConvertTo-Json -Depth 8
