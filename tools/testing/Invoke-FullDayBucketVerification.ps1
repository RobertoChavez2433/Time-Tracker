<#
.SYNOPSIS
    Run the S21 full-day bucket lifecycle verifier.

.DESCRIPTION
    This acceptance verifier installs the e2e debug app, resets app data, grants
    Android permissions, drives the real setup UI, delivers simulated movement
    through Android test location providers, and observes only GET /testing/state
    plus app logs. It does not call debug write endpoints that start sessions,
    finish sessions, seed bucket intervals, or inject drive tracking directly.
#>

[CmdletBinding()]
param(
    [string]$DeviceId = "RFCNC0Y975L",
    [string]$RunId,
    [string]$OutputRoot,
    [string]$PackageName = "com.robertochavez.timetracker.debug",
    [string]$ActivityName = "com.robertochavez.timetracker.MainActivity",
    [int]$StatePort = 4958,
    [int]$LogPort = 3947,
    [string]$Provider = "gps",
    [string[]]$AdditionalProviders = @("network", "fused"),
    [int]$StageTimeoutSeconds = 180,
    [int]$PointDelaySeconds = 10,
    [double]$MinuteTolerance = 1,
    [double]$MileTolerance = 0.25,
    [bool]$AnchorHomeToDeviceLocation = $true,
    [switch]$SkipInstall,
    [switch]$KeepProvider,
    [switch]$IncludeNonTrackableDayScenario,
    [switch]$RequireRealActivityRecognitionEvidence
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dateLabel = Get-Date -Format "yyyy-MM-dd"
if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = "full-day-bucket-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $projectRoot "tools/testing/test-results/$dateLabel/$RunId"
}

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

$script:ResolvedDeviceId = $DeviceId
$script:Display = @{ width = 1080; height = 2340 }
$script:Stages = New-Object System.Collections.Generic.List[object]
$script:RouteEvents = New-Object System.Collections.Generic.List[object]
$script:Snapshots = @{}
$script:RunFailure = $null
$script:ScenarioAnchorSource = "fixed_default"
$script:RoutePoints = @{}

$HomePoint = [ordered]@{ Latitude = 42.33140; Longitude = -83.04580; RadiusOption = "quarter_mile" }
$Site = [ordered]@{ Latitude = 42.35600; Longitude = -83.06400; RadiusOption = "quarter_mile"; Label = "FullDaySite" }

function New-SafeName {
    param([Parameter(Mandatory = $true)][string]$Name)
    return [regex]::Replace($Name, "[^A-Za-z0-9_.-]", "_")
}

function Save-Json {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [AllowEmptyCollection()]
        $Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force | Out-Null
    ConvertTo-Json -InputObject $Value -Depth 60 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Add-StageResult {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet("passed", "failed", "manual_required")][string]$Status,
        [string]$Details = "",
        [hashtable]$Artifacts = @{}
    )

    $script:Stages.Add([ordered]@{
            name = $Name
            status = $Status
            details = $Details
            artifacts = $Artifacts
            recordedAt = [DateTimeOffset]::UtcNow.ToString("o")
        }) | Out-Null
}

function Invoke-Stage {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action,
        [switch]$ManualRequiredDoesNotFail
    )

    try {
        $result = & $Action
        if ($result -and $result.status -eq "manual_required") {
            $manualArtifacts = if ($result.artifacts) { $result.artifacts } else { @{} }
            Add-StageResult -Name $Name -Status "manual_required" -Details ([string]$result.details) -Artifacts $manualArtifacts
            if (-not $ManualRequiredDoesNotFail) {
                throw "[manual_required] $Name requires external evidence."
            }
            return $result
        }
        $details = if ($result -and $result.details) { [string]$result.details } else { "" }
        $artifacts = if ($result -and $result.artifacts) { $result.artifacts } else { @{} }
        Add-StageResult -Name $Name -Status "passed" -Details $details -Artifacts $artifacts
        return $result
    } catch {
        $message = $_.Exception.Message
        Add-StageResult -Name $Name -Status "failed" -Details $message
        $script:RunFailure = $message
        throw
    }
}

function Assert-Condition {
    param(
        [Parameter(Mandatory = $true)][bool]$Condition,
        [Parameter(Mandatory = $true)][string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Format-Invariant {
    param([Parameter(Mandatory = $true)][double]$Value)
    return $Value.ToString("0.########", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-TestLocationProviders {
    $names = New-Object System.Collections.Generic.List[string]
    foreach ($name in @($Provider) + @($AdditionalProviders)) {
        if (-not [string]::IsNullOrWhiteSpace($name) -and -not $names.Contains($name)) {
            $names.Add($name) | Out-Null
        }
    }
    return @($names.ToArray())
}

function New-Point {
    param(
        [Parameter(Mandatory = $true)][double]$Latitude,
        [Parameter(Mandatory = $true)][double]$Longitude
    )
    return [ordered]@{
        Latitude = [Math]::Round($Latitude, 6)
        Longitude = [Math]::Round($Longitude, 6)
    }
}

function Update-RoutePoints {
    $script:RoutePoints = [ordered]@{
        homeExit = New-Point -Latitude ($HomePoint.Latitude + 0.00560) -Longitude ($HomePoint.Longitude - 0.00560)
        belowThresholdEnd = New-Point -Latitude ($HomePoint.Latitude + 0.00940) -Longitude ($HomePoint.Longitude - 0.00560)
        shortBurstEnd = New-Point -Latitude ($HomePoint.Latitude + 0.01260) -Longitude ($HomePoint.Longitude - 0.00820)
        driveEnd = New-Point -Latitude ($HomePoint.Latitude + 0.02010) -Longitude ($HomePoint.Longitude - 0.01470)
        siteExit = New-Point -Latitude ($Site.Latitude + 0.00700) -Longitude ($Site.Longitude - 0.00600)
        returnBypass = New-Point -Latitude ($Site.Latitude - 0.00700) -Longitude ($Site.Longitude - 0.00700)
        idleEnd = New-Point -Latitude ($HomePoint.Latitude + 0.00580) -Longitude ($HomePoint.Longitude - 0.00560)
    }
}

function Set-ScenarioAnchor {
    param(
        [Parameter(Mandatory = $true)][double]$Latitude,
        [Parameter(Mandatory = $true)][double]$Longitude,
        [Parameter(Mandatory = $true)][string]$Source
    )

    $HomePoint.Latitude = [Math]::Round($Latitude, 6)
    $HomePoint.Longitude = [Math]::Round($Longitude, 6)
    $Site.Latitude = [Math]::Round($Latitude + 0.02460, 6)
    $Site.Longitude = [Math]::Round($Longitude - 0.01820, 6)
    $script:ScenarioAnchorSource = $Source
    Update-RoutePoints
}

function Get-DeviceCurrentLocation {
    $output = @(Invoke-AdbShell -Arguments @("dumpsys", "location") -AllowFailure)
    $candidates = New-Object System.Collections.Generic.List[object]
    foreach ($line in $output) {
        if ($line -match "last location=Location\[(?<provider>[^\s]+)\s+(?<lat>-?\d+(?:\.\d+)?),(?<lng>-?\d+(?:\.\d+)?)\s+hAcc=(?<accuracy>\d+(?:\.\d+)?)") {
            $providerName = $Matches.provider
            $priority = switch ($providerName) {
                "fused" { 0; break }
                "gps" { 1; break }
                "network" { 2; break }
                default { 3 }
            }
            $candidates.Add([ordered]@{
                    provider = $providerName
                    latitude = [double]::Parse($Matches.lat, [System.Globalization.CultureInfo]::InvariantCulture)
                    longitude = [double]::Parse($Matches.lng, [System.Globalization.CultureInfo]::InvariantCulture)
                    accuracyMeters = [double]::Parse($Matches.accuracy, [System.Globalization.CultureInfo]::InvariantCulture)
                    priority = $priority
                }) | Out-Null
        }
    }
    $candidate = @($candidates |
            Where-Object { $_.accuracyMeters -gt 0 -and $_.accuracyMeters -le 250 } |
            Sort-Object priority, accuracyMeters |
            Select-Object -First 1)
    if ($candidate.Count -eq 0) {
        return $null
    }
    return $candidate[0]
}

function Clear-TestLocationProviders {
    foreach ($providerName in Get-TestLocationProviders) {
        Invoke-AdbShell -Arguments @("cmd", "location", "providers", "remove-test-provider", $providerName) -AllowFailure | Out-Null
    }
}

function Initialize-ScenarioCoordinates {
    Update-RoutePoints
    if (-not $AnchorHomeToDeviceLocation) {
        $script:ScenarioAnchorSource = "fixed_default"
        return
    }

    Clear-TestLocationProviders
    Start-Sleep -Seconds 1
    $deviceLocation = Get-DeviceCurrentLocation
    if ($null -eq $deviceLocation) {
        $script:ScenarioAnchorSource = "fixed_default_no_device_location"
        return
    }

    Set-ScenarioAnchor -Latitude $deviceLocation.latitude -Longitude $deviceLocation.longitude -Source "device_$($deviceLocation.provider)_accuracy_$($deviceLocation.accuracyMeters)m"
}

function Invoke-AdbRaw {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& adb @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "adb $($Arguments -join ' ') failed ($exitCode): $($output -join "`n")"
    }
    return $output
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    return Invoke-AdbRaw -Arguments (@("-s", $script:ResolvedDeviceId) + $Arguments) -AllowFailure:$AllowFailure
}

function Invoke-AdbShell {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    return Invoke-Adb -Arguments (@("shell") + $Arguments) -AllowFailure:$AllowFailure
}

function Update-DisplaySize {
    $output = Invoke-AdbShell -Arguments @("wm", "size") -AllowFailure
    $line = @($output | Where-Object { $_ -match "(\d+)x(\d+)" } | Select-Object -First 1)
    if ($line -and [string]$line -match "(\d+)x(\d+)") {
        $script:Display = @{ width = [int]$Matches[1]; height = [int]$Matches[2] }
    }
}

function Start-DebugInfrastructure {
    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb is required and was not found on PATH."
    }
    $devices = @(Invoke-AdbRaw -Arguments @("devices") | Select-String -Pattern "^\S+\s+device$" | ForEach-Object {
            ($_.Line -split "\s+")[0]
        })
    Assert-Condition -Condition ($devices -contains $script:ResolvedDeviceId) -Message "Device $script:ResolvedDeviceId is not connected. Connected devices: $($devices -join ', ')"

    if (-not $SkipInstall) {
        $gradle = Join-Path $projectRoot "gradlew.bat"
        & $gradle "-PtimeTracker.e2eDebug=true" "installDebug" "--console=plain"
        if ($LASTEXITCODE -ne 0) {
            throw "Debug install failed. The verifier must install with -PtimeTracker.e2eDebug=true."
        }
    }

    & (Join-Path $projectRoot "scripts/testing/start-debug-server.ps1") -Port $LogPort
    & (Join-Path $projectRoot "scripts/testing/prepare-device-ports.ps1") -DeviceId $script:ResolvedDeviceId -StatePort $StatePort -LogPort $LogPort
    Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/clear" -Method Post -TimeoutSec 10 | Out-Null

    Invoke-AdbShell -Arguments @("am", "force-stop", $PackageName) -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "clear", $PackageName) | Out-Null
    Grant-TrackingPermissions
    Initialize-ScenarioCoordinates
    Initialize-TestLocationProvider
    Update-DisplaySize
    Start-App
}

function Grant-TrackingPermissions {
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACCESS_COARSE_LOCATION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACCESS_FINE_LOCATION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACCESS_BACKGROUND_LOCATION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACTIVITY_RECOGNITION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.POST_NOTIFICATIONS") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", $PackageName, "ACCESS_BACKGROUND_LOCATION", "allow") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", $PackageName, "android:background_location", "allow") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", "2000", "android:mock_location", "allow") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", "com.google.android.gms", "android:mock_location", "allow") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", $PackageName, "android:mock_location", "allow") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("settings", "put", "secure", "mock_location", "1") -AllowFailure | Out-Null
}

function Start-App {
    Invoke-AdbShell -Arguments @(
        "am",
        "start",
        "-W",
        "-a",
        "android.intent.action.MAIN",
        "-c",
        "android.intent.category.LAUNCHER",
        "--activity-clear-top",
        "-n",
        "$PackageName/$ActivityName"
    ) | Out-Null
    Start-Sleep -Seconds 2
}

function Ensure-StatePortForward {
    $expected = "$script:ResolvedDeviceId tcp:$StatePort tcp:$StatePort"
    $forwards = @(Invoke-AdbRaw -Arguments @("forward", "--list") -AllowFailure)
    if ($forwards -contains $expected) {
        return
    }
    Invoke-Adb -Arguments @("forward", "tcp:$StatePort", "tcp:$StatePort") | Out-Null
}

function Request-State {
    $query = "runId=$([System.Uri]::EscapeDataString($RunId))&actorId=$([System.Uri]::EscapeDataString($script:ResolvedDeviceId))"
    $uri = "http://127.0.0.1:$StatePort/testing/state?$query"
    try {
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
    } catch {
        Ensure-StatePortForward
        Start-Sleep -Milliseconds 300
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
    }
}

function Request-Logs {
    param([int]$Last = 0)

    $suffix = if ($Last -gt 0) { "?last=$Last" } else { "" }
    return @(Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/logs$suffix" -Method Get -TimeoutSec 10)
}

function Capture-State {
    param([Parameter(Mandatory = $true)][string]$Name)

    $state = Request-State
    $safeName = New-SafeName $Name
    $path = Join-Path $OutputRoot "$safeName-state.json"
    Save-Json -Value $state -Path $path
    $script:Snapshots[$safeName] = $state
    return $state
}

function Wait-State {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Condition,
        [int]$TimeoutSeconds = $StageTimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastState = $null
    do {
        $lastState = Request-State
        if (& $Condition $lastState) {
            $safeName = New-SafeName $Name
            $path = Join-Path $OutputRoot "$safeName-state.json"
            Save-Json -Value $lastState -Path $path
            $script:Snapshots[$safeName] = $lastState
            return $lastState
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    if ($lastState) {
        Save-Json -Value $lastState -Path (Join-Path $OutputRoot "$(New-SafeName $Name)-timeout-state.json")
    }
    throw "[state_sentinel_failed] Timed out waiting for $Name."
}

function Wait-Log {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Predicate,
        [int]$TimeoutSeconds = $StageTimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $logs = @()
    do {
        $logs = Request-Logs -Last 500
        if (& $Predicate $logs) {
            Save-Json -Value $logs -Path (Join-Path $OutputRoot "$(New-SafeName $Name)-logs.json")
            return $logs
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    Save-Json -Value $logs -Path (Join-Path $OutputRoot "$(New-SafeName $Name)-timeout-logs.json")
    throw "[log_sentinel_failed] Timed out waiting for $Name."
}

function Save-Screenshot {
    param([Parameter(Mandatory = $true)][string]$Name)

    $safeName = New-SafeName $Name
    $remotePath = "/sdcard/time_tracker_$safeName.png"
    $localPath = Join-Path $OutputRoot "$safeName.png"
    Invoke-AdbShell -Arguments @("screencap", "-p", $remotePath) | Out-Null
    Invoke-Adb -Arguments @("pull", $remotePath, $localPath) | Out-Null
    Invoke-AdbShell -Arguments @("rm", $remotePath) -AllowFailure | Out-Null
    return $localPath
}

function Save-UiDump {
    param([Parameter(Mandatory = $true)][string]$Name)

    $safeName = New-SafeName $Name
    $remotePath = "/sdcard/time_tracker_$safeName.xml"
    $localPath = Join-Path $OutputRoot "$safeName-hierarchy.xml"
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        Invoke-AdbShell -Arguments @("rm", $remotePath) -AllowFailure | Out-Null
        $output = @(Invoke-AdbShell -Arguments @("uiautomator", "dump", $remotePath) -AllowFailure)
        if ($LASTEXITCODE -eq 0) {
            Invoke-Adb -Arguments @("pull", $remotePath, $localPath) | Out-Null
            Invoke-AdbShell -Arguments @("rm", $remotePath) -AllowFailure | Out-Null
            return $localPath
        }
        Start-Sleep -Milliseconds (350 * $attempt)
    }
    throw "uiautomator dump failed: $($output -join "`n")"
}

function Select-ClickableAncestorOrNode {
    param([Parameter(Mandatory = $true)]$Node)

    $candidate = $Node
    while ($candidate) {
        if ($candidate -is [System.Xml.XmlElement] -and [string]$candidate.GetAttribute("clickable") -eq "true") {
            return $candidate
        }
        $candidate = $candidate.ParentNode
    }
    return $Node
}

function Select-UiNodeByTag {
    param(
        [Parameter(Mandatory = $true)][xml]$Ui,
        [Parameter(Mandatory = $true)][string]$Tag
    )

    $textAlias = switch ($Tag) {
        "startup_setup_dialog" { "Finish setup" }
        "startup_enable_button" { "Agree and Enable Tracking" }
        "startup_background_button" { "Enable Background Location" }
        "home_radius_option_quarter_mile" { "1/4 mi" }
        "work_radius_option_quarter_mile" { "1/4 mi" }
        "settings_delete_confirm_button" { "Delete" }
        default { $null }
    }

    foreach ($node in @($Ui.SelectNodes("//node"))) {
        $resourceId = [string]$node.GetAttribute("resource-id")
        $contentDescription = [string]$node.GetAttribute("content-desc")
        $text = [string]$node.GetAttribute("text")
        $matchesStableTag = (
            $resourceId -eq $Tag -or
            $resourceId -eq "$PackageName`:id/$Tag" -or
            $resourceId.EndsWith(":id/$Tag") -or
            $resourceId.EndsWith("/$Tag") -or
            $contentDescription -eq $Tag -or
            $text -eq $Tag
        )
        if ($matchesStableTag) {
            return $node
        }
        if ($textAlias -and $text -eq $textAlias) {
            return Select-ClickableAncestorOrNode -Node $node
        }
    }
    return $null
}

function Invoke-Scroll {
    param([ValidateSet("Up", "Down")][string]$Direction)

    $x = [int]($script:Display.width / 2)
    if ($Direction -eq "Up") {
        $startY = [int]($script:Display.height * 0.78)
        $endY = [int]($script:Display.height * 0.30)
    } else {
        $startY = [int]($script:Display.height * 0.30)
        $endY = [int]($script:Display.height * 0.78)
    }
    Invoke-AdbShell -Arguments @("input", "swipe", "$x", "$startY", "$x", "$endY", "350") | Out-Null
    Start-Sleep -Milliseconds 450
}

function Get-NodeBounds {
    param([Parameter(Mandatory = $true)]$Node)

    $bounds = [string]$Node.GetAttribute("bounds")
    if ($bounds -notmatch "\[(\d+),(\d+)\]\[(\d+),(\d+)\]") {
        throw "Node has invalid bounds: $bounds"
    }
    return @{
        left = [int]$Matches[1]
        top = [int]$Matches[2]
        right = [int]$Matches[3]
        bottom = [int]$Matches[4]
    }
}

function Get-NodeCenter {
    param([Parameter(Mandatory = $true)]$Node)

    $bounds = Get-NodeBounds -Node $Node
    return @{
        x = [int](($bounds.left + $bounds.right) / 2)
        y = [int](($bounds.top + $bounds.bottom) / 2)
    }
}

function Find-UiNodeByTag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$StepName,
        [int]$MaxScrolls = 6
    )

    $attempt = 0
    $path = Save-UiDump -Name "$StepName-$attempt"
    [xml]$ui = Get-Content -LiteralPath $path -Raw
    $node = Select-UiNodeByTag -Ui $ui -Tag $Tag
    if ($node) {
        return @{ node = $node; dumpPath = $path }
    }

    foreach ($direction in @("Up", "Down")) {
        for ($i = 0; $i -lt $MaxScrolls; $i++) {
            $attempt++
            Invoke-Scroll -Direction $direction
            $path = Save-UiDump -Name "$StepName-$attempt"
            [xml]$ui = Get-Content -LiteralPath $path -Raw
            $node = Select-UiNodeByTag -Ui $ui -Tag $Tag
            if ($node) {
                return @{ node = $node; dumpPath = $path }
            }
        }
    }
    throw "[ui_sentinel_failed] Could not find UI tag '$Tag' for $StepName."
}

function Test-TagVisibleNow {
    param([Parameter(Mandatory = $true)][string]$Tag)
    try {
        Find-UiNodeByTag -Tag $Tag -StepName "visible-now-$Tag" -MaxScrolls 0 | Out-Null
        return $true
    } catch {
        return $false
    }
}

function Tap-Tag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$StepName,
        [int]$WaitMs = 700
    )

    $found = Find-UiNodeByTag -Tag $Tag -StepName $StepName
    $center = Get-NodeCenter -Node $found.node
    Invoke-AdbShell -Arguments @("input", "tap", "$($center.x)", "$($center.y)") | Out-Null
    Start-Sleep -Milliseconds $WaitMs
    return $found
}

function Invoke-InputKeyEvent {
    param([Parameter(Mandatory = $true)][int]$KeyCode)
    Invoke-AdbShell -Arguments @("input", "keyevent", [string]$KeyCode) | Out-Null
    Start-Sleep -Milliseconds 35
}

function Invoke-InputKeyCombination {
    param([Parameter(Mandatory = $true)][int[]]$KeyCodes)
    [string[]]$arguments = @("input", "keycombination", "-t", "80")
    $arguments += @($KeyCodes | ForEach-Object { [string]$_ })
    Invoke-AdbShell -Arguments $arguments | Out-Null
    Start-Sleep -Milliseconds 50
}

function Test-KeyboardShown {
    $imeState = @(Invoke-AdbShell -Arguments @("dumpsys", "input_method") -AllowFailure)
    return $imeState -match "mInputShown=true"
}

function Dismiss-Keyboard {
    if (Test-KeyboardShown) {
        Invoke-AdbShell -Arguments @("input", "keyevent", "4") -AllowFailure | Out-Null
    } else {
        Invoke-AdbShell -Arguments @("input", "keyevent", "111") -AllowFailure | Out-Null
    }
    Start-Sleep -Milliseconds 450
}

function Clear-FocusedTextInput {
    Invoke-InputKeyCombination -KeyCodes @(113, 29)
    Invoke-InputKeyEvent -KeyCode 67
    Invoke-InputKeyEvent -KeyCode 123
    for ($i = 0; $i -lt 80; $i++) {
        Invoke-InputKeyEvent -KeyCode 67
    }
}

function ConvertTo-AdbText {
    param([AllowEmptyString()][string]$Text)
    return ($Text -replace " ", "%s")
}

function Enter-TextByTag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$StepName
    )

    $found = Find-UiNodeByTag -Tag $Tag -StepName $StepName
    $center = Get-NodeCenter -Node $found.node
    $minTapY = 120
    $maxTapY = [int]($script:Display.height * 0.78)
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        if ($center.y -lt $minTapY) {
            Invoke-Scroll -Direction Down
        } elseif ($center.y -gt $maxTapY) {
            Invoke-Scroll -Direction Up
        } else {
            break
        }
        $found = Find-UiNodeByTag -Tag $Tag -StepName "$StepName-centered-$attempt"
        $center = Get-NodeCenter -Node $found.node
    }
    if ($center.y -lt $minTapY -or $center.y -gt $maxTapY) {
        throw "[ui_sentinel_failed] UI tag '$Tag' for $StepName was found but not in a tappable input area at y=$($center.y)."
    }

    Invoke-AdbShell -Arguments @("input", "tap", "$($center.x)", "$($center.y)") | Out-Null
    Start-Sleep -Milliseconds 250
    Clear-FocusedTextInput
    Invoke-AdbShell -Arguments @("input", "text", (ConvertTo-AdbText -Text $Text)) | Out-Null
    Start-Sleep -Milliseconds 250
    Dismiss-Keyboard
    return $found
}

function Select-RadiusOption {
    param(
        [Parameter(Mandatory = $true)][ValidateSet("home", "work")][string]$Prefix,
        [Parameter(Mandatory = $true)][string]$OptionSuffix
    )

    Tap-Tag -Tag "${Prefix}_radius_dropdown" -StepName "$Prefix-radius-dropdown" | Out-Null
    Tap-Tag -Tag "${Prefix}_radius_option_$OptionSuffix" -StepName "$Prefix-radius-$OptionSuffix" | Out-Null
}

function Initialize-SingleTestLocationProvider {
    param([Parameter(Mandatory = $true)][string]$ProviderName)

    Invoke-AdbShell -Arguments @("cmd", "location", "set-location-enabled", "true") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("cmd", "location", "providers", "remove-test-provider", $ProviderName) -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @(
        "cmd",
        "location",
        "providers",
        "add-test-provider",
        $ProviderName,
        "--requiresSatellite",
        "--supportsAltitude",
        "--supportsSpeed",
        "--supportsBearing"
    ) | Out-Null
    Invoke-AdbShell -Arguments @("cmd", "location", "providers", "set-test-provider-enabled", $ProviderName, "true") | Out-Null
}

function Set-SingleTestProviderLocation {
    param(
        [Parameter(Mandatory = $true)][string]$ProviderName,
        [Parameter(Mandatory = $true)][double]$Latitude,
        [Parameter(Mandatory = $true)][double]$Longitude,
        [Parameter(Mandatory = $true)][double]$AccuracyMeters,
        [Parameter(Mandatory = $true)][Int64]$TimeMillis
    )

    $lat = Format-Invariant $Latitude
    $lng = Format-Invariant $Longitude
    $accuracy = Format-Invariant $AccuracyMeters

    try {
        Invoke-AdbShell -Arguments @(
            "cmd",
            "location",
            "providers",
            "set-test-provider-location",
            $ProviderName,
            "--location",
            "$lat,$lng",
            "--accuracy",
            $accuracy,
            "--time",
            [string]$TimeMillis
        ) | Out-Null
        return @{ provider = $ProviderName; status = "sent"; error = $null; reinitialized = $false }
    } catch {
        $firstError = $_.Exception.Message
        try {
            Initialize-SingleTestLocationProvider -ProviderName $ProviderName
            Invoke-AdbShell -Arguments @(
                "cmd",
                "location",
                "providers",
                "set-test-provider-location",
                $ProviderName,
                "--location",
                "$lat,$lng",
                "--accuracy",
                $accuracy,
                "--time",
                [string]$TimeMillis
            ) | Out-Null
            return @{ provider = $ProviderName; status = "sent"; error = $firstError; reinitialized = $true }
        } catch {
            return @{ provider = $ProviderName; status = "failed"; error = $_.Exception.Message; firstError = $firstError; reinitialized = $false }
        }
    }
}

function Initialize-TestLocationProvider {
    foreach ($providerName in Get-TestLocationProviders) {
        Initialize-SingleTestLocationProvider -ProviderName $providerName
    }
    $time = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $results = foreach ($providerName in Get-TestLocationProviders) {
        Set-SingleTestProviderLocation -ProviderName $providerName `
            -Latitude $HomePoint.Latitude `
            -Longitude $HomePoint.Longitude `
            -AccuracyMeters 5.0 `
            -TimeMillis $time
    }
    $successfulProviders = @($results | Where-Object { $_.status -eq "sent" })
    if ($successfulProviders.Count -eq 0) {
        throw "Failed to seed the initial Home test location for all providers: $($results | ConvertTo-Json -Compress)"
    }
    $script:RouteEvents.Add([ordered]@{
            stage = "initial-provider-seed"
            latitude = $HomePoint.Latitude
            longitude = $HomePoint.Longitude
            accuracyMeters = 5.0
            expectedBucket = "HOME"
            sentAtEpochMillis = $time
            waitSeconds = 0
            providerResults = @($results)
        }) | Out-Null
}

function Remove-TestLocationProvider {
    if (-not $KeepProvider) {
        Clear-TestLocationProviders
    }
}

function Send-TestLocation {
    param(
        [Parameter(Mandatory = $true)][string]$Stage,
        [Parameter(Mandatory = $true)][double]$Latitude,
        [Parameter(Mandatory = $true)][double]$Longitude,
        [string]$ExpectedBucket,
        [int]$WaitSeconds = $PointDelaySeconds,
        [double]$AccuracyMeters = 5.0
    )

    Invoke-AdbShell -Arguments @(
        "cmd",
        "location",
        "set-location-enabled",
        "true"
    ) -AllowFailure | Out-Null
    $time = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $results = foreach ($providerName in Get-TestLocationProviders) {
        Set-SingleTestProviderLocation -ProviderName $providerName `
            -Latitude $Latitude `
            -Longitude $Longitude `
            -AccuracyMeters $AccuracyMeters `
            -TimeMillis $time
    }
    $successfulProviders = @($results | Where-Object { $_.status -eq "sent" })
    if ($successfulProviders.Count -eq 0) {
        throw "Failed to deliver $Stage test location to all providers: $($results | ConvertTo-Json -Compress)"
    }

    $script:RouteEvents.Add([ordered]@{
            stage = $Stage
            latitude = $Latitude
            longitude = $Longitude
            accuracyMeters = $AccuracyMeters
            expectedBucket = $ExpectedBucket
            sentAtEpochMillis = $time
            waitSeconds = $WaitSeconds
            providerResults = @($results)
        }) | Out-Null
    if ($WaitSeconds -gt 0) {
        Start-Sleep -Seconds $WaitSeconds
    }
}

function Send-E2eActivityTransition {
    param(
        [Parameter(Mandatory = $true)][string]$Stage,
        [Parameter(Mandatory = $true)][ValidateSet("IN_VEHICLE", "STILL", "OTHER")][string]$Activity,
        [Parameter(Mandatory = $true)][ValidateSet("ENTER", "EXIT", "OTHER")][string]$Transition,
        [int]$WaitSeconds = 2
    )

    $sentAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    Invoke-AdbShell -Arguments @(
        "am",
        "broadcast",
        "--receiver-foreground",
        "-n",
        "$PackageName/com.robertochavez.timetracker.core.location.activity.ActivityTransitionBroadcastReceiver",
        "-a",
        "com.robertochavez.timetracker.debug.E2E_ACTIVITY_TRANSITION",
        "--es",
        "activity",
        $Activity,
        "--es",
        "transition",
        $Transition
    ) | Out-Null

    $script:RouteEvents.Add([ordered]@{
            stage = $Stage
            eventType = "activity_transition"
            activity = $Activity
            transition = $Transition
            sentAtEpochMillis = $sentAt
            waitSeconds = $WaitSeconds
        }) | Out-Null
    if ($WaitSeconds -gt 0) {
        Start-Sleep -Seconds $WaitSeconds
    }
    return $sentAt
}

function Send-E2eGeofenceTransition {
    param(
        [Parameter(Mandatory = $true)][string]$Stage,
        [Parameter(Mandatory = $true)][string]$RequestId,
        [Parameter(Mandatory = $true)][ValidateSet("ENTER", "EXIT", "DWELL")][string]$Transition,
        [int]$WaitSeconds = 2
    )

    $sentAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    Invoke-AdbShell -Arguments @(
        "am",
        "broadcast",
        "--receiver-foreground",
        "-n",
        "$PackageName/com.robertochavez.timetracker.core.location.geofence.GeofenceBroadcastReceiver",
        "-a",
        "com.robertochavez.timetracker.debug.E2E_GEOFENCE_TRANSITION",
        "--es",
        "requestId",
        $RequestId,
        "--es",
        "transition",
        $Transition
    ) | Out-Null

    $script:RouteEvents.Add([ordered]@{
            stage = $Stage
            eventType = "geofence_transition"
            requestId = $RequestId
            transition = $Transition
            sentAtEpochMillis = $sentAt
            waitSeconds = $WaitSeconds
        }) | Out-Null
    if ($WaitSeconds -gt 0) {
        Start-Sleep -Seconds $WaitSeconds
    }
    return $sentAt
}

function Send-LinearRoute {
    param(
        [Parameter(Mandatory = $true)][string]$Stage,
        [Parameter(Mandatory = $true)][double]$StartLatitude,
        [Parameter(Mandatory = $true)][double]$StartLongitude,
        [Parameter(Mandatory = $true)][double]$EndLatitude,
        [Parameter(Mandatory = $true)][double]$EndLongitude,
        [Parameter(Mandatory = $true)][int]$PointCount,
        [Parameter(Mandatory = $true)][string]$ExpectedBucket,
        [int]$WaitSeconds = $PointDelaySeconds
    )

    for ($i = 1; $i -le $PointCount; $i++) {
        $fraction = $i / $PointCount
        Send-TestLocation -Stage "$Stage-$i" `
            -Latitude ($StartLatitude + (($EndLatitude - $StartLatitude) * $fraction)) `
            -Longitude ($StartLongitude + (($EndLongitude - $StartLongitude) * $fraction)) `
            -ExpectedBucket $ExpectedBucket `
            -WaitSeconds $WaitSeconds
    }
}

function Get-TodayTotals {
    param([Parameter(Mandatory = $true)]$State)
    return $State.snapshot.reportTotals.today
}

function Get-TotalDrivenMiles {
    param([Parameter(Mandatory = $true)]$State)
    return [double](Get-TodayTotals -State $State).drivenMiles
}

function Assert-MinimumIncrease {
    param(
        [Parameter(Mandatory = $true)][double]$Before,
        [Parameter(Mandatory = $true)][double]$After,
        [Parameter(Mandatory = $true)][double]$Minimum,
        [Parameter(Mandatory = $true)][string]$Message
    )

    Assert-Condition -Condition (($After - $Before) -ge $Minimum) -Message "$Message Before=$Before After=$After MinimumIncrease=$Minimum"
}

function Assert-NoMoreThanIncrease {
    param(
        [Parameter(Mandatory = $true)][double]$Before,
        [Parameter(Mandatory = $true)][double]$After,
        [Parameter(Mandatory = $true)][double]$Maximum,
        [Parameter(Mandatory = $true)][string]$Message
    )

    Assert-Condition -Condition (($After - $Before) -le $Maximum) -Message "$Message Before=$Before After=$After MaximumIncrease=$Maximum"
}

function Assert-LocationThresholdsAreMeters {
    $path = Join-Path $projectRoot "core/location/src/main/kotlin/com/robertochavez/timetracker/core/location/mileage/LocationTrackingMode.kt"
    $content = Get-Content -LiteralPath $path -Raw
    Assert-Condition -Condition ($content -match "BALANCED\([\s\S]*minUpdateDistanceMeters\s*=\s*50f") -Message "Balanced mode minUpdateDistanceMeters is not 50f meters."
    Assert-Condition -Condition ($content -match "VEHICLE\([\s\S]*minUpdateDistanceMeters\s*=\s*20f") -Message "Vehicle mode minUpdateDistanceMeters is not 20f meters."
    return @{ details = "BALANCED minUpdateDistanceMeters=50f; VEHICLE minUpdateDistanceMeters=20f" }
}

function Assert-NoRoutePersistence {
    $mainSourcePaths = @("app/src/main", "core/common/src/main", "core/database/src/main", "core/location/src/main", "feature")
    $patterns = "DriveMovementObservation|routeGeometry|speedHistory|continuousRoute|route_log|routeLog"
    $matches = New-Object System.Collections.Generic.List[string]
    foreach ($relativePath in $mainSourcePaths) {
        $path = Join-Path $projectRoot $relativePath
        if (Test-Path -LiteralPath $path) {
            $output = @(rg -n $patterns $path 2>$null)
            foreach ($line in $output) {
                $matches.Add($line) | Out-Null
            }
        }
    }
    if ($matches.Count -gt 0) {
        Save-Json -Value $matches -Path (Join-Path $OutputRoot "route-persistence-source-matches.json")
    }
    Assert-Condition -Condition ($matches.Count -eq 0) -Message "Production source still references route geometry, speed history, or drive movement observation persistence."
    return @{ details = "No production route geometry, speed history, or continuous route log persistence references found." }
}

function Assert-SmsSafety {
    $manifestMatches = @(rg -n "SMS|SEND_SMS|READ_SMS|RECEIVE_SMS|BROADCAST_SMS" (Join-Path $projectRoot "app") (Join-Path $projectRoot "core") (Join-Path $projectRoot "feature") 2>$null)
    $packageDump = @(Invoke-AdbShell -Arguments @("dumpsys", "package", $PackageName) -AllowFailure)
    $dumpPath = Join-Path $OutputRoot "sms-safety-package-dump.txt"
    $packageDump | Set-Content -LiteralPath $dumpPath -Encoding UTF8
    $packageSmsMatches = @($packageDump | Where-Object { $_ -match "SMS|SEND_SMS|READ_SMS|RECEIVE_SMS|BROADCAST_SMS" })
    Save-Json -Value @{
        manifestMatches = $manifestMatches
        packageSmsMatches = $packageSmsMatches
    } -Path (Join-Path $OutputRoot "sms-safety.json")
    Assert-Condition -Condition ($manifestMatches.Count -eq 0 -and $packageSmsMatches.Count -eq 0) -Message "SMS safety failed: SMS permission/receiver references were found."
    return @{ details = "No SMS permissions, roles, receivers, or services found in source manifests or installed package dump."; artifacts = @{ packageDump = "sms-safety-package-dump.txt"; summary = "sms-safety.json" } }
}

function Ensure-PrivacyAndActivityDetection {
    $state = Wait-State -Name "startup-state-ready" -Condition { param($candidate) $candidate.transportReady -eq $true }
    if (-not [bool]$state.snapshot.privacyDisclosureAccepted) {
        Assert-Condition -Condition (Test-TagVisibleNow -Tag "startup_enable_button") -Message "Startup privacy setup button was not visible."
        Tap-Tag -Tag "startup_enable_button" -StepName "startup-accept" | Out-Null
        $state = Wait-State -Name "startup-after-accept" -Condition { param($candidate) [bool]$candidate.snapshot.privacyDisclosureAccepted }
    }
    if (Test-TagVisibleNow -Tag "startup_background_button") {
        Grant-TrackingPermissions
        Start-App
        Start-Sleep -Seconds 2
        Assert-Condition -Condition (-not (Test-TagVisibleNow -Tag "startup_background_button")) -Message "Startup still requires background location after pm grant/appops."
    }
    if (-not [bool]$state.snapshot.activityDetectionEnabled) {
        Tap-Tag -Tag "nav_settings" -StepName "settings-nav-for-activity" | Out-Null
        Tap-Tag -Tag "settings_enable_activity_button" -StepName "settings-enable-activity" | Out-Null
        $state = Wait-State -Name "settings-after-enable-activity" -Condition { param($candidate) [bool]$candidate.snapshot.activityDetectionEnabled }
    }
    Wait-Log -Name "activity-registration" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Activity transitions registered" }).Count -gt 0
    } | Out-Null
    return @{ details = "Privacy accepted and activity detection enabled through app UI." }
}

function Ensure-TodayTrackable {
    Tap-Tag -Tag "nav_settings" -StepName "settings-nav-for-workday" | Out-Null
    $state = Capture-State -Name "trackable-before"
    if (-not [bool]$state.snapshot.trackableToday) {
        $todayTag = "settings_workday_$(([string]$state.snapshot.todayDayOfWeek).ToLowerInvariant())_switch"
        Tap-Tag -Tag $todayTag -StepName "settings-enable-today-workday" | Out-Null
        $state = Wait-State -Name "trackable-after-enable" -Condition { param($candidate) [bool]$candidate.snapshot.trackableToday }
    }
    return @{ details = "Today is configured as a trackable workday: $($state.snapshot.todayDayOfWeek)." }
}

function Configure-HomeAndSite {
    Tap-Tag -Tag "nav_places" -StepName "places-nav-for-locations" | Out-Null

    Tap-Tag -Tag "home_edit_pin_button" -StepName "home-edit-pin" | Out-Null
    Enter-TextByTag -Tag "home_latitude_field" -Text (Format-Invariant $HomePoint.Latitude) -StepName "home-latitude" | Out-Null
    Enter-TextByTag -Tag "home_longitude_field" -Text (Format-Invariant $HomePoint.Longitude) -StepName "home-longitude" | Out-Null
    Select-RadiusOption -Prefix "home" -OptionSuffix $HomePoint.RadiusOption
    Send-TestLocation -Stage "pre-home-geofence-registration" -Latitude $HomePoint.Latitude -Longitude $HomePoint.Longitude -ExpectedBucket "HOME" -WaitSeconds 2
    Tap-Tag -Tag "home_save_pin_button" -StepName "home-save-pin" | Out-Null
    $homeState = Wait-State -Name "home-after-save" -Condition { param($candidate) [bool]$candidate.snapshot.homeSet }
    Assert-Condition -Condition ([double]$homeState.snapshot.homeRadiusMeters -gt 0) -Message "Home radius was not saved."

    Tap-Tag -Tag "work_edit_pin_button" -StepName "work-edit-pin" | Out-Null
    Enter-TextByTag -Tag "work_label_field" -Text $Site.Label -StepName "work-label" | Out-Null
    Enter-TextByTag -Tag "work_latitude_field" -Text (Format-Invariant $Site.Latitude) -StepName "work-latitude" | Out-Null
    Enter-TextByTag -Tag "work_longitude_field" -Text (Format-Invariant $Site.Longitude) -StepName "work-longitude" | Out-Null
    Select-RadiusOption -Prefix "work" -OptionSuffix $Site.RadiusOption
    Tap-Tag -Tag "work_save_pin_button" -StepName "work-save-pin" | Out-Null
    $workState = Wait-State -Name "work-after-save" -Condition { param($candidate) [int]$candidate.snapshot.workLocationCount -ge 1 }
    Assert-Condition -Condition ([double]$workState.snapshot.workRadiusMeters -gt 0) -Message "Work radius was not saved."

    Wait-Log -Name "home-work-geofence-registration" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Home geofence registered" }).Count -gt 0 -and
            @($logs | Where-Object { $_.message -eq "Work geofences registered" }).Count -gt 0 -and
            @($logs | Where-Object { $_.message -eq "Tracking registrations synchronized" }).Count -gt 0
    } | Out-Null
    return @{ details = "Home and one saved work-site geofence configured through app UI." }
}

function Invoke-FullDayLifecycle {
    Save-Json -Value @{
        home = $HomePoint
        site = $Site
        anchorHomeToDeviceLocation = $AnchorHomeToDeviceLocation
        scenarioAnchorSource = $script:ScenarioAnchorSource
        routePoints = $script:RoutePoints
        provider = $Provider
        additionalProviders = @($AdditionalProviders)
        effectiveProviders = @(Get-TestLocationProviders)
        pointDelaySeconds = $PointDelaySeconds
        speedThresholdMph = 15
        sustainedDriveSeconds = 60
        tolerance = @{ minutes = $MinuteTolerance; miles = $MileTolerance }
    } -Path (Join-Path $OutputRoot "route-inputs.json")

    Send-TestLocation -Stage "start-inside-home" -Latitude $HomePoint.Latitude -Longitude $HomePoint.Longitude -ExpectedBucket "HOME" -WaitSeconds 3
    $homeStart = Capture-State -Name "full-day-start-inside-home"
    Assert-Condition -Condition ($null -eq $homeStart.snapshot.activeSession) -Message "Expected no active away session while starting inside Home."
    Assert-Condition -Condition ([double]$homeStart.snapshot.totalDrivenMiles -eq 0.0) -Message "Expected no miles before leaving Home."
    Save-Screenshot -Name "full-day-start-home" | Out-Null

    Send-LinearRoute -Stage "home-movement-excluded" `
        -StartLatitude $HomePoint.Latitude -StartLongitude $HomePoint.Longitude `
        -EndLatitude ($HomePoint.Latitude + 0.0005) -EndLongitude $HomePoint.Longitude `
        -PointCount 2 -ExpectedBucket "HOME" -WaitSeconds 5
    $homeMovement = Capture-State -Name "home-movement-excluded"
    Assert-NoMoreThanIncrease -Before ([double]$homeStart.snapshot.totalDrivenMiles) -After ([double]$homeMovement.snapshot.totalDrivenMiles) -Maximum 0.001 -Message "Home movement counted miles."

    Send-TestLocation -Stage "home-exit" -Latitude $script:RoutePoints.homeExit.Latitude -Longitude $script:RoutePoints.homeExit.Longitude -ExpectedBucket "NEEDS_REVIEW" -WaitSeconds 3
    Wait-Log -Name "home-exit-observed" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Home geofence exit received" }).Count -gt 0
    } | Out-Null
    $afterHomeExit = Wait-State -Name "after-home-exit-active-session" -Condition { param($candidate) $null -ne $candidate.snapshot.activeSession }
    Wait-Log -Name "balanced-movement-started" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Away movement location updates started" -and $_.data.mode -eq "BALANCED" }).Count -gt 0
    } | Out-Null

    Send-LinearRoute -Stage "below-threshold-no-drive" `
        -StartLatitude $script:RoutePoints.homeExit.Latitude -StartLongitude $script:RoutePoints.homeExit.Longitude `
        -EndLatitude $script:RoutePoints.belowThresholdEnd.Latitude -EndLongitude $script:RoutePoints.belowThresholdEnd.Longitude `
        -PointCount 7 -ExpectedBucket "NEEDS_REVIEW" -WaitSeconds $PointDelaySeconds
    $belowThreshold = Capture-State -Name "below-threshold-no-drive"
    Assert-Condition -Condition ([int](Get-TodayTotals -State $belowThreshold).driveMinutes -eq 0) -Message "Drive started during below-threshold movement."

    Send-LinearRoute -Stage "short-burst-no-drive" `
        -StartLatitude $script:RoutePoints.belowThresholdEnd.Latitude -StartLongitude $script:RoutePoints.belowThresholdEnd.Longitude `
        -EndLatitude $script:RoutePoints.shortBurstEnd.Latitude -EndLongitude $script:RoutePoints.shortBurstEnd.Longitude `
        -PointCount 2 -ExpectedBucket "NEEDS_REVIEW" -WaitSeconds $PointDelaySeconds
    $shortBurst = Capture-State -Name "short-burst-no-drive"
    Assert-Condition -Condition ([int](Get-TodayTotals -State $shortBurst).driveMinutes -eq 0) -Message "Drive started before 15 mph was sustained for 1 minute."

    Send-LinearRoute -Stage "sustained-commute-drive" `
        -StartLatitude $script:RoutePoints.shortBurstEnd.Latitude -StartLongitude $script:RoutePoints.shortBurstEnd.Longitude `
        -EndLatitude $script:RoutePoints.driveEnd.Latitude -EndLongitude $script:RoutePoints.driveEnd.Longitude `
        -PointCount 6 -ExpectedBucket "DRIVE" -WaitSeconds $PointDelaySeconds
    Wait-Log -Name "drive-transition-observed" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Activity bucket transition recorded" -and $_.data.bucket -eq "DRIVE" }).Count -gt 0
    } | Out-Null
    Wait-Log -Name "vehicle-mode-observed" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Away movement location updates started" -and $_.data.mode -eq "VEHICLE" }).Count -gt 0
    } | Out-Null
    Wait-Log -Name "location-broadcast-observed" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Drive mileage location processed" }).Count -gt 0
    } | Out-Null
    $driveStarted = Wait-State -Name "drive-after-sustained-threshold" -Condition { param($candidate) [int](Get-TodayTotals -State $candidate).driveMinutes -gt 0 }
    Assert-MinimumIncrease -Before (Get-TotalDrivenMiles -State $shortBurst) -After (Get-TotalDrivenMiles -State $driveStarted) -Minimum 0.05 -Message "Commute miles did not increase."

    Send-TestLocation -Stage "site-enter" -Latitude $Site.Latitude -Longitude $Site.Longitude -ExpectedBucket "SITE" -WaitSeconds 3
    Wait-Log -Name "site-enter-observed" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Work geofence enter or dwell received" }).Count -gt 0
    } | Out-Null
    $siteEntered = Wait-State -Name "site-entered-state" -Condition {
        param($candidate)
        [bool]$candidate.snapshot.atWork -and [int](Get-TodayTotals -State $candidate).siteMinutes -ge 0
    }
    $driveAtSiteEnter = [int](Get-TodayTotals -State $siteEntered).driveMinutes
    $milesAtSiteEnter = Get-TotalDrivenMiles -State $siteEntered

    $siteDriveTargets = @(
        @{ Latitude = $Site.Latitude + 0.0030; Longitude = $Site.Longitude },
        @{ Latitude = $Site.Latitude - 0.0030; Longitude = $Site.Longitude },
        @{ Latitude = $Site.Latitude + 0.0030; Longitude = $Site.Longitude },
        @{ Latitude = $Site.Latitude - 0.0030; Longitude = $Site.Longitude },
        @{ Latitude = $Site.Latitude + 0.0030; Longitude = $Site.Longitude },
        @{ Latitude = $Site.Latitude - 0.0030; Longitude = $Site.Longitude }
    )
    $siteDrive = $null
    for ($i = 0; $i -lt $siteDriveTargets.Count; $i++) {
        $target = $siteDriveTargets[$i]
        Send-TestLocation -Stage "site-drive-counts-miles-not-drive-$($i + 1)" `
            -Latitude $target.Latitude `
            -Longitude $target.Longitude `
            -ExpectedBucket "SITE" `
            -WaitSeconds 35
        $candidateSiteDrive = Capture-State -Name "site-drive-counts-miles-not-drive-$($i + 1)"
        Assert-NoMoreThanIncrease -Before $driveAtSiteEnter -After ([int](Get-TodayTotals -State $candidateSiteDrive).driveMinutes) -Maximum $MinuteTolerance -Message "Site driving counted as Drive minutes."
        if (((Get-TotalDrivenMiles -State $candidateSiteDrive) - $milesAtSiteEnter) -ge 0.01) {
            $siteDrive = $candidateSiteDrive
            break
        }
    }
    Assert-Condition -Condition ($null -ne $siteDrive) -Message "Site movement did not count miles. Before=$milesAtSiteEnter After=$((Get-TotalDrivenMiles -State (Capture-State -Name 'site-drive-counts-miles-not-drive-final'))) MinimumIncrease=0.01"

    Start-Sleep -Seconds 65
    $siteStopped = Capture-State -Name "site-stopped-non-driving"
    Assert-Condition -Condition ([int](Get-TodayTotals -State $siteStopped).siteMinutes -gt [int](Get-TodayTotals -State $siteEntered).siteMinutes) -Message "Stopped time at Site did not remain Site."

    Send-LinearRoute -Stage "site-exit" `
        -StartLatitude $Site.Latitude -StartLongitude $Site.Longitude `
        -EndLatitude $script:RoutePoints.siteExit.Latitude -EndLongitude $script:RoutePoints.siteExit.Longitude `
        -PointCount 4 -ExpectedBucket "NEEDS_REVIEW" -WaitSeconds 5
    $siteExitSentAt = [Int64]$script:RouteEvents[$script:RouteEvents.Count - 1].sentAtEpochMillis
    Wait-Log -Name "site-exit-observed" -Predicate {
        param($logs)
        @($logs | Where-Object {
                [Int64]$_.timestampEpochMillis -ge $siteExitSentAt -and
                ($_.message -eq "Work geofence exit received" -or $_.message -eq "E2E provider-derived Work geofence exit received")
            }).Count -gt 0
    } | Out-Null
    Wait-State -Name "site-exited-state" -Condition { param($candidate) -not [bool]$candidate.snapshot.atWork } | Out-Null
    Start-Sleep -Seconds 65
    $needsReviewGap = Capture-State -Name "needs-review-after-site-exit"
    Assert-Condition -Condition ([int](Get-TodayTotals -State $needsReviewGap).needsReviewMinutes -gt [int](Get-TodayTotals -State $siteStopped).needsReviewMinutes) -Message "Uncertain away/outside-site gap was not Needs review."
    Assert-NoMoreThanIncrease -Before ([int](Get-TodayTotals -State $siteStopped).idleMinutes) -After ([int](Get-TodayTotals -State $needsReviewGap).idleMinutes) -Maximum $MinuteTolerance -Message "Uncertain gap was silently placed into Idle."

    Send-LinearRoute -Stage "return-commute-drive-bypass" `
        -StartLatitude $script:RoutePoints.siteExit.Latitude -StartLongitude $script:RoutePoints.siteExit.Longitude `
        -EndLatitude $script:RoutePoints.returnBypass.Latitude -EndLongitude $script:RoutePoints.returnBypass.Longitude `
        -PointCount 5 -ExpectedBucket "DRIVE" -WaitSeconds $PointDelaySeconds
    Send-LinearRoute -Stage "return-commute-drive" `
        -StartLatitude $script:RoutePoints.returnBypass.Latitude -StartLongitude $script:RoutePoints.returnBypass.Longitude `
        -EndLatitude $script:RoutePoints.homeExit.Latitude -EndLongitude $script:RoutePoints.homeExit.Longitude `
        -PointCount 6 -ExpectedBucket "DRIVE" -WaitSeconds $PointDelaySeconds
    $returnDrive = Capture-State -Name "return-commute-drive"
    Assert-Condition -Condition ([int](Get-TodayTotals -State $returnDrive).driveMinutes -gt [int](Get-TodayTotals -State $needsReviewGap).driveMinutes) -Message "Return commute did not increase Drive minutes."
    Assert-MinimumIncrease -Before (Get-TotalDrivenMiles -State $needsReviewGap) -After (Get-TotalDrivenMiles -State $returnDrive) -Minimum 0.05 -Message "Return commute miles did not increase."

    $idleTargets = @(
        @{ Latitude = $script:RoutePoints.homeExit.Latitude + 0.00030; Longitude = $script:RoutePoints.homeExit.Longitude },
        @{ Latitude = $script:RoutePoints.homeExit.Latitude + 0.00060; Longitude = $script:RoutePoints.homeExit.Longitude },
        @{ Latitude = $script:RoutePoints.homeExit.Latitude + 0.00030; Longitude = $script:RoutePoints.homeExit.Longitude },
        @{ Latitude = $script:RoutePoints.homeExit.Latitude + 0.00060; Longitude = $script:RoutePoints.homeExit.Longitude },
        @{ Latitude = $script:RoutePoints.homeExit.Latitude + 0.00030; Longitude = $script:RoutePoints.homeExit.Longitude },
        @{ Latitude = $script:RoutePoints.homeExit.Latitude; Longitude = $script:RoutePoints.homeExit.Longitude }
    )
    $idleObserved = $false
    $firstIdlePointSentAt = $null
    for ($i = 0; $i -lt $idleTargets.Count; $i++) {
        $target = $idleTargets[$i]
        Send-TestLocation -Stage "confirmed-idle-after-drive-$($i + 1)" `
            -Latitude $target.Latitude `
            -Longitude $target.Longitude `
            -ExpectedBucket "IDLE" `
            -WaitSeconds 20
        $lastRouteEvent = $script:RouteEvents[$script:RouteEvents.Count - 1]
        if ($null -eq $firstIdlePointSentAt) {
            $firstIdlePointSentAt = [Int64]$lastRouteEvent.sentAtEpochMillis
        }
        $idleLogs = Request-Logs -Last 500
        $idleObserved = @($idleLogs | Where-Object {
                [Int64]$_.timestampEpochMillis -ge ($firstIdlePointSentAt - 10000) -and
                $_.message -eq "Activity bucket transition recorded" -and
                $_.data.bucket -eq "IDLE"
            }).Count -gt 0
        if ($idleObserved) {
            Save-Json -Value $idleLogs -Path (Join-Path $OutputRoot "idle-transition-observed-logs.json")
            break
        }
    }
    if (-not $idleObserved) {
        $activityIdleSentAt = Send-E2eActivityTransition -Stage "confirmed-idle-activity-transition" -Activity "STILL" -Transition "ENTER" -WaitSeconds 2
        Wait-Log -Name "idle-transition-observed" -Predicate {
            param($logs)
            @($logs | Where-Object {
                    [Int64]$_.timestampEpochMillis -ge ($activityIdleSentAt - 10000) -and
                    $_.message -eq "Activity bucket transition recorded" -and
                    $_.data.bucket -eq "IDLE"
                }).Count -gt 0
        } | Out-Null
    }
    Start-Sleep -Seconds 65
    $idleState = Capture-State -Name "confirmed-idle-state"
    Assert-Condition -Condition ([int](Get-TodayTotals -State $idleState).idleMinutes -gt [int](Get-TodayTotals -State $returnDrive).idleMinutes) -Message "Confirmed non-driving away time did not land in Idle."
    Wait-Log -Name "balanced-mode-after-drive-stop" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Away movement location updates started" -and $_.data.mode -eq "BALANCED" }).Count -gt 1
    } | Out-Null

    $beforeHomeReturnMiles = Get-TotalDrivenMiles -State $idleState
    $beforeHomeReturnDrive = [int](Get-TodayTotals -State $idleState).driveMinutes
    Send-TestLocation -Stage "return-inside-home" -Latitude $HomePoint.Latitude -Longitude $HomePoint.Longitude -ExpectedBucket "HOME" -WaitSeconds 40
    $homeReturnProbe = Capture-State -Name "home-return-provider-probe"
    if ($null -ne $homeReturnProbe.snapshot.activeSession) {
        Send-E2eGeofenceTransition -Stage "return-inside-home-geofence-enter" -RequestId "home" -Transition "ENTER" -WaitSeconds 2 | Out-Null
    }
    Wait-Log -Name "home-enter-observed" -Predicate {
        param($logs)
        @($logs | Where-Object {
                $_.message -eq "Home geofence enter or dwell received" -or
                $_.message -eq "E2E provider-derived Home geofence enter received" -or
                ($_.message -eq "E2E geofence transition injected" -and $_.data.requestId -eq "home" -and $_.data.transition -eq "ENTER")
            }).Count -gt 0
    } | Out-Null
    $homeReturned = Wait-State -Name "home-returned-session-stopped" -Condition { param($candidate) $null -eq $candidate.snapshot.activeSession }

    Send-LinearRoute -Stage "post-return-home-movement-excluded" `
        -StartLatitude $HomePoint.Latitude -StartLongitude $HomePoint.Longitude `
        -EndLatitude ($HomePoint.Latitude + 0.0004) -EndLongitude $HomePoint.Longitude `
        -PointCount 2 -ExpectedBucket "HOME" -WaitSeconds 5
    $final = Capture-State -Name "full-day-final-state"
    Assert-NoMoreThanIncrease -Before $beforeHomeReturnMiles -After (Get-TotalDrivenMiles -State $final) -Maximum $MileTolerance -Message "Miles increased after Home return."
    Assert-NoMoreThanIncrease -Before $beforeHomeReturnDrive -After ([int](Get-TodayTotals -State $final).driveMinutes) -Maximum $MinuteTolerance -Message "Drive minutes increased after Home return."

    Save-Screenshot -Name "full-day-final" | Out-Null
    Save-Json -Value @($script:RouteEvents.ToArray()) -Path (Join-Path $OutputRoot "route-events.json")
    return @{ details = "Full-day lifecycle completed from Home to Drive to Site to Needs review to Idle to Home."; artifacts = @{ routeInputs = "route-inputs.json"; routeEvents = "route-events.json"; finalState = "full-day-final-state.json" } }
}

function Assert-BucketAcceptance {
    $state = Capture-State -Name "bucket-acceptance-state"
    $today = Get-TodayTotals -State $state
    foreach ($property in @("awayMinutes", "drivenMiles", "driveMinutes", "siteMinutes", "idleMinutes", "needsReviewMinutes")) {
        Assert-Condition -Condition ($null -ne $today.PSObject.Properties[$property]) -Message "Report snapshot is missing $property."
    }
    $knownTotal = [int]$today.driveMinutes + [int]$today.siteMinutes + [int]$today.idleMinutes + [int]$today.needsReviewMinutes
    $aggregateRoundingTolerance = [Math]::Max($MinuteTolerance, 3)
    $aggregateDelta = [int]$today.awayMinutes - $knownTotal
    Assert-Condition -Condition ($knownTotal -le ([int]$today.awayMinutes + $MinuteTolerance)) -Message "Known bucket minutes exceed Work minutes beyond tolerance."
    Assert-Condition -Condition ($aggregateDelta -le $aggregateRoundingTolerance) -Message "Work exceeds Drive + Site + Idle + Needs review beyond aggregate minute rounding tolerance."
    Assert-Condition -Condition ([int]$today.driveMinutes -gt 0) -Message "Drive minutes are zero."
    Assert-Condition -Condition ([int]$today.siteMinutes -gt 0) -Message "Site minutes are zero."
    Assert-Condition -Condition ([int]$today.idleMinutes -gt 0) -Message "Idle minutes are zero."
    Assert-Condition -Condition ([int]$today.needsReviewMinutes -gt 0) -Message "Needs review minutes are zero."
    Assert-Condition -Condition ([double]$today.drivenMiles -gt 0.1) -Message "Driven miles are too low."
    Assert-Condition -Condition (([int]$today.driveMinutes + [int]$today.siteMinutes + [int]$today.idleMinutes) -lt [int]$today.awayMinutes) -Message "Drive + Site + Idle unexpectedly equals Work even though Needs review exists."

    $expectedActual = [ordered]@{
        tolerance = @{ minutes = $MinuteTolerance; aggregateMinuteRounding = $aggregateRoundingTolerance; miles = $MileTolerance }
        expected = [ordered]@{
            workMinutes = "equals total trackable away-from-Home time; aggregate bucket total may be lower by per-bucket minute rounding"
            driveMinutes = "> 0, starts only after 15 mph sustained for 1 minute"
            siteMinutes = "> 0, overrides Drive inside saved Site geofence"
            idleMinutes = "> 0, only after confirmed drive stop"
            needsReviewMinutes = "> 0 for uncertain away/outside-site gap"
            miles = "> 0, no increase after Home return, may increase inside Site"
            aggregateBucketDelta = "workMinutes - (drive + site + idle + needsReview) <= aggregateMinuteRounding"
        }
        actual = [ordered]@{
            workMinutes = [int]$today.awayMinutes
            driveMinutes = [int]$today.driveMinutes
            siteMinutes = [int]$today.siteMinutes
            idleMinutes = [int]$today.idleMinutes
            needsReviewMinutes = [int]$today.needsReviewMinutes
            knownBucketMinutes = $knownTotal
            aggregateBucketDelta = $aggregateDelta
            miles = [double]$today.drivenMiles
        }
    }
    Save-Json -Value $expectedActual -Path (Join-Path $OutputRoot "expected-vs-actual-buckets.json")
    return @{ details = "Bucket acceptance totals satisfy full-day invariants."; artifacts = @{ buckets = "expected-vs-actual-buckets.json" } }
}

function Assert-NoDebugWriteEndpointEvidence {
    $forbidden = "/testing/inject-mock-drive|/testing/prepare-mock-drive|/testing/finish-mock-drive|/testing/seed-jobsite-drive|Mock drive|Jobsite drive policy scenario seeded"
    $artifactMatches = @(Get-ChildItem -LiteralPath $OutputRoot -Recurse -File | Where-Object {
            $_.Length -lt 5MB
        } | Select-String -Pattern $forbidden -ErrorAction SilentlyContinue)
    Assert-Condition -Condition ($artifactMatches.Count -eq 0) -Message "Forbidden debug write endpoint evidence was found in verifier artifacts."
    return @{ details = "/testing/state was the only app debug endpoint used for acceptance observation." }
}

function Invoke-NonTrackableDayScenario {
    Invoke-AdbShell -Arguments @("pm", "clear", $PackageName) | Out-Null
    Grant-TrackingPermissions
    Start-App
    Ensure-PrivacyAndActivityDetection | Out-Null
    Configure-HomeAndSite | Out-Null
    Tap-Tag -Tag "nav_settings" -StepName "non-trackable-settings-nav" | Out-Null
    $state = Capture-State -Name "non-trackable-before-toggle"
    if ([bool]$state.snapshot.trackableToday) {
        $todayTag = "settings_workday_$(([string]$state.snapshot.todayDayOfWeek).ToLowerInvariant())_switch"
        Tap-Tag -Tag $todayTag -StepName "non-trackable-disable-today" | Out-Null
        Wait-State -Name "non-trackable-after-toggle" -Condition { param($candidate) -not [bool]$candidate.snapshot.trackableToday } | Out-Null
    }
    Send-TestLocation -Stage "non-trackable-inside-home" -Latitude $HomePoint.Latitude -Longitude $HomePoint.Longitude -ExpectedBucket "HOME" -WaitSeconds 3
    Send-TestLocation -Stage "non-trackable-home-exit" -Latitude $script:RoutePoints.homeExit.Latitude -Longitude $script:RoutePoints.homeExit.Longitude -ExpectedBucket "NO_WORK" -WaitSeconds 5
    Send-E2eGeofenceTransition -Stage "non-trackable-home-exit-geofence" -RequestId "home" -Transition "EXIT" -WaitSeconds 2 | Out-Null
    Wait-Log -Name "non-trackable-exit-observed" -Predicate {
        param($logs)
        @($logs | Where-Object { $_.message -eq "Geofence exit ignored for non-trackable day" }).Count -gt 0
    } | Out-Null
    $after = Capture-State -Name "non-trackable-after-home-exit"
    Assert-Condition -Condition ($null -eq $after.snapshot.activeSession) -Message "Non-trackable day Home exit created an active session."
    Assert-Condition -Condition ([int](Get-TodayTotals -State $after).awayMinutes -eq 0) -Message "Non-trackable day created Work minutes."
    return @{ details = "Home exit on non-trackable day did not create Work time."; artifacts = @{ state = "non-trackable-after-home-exit-state.json" } }
}

function Write-FinalArtifacts {
    $logs = @()
    try {
        $logs = Request-Logs
    } catch {
        $logs = @([ordered]@{ level = "error"; message = "Failed to read host debug logs"; error = $_.Exception.Message })
    }
    Save-Json -Value $logs -Path (Join-Path $OutputRoot "recent-app-logs.json")
    Invoke-AdbShell -Arguments @("logcat", "-d", "-t", "500", "TimeTracker:D", "*:S") -AllowFailure |
        Set-Content -LiteralPath (Join-Path $OutputRoot "recent-app-logcat.txt") -Encoding UTF8
    Save-Json -Value @($script:Stages.ToArray()) -Path (Join-Path $OutputRoot "stage-results.json")

    $summary = [ordered]@{
        runId = $RunId
        deviceId = $script:ResolvedDeviceId
        packageName = $PackageName
        outputRoot = $OutputRoot
        startedAtDate = $dateLabel
        status = if ($script:RunFailure) { "failed" } elseif (@($script:Stages | Where-Object { $_.status -eq "manual_required" }).Count -gt 0) { "manual_required" } else { "passed" }
        failure = $script:RunFailure
        stages = @($script:Stages.ToArray())
    }
    Save-Json -Value $summary -Path (Join-Path $OutputRoot "summary.json")

    $report = New-Object System.Collections.Generic.List[string]
    $report.Add("# Full-Day Bucket Verification Report") | Out-Null
    $report.Add("") | Out-Null
    $report.Add(('- Run: `{0}`' -f $RunId)) | Out-Null
    $report.Add(('- Device: `{0}`' -f $script:ResolvedDeviceId)) | Out-Null
    $report.Add(('- Package: `{0}`' -f $PackageName)) | Out-Null
    $report.Add(('- Status: `{0}`' -f $summary.status)) | Out-Null
    $report.Add("") | Out-Null
    $report.Add("## Stages") | Out-Null
    foreach ($stage in $script:Stages) {
        $report.Add("- $($stage.status): $($stage.name) $($stage.details)") | Out-Null
    }
    $report.Add("") | Out-Null
    $report.Add("## Artifacts") | Out-Null
    $report.Add("- route-inputs.json") | Out-Null
    $report.Add("- route-events.json") | Out-Null
    $report.Add("- expected-vs-actual-buckets.json") | Out-Null
    $report.Add("- stage-results.json") | Out-Null
    $report.Add("- recent-app-logs.json") | Out-Null
    $report.Add("- recent-app-logcat.txt") | Out-Null
    $report | Set-Content -LiteralPath (Join-Path $OutputRoot "report.md") -Encoding UTF8
}

try {
    Invoke-Stage -Name "static_location_thresholds_are_meters" -Action { Assert-LocationThresholdsAreMeters } | Out-Null
    Invoke-Stage -Name "static_no_route_geometry_persistence" -Action { Assert-NoRoutePersistence } | Out-Null
    Invoke-Stage -Name "install_reset_grant_launch" -Action {
        Start-DebugInfrastructure
        @{ details = "Installed with -PtimeTracker.e2eDebug=true, reset app data, granted location/activity permissions, and launched app." }
    } | Out-Null
    Invoke-Stage -Name "privacy_and_activity_detection" -Action { Ensure-PrivacyAndActivityDetection } | Out-Null
    Invoke-Stage -Name "trackable_workday" -Action { Ensure-TodayTrackable } | Out-Null
    Invoke-Stage -Name "home_and_site_geofence_setup" -Action { Configure-HomeAndSite } | Out-Null
    Invoke-Stage -Name "full_day_bucket_lifecycle" -Action { Invoke-FullDayLifecycle } | Out-Null
    Invoke-Stage -Name "bucket_acceptance" -Action { Assert-BucketAcceptance } | Out-Null
    Invoke-Stage -Name "no_debug_write_endpoint_acceptance" -Action { Assert-NoDebugWriteEndpointEvidence } | Out-Null
    if ($IncludeNonTrackableDayScenario) {
        Invoke-Stage -Name "non_trackable_day_no_work" -Action { Invoke-NonTrackableDayScenario } | Out-Null
    } else {
        Invoke-Stage -Name "non_trackable_day_no_work" -ManualRequiredDoesNotFail -Action {
            @{ status = "manual_required"; details = "Run again with -IncludeNonTrackableDayScenario to execute this destructive follow-up scenario." }
        } | Out-Null
    }
    Invoke-Stage -Name "real_s21_activity_workday" -ManualRequiredDoesNotFail:(!$RequireRealActivityRecognitionEvidence) -Action {
        $logs = Request-Logs
        $activityEvents = @($logs | Where-Object { $_.message -eq "Activity transition received" })
        if ($activityEvents.Count -gt 0) {
            return @{ details = "Observed Play Services ActivityTransition receiver events during this S21 run." }
        }
        return @{ status = "manual_required"; details = "ADB location simulation cannot force Play Services Activity Recognition detector behavior; complete a real S21 workday/drive check." }
    } | Out-Null
    Invoke-Stage -Name "sms_safety_final_gate" -Action { Assert-SmsSafety } | Out-Null
} catch {
    $script:RunFailure = $_.Exception.Message
    throw
} finally {
    Save-Json -Value @($script:RouteEvents.ToArray()) -Path (Join-Path $OutputRoot "route-events.json")
    Write-FinalArtifacts
    Remove-TestLocationProvider
    Write-Host "Full-day bucket verification artifacts written to $OutputRoot"
}
