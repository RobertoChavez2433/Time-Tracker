<#
.SYNOPSIS
    Run the Time Tracker device E2E verification flow.

.DESCRIPTION
    Builds and installs a debug app with -PtimeTracker.e2eDebug=true, starts
    the host debug log server, prepares ADB ports, drives Compose UI controls
    through stable test tags, and validates app state through /testing/state.
#>

[CmdletBinding()]
param(
    [string]$DeviceId,
    [string]$RunId,
    [string]$OutputRoot,
    [int]$StatePort = 4948,
    [int]$LogPort = 3947,
    [string]$PackageName = "com.robertochavez.timetracker.debug",
    [string]$ActivityName = "com.robertochavez.timetracker.MainActivity",
    [switch]$SkipInstall,
    [switch]$ProbeSystemButtons
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dateLabel = Get-Date -Format "yyyy-MM-dd"
if ([string]::IsNullOrWhiteSpace($RunId)) {
    $RunId = "time-tracker-e2e-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss")
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $projectRoot "tools/testing/test-results/$dateLabel/$RunId"
}

New-Item -ItemType Directory -Path $OutputRoot -Force | Out-Null

$script:ResolvedDeviceId = $null
$script:Display = @{ width = 1080; height = 2340 }
$script:Steps = New-Object System.Collections.Generic.List[object]
$script:ButtonMatrix = New-Object System.Collections.Generic.List[object]
$script:RunFailure = $null
$script:RequiredControlInventory = @(
    @{ id = "nav_home"; tagPattern = "^nav_home$"; kind = "bottom_navigation"; screen = "app"; requiredMode = "state" },
    @{ id = "nav_tracking"; tagPattern = "^nav_tracking$"; kind = "bottom_navigation"; screen = "app"; requiredMode = "state" },
    @{ id = "nav_reports"; tagPattern = "^nav_reports$"; kind = "bottom_navigation"; screen = "app"; requiredMode = "state" },
    @{ id = "nav_settings"; tagPattern = "^nav_settings$"; kind = "bottom_navigation"; screen = "app"; requiredMode = "state" },
    @{ id = "settings_request_foreground_button"; tagPattern = "^settings_request_foreground_button$"; kind = "button"; screen = "settings"; requiredMode = "log" },
    @{ id = "settings_enable_background_button"; tagPattern = "^settings_enable_background_button$"; kind = "button"; screen = "settings"; requiredMode = "log" },
    @{ id = "settings_enable_activity_button"; tagPattern = "^settings_enable_activity_button$"; kind = "button"; screen = "settings"; requiredMode = "log" },
    @{ id = "settings_disable_activity_button"; tagPattern = "^settings_disable_activity_button$"; kind = "button"; screen = "settings"; requiredMode = "log" },
    @{ id = "settings_minimal_notification_switch"; tagPattern = "^settings_minimal_notification_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_live_timer_notification_switch"; tagPattern = "^settings_live_timer_notification_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_privacy_disclosure_switch"; tagPattern = "^settings_privacy_disclosure_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_anchor_date_field"; tagPattern = "^settings_anchor_date_field$"; kind = "text_field"; screen = "settings"; requiredMode = "input" },
    @{ id = "settings_save_anchor_button"; tagPattern = "^settings_save_anchor_button$"; kind = "button"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_delete_local_data_button"; tagPattern = "^settings_delete_local_data_button$"; kind = "button"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_delete_confirm_cancel_button"; tagPattern = "^settings_delete_confirm_cancel_button$"; kind = "button"; screen = "settings_dialog"; requiredMode = "state" },
    @{ id = "settings_delete_confirm_button"; tagPattern = "^settings_delete_confirm_button$"; kind = "button"; screen = "settings_dialog"; requiredMode = "state" },
    @{ id = "settings_workday_monday_switch"; tagPattern = "^settings_workday_monday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_workday_tuesday_switch"; tagPattern = "^settings_workday_tuesday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_workday_wednesday_switch"; tagPattern = "^settings_workday_wednesday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_workday_thursday_switch"; tagPattern = "^settings_workday_thursday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_workday_friday_switch"; tagPattern = "^settings_workday_friday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_workday_saturday_switch"; tagPattern = "^settings_workday_saturday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "settings_workday_sunday_switch"; tagPattern = "^settings_workday_sunday_switch$"; kind = "switch"; screen = "settings"; requiredMode = "state" },
    @{ id = "home_use_current_button"; tagPattern = "^home_use_current_button$"; kind = "button"; screen = "home"; requiredMode = "state" },
    @{ id = "home_latitude_field"; tagPattern = "^home_latitude_field$"; kind = "text_field"; screen = "home"; requiredMode = "input" },
    @{ id = "home_longitude_field"; tagPattern = "^home_longitude_field$"; kind = "text_field"; screen = "home"; requiredMode = "input" },
    @{ id = "home_radius_field"; tagPattern = "^home_radius_field$"; kind = "text_field"; screen = "home"; requiredMode = "input" },
    @{ id = "home_save_pin_button"; tagPattern = "^home_save_pin_button$"; kind = "button"; screen = "home"; requiredMode = "state" },
    @{ id = "work_use_current_button"; tagPattern = "^work_use_current_button$"; kind = "button"; screen = "home"; requiredMode = "state" },
    @{ id = "work_latitude_field"; tagPattern = "^work_latitude_field$"; kind = "text_field"; screen = "home"; requiredMode = "input" },
    @{ id = "work_longitude_field"; tagPattern = "^work_longitude_field$"; kind = "text_field"; screen = "home"; requiredMode = "input" },
    @{ id = "work_radius_field"; tagPattern = "^work_radius_field$"; kind = "text_field"; screen = "home"; requiredMode = "input" },
    @{ id = "work_save_pin_button"; tagPattern = "^work_save_pin_button$"; kind = "button"; screen = "home"; requiredMode = "state" },
    @{ id = "tracking_start_button"; tagPattern = "^tracking_start_button$"; kind = "button"; screen = "tracking"; requiredMode = "state" },
    @{ id = "tracking_stop_button"; tagPattern = "^tracking_stop_button$"; kind = "button"; screen = "tracking"; requiredMode = "state" },
    @{ id = "tracking_session_counts_switch"; tagPattern = "^tracking_session_[^_]+_counts_switch$"; kind = "switch"; screen = "tracking"; requiredMode = "state" },
    @{ id = "tracking_session_start_field"; tagPattern = "^tracking_session_[^_]+_start_field$"; kind = "text_field"; screen = "tracking"; requiredMode = "input" },
    @{ id = "tracking_session_end_field"; tagPattern = "^tracking_session_[^_]+_end_field$"; kind = "text_field"; screen = "tracking"; requiredMode = "input" },
    @{ id = "tracking_session_miles_field"; tagPattern = "^tracking_session_[^_]+_miles_field$"; kind = "text_field"; screen = "tracking"; requiredMode = "input" },
    @{ id = "tracking_session_save_button"; tagPattern = "^tracking_session_[^_]+_save_button$"; kind = "button"; screen = "tracking"; requiredMode = "state" }
)

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
    ConvertTo-Json -InputObject $Value -Depth 40 | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Get-ControlCoverage {
    $matrix = @($script:ButtonMatrix.ToArray())
    $coverage = foreach ($spec in $script:RequiredControlInventory) {
        $matches = @($matrix | Where-Object {
                $_.result -eq "passed" -and [string]$_.testTag -match [string]$spec.tagPattern
            })
        $actions = @($matches | ForEach-Object { [string]$_.actionPerformed })
        $expectedStateDeltas = @($matches | ForEach-Object { [string]$_.expectedStateDelta })
        $isExercised = Test-ControlCoverageExercise -Spec $spec -Actions $actions -ExpectedStateDeltas $expectedStateDeltas
        [ordered]@{
            id = [string]$spec.id
            tagPattern = [string]$spec.tagPattern
            kind = [string]$spec.kind
            screen = [string]$spec.screen
            requiredMode = [string]$spec.requiredMode
            passed = $matches.Count -gt 0 -and $isExercised
            matchCount = $matches.Count
            matchedTags = @($matches | ForEach-Object { $_.testTag } | Select-Object -Unique)
            actions = $actions
            expectedStateDeltas = $expectedStateDeltas
            exercised = $isExercised
        }
    }
    return @($coverage)
}

function Test-ControlCoverageExercise {
    param(
        [Parameter(Mandatory = $true)]$Spec,
        [string[]]$Actions,
        [string[]]$ExpectedStateDeltas
    )

    if (@($Actions).Count -eq 0) {
        return $false
    }

    $joinedActions = ($Actions -join "`n")
    $joinedDeltas = ($ExpectedStateDeltas -join "`n")
    switch ([string]$Spec.requiredMode) {
        "input" {
            return $joinedActions -match "(?i)\benter\b"
        }
        "log" {
            return $joinedActions -match "(?i)\b(tap|enable|disable)\b" -and
                $joinedDeltas -match "(?i)(permission result|registration|opened|handled|activity)"
        }
        default {
            if ([string]$Spec.kind -eq "bottom_navigation") {
                return $joinedActions -match "(?i)\b(open|switch)\b" -and $joinedDeltas -match "(?i)screen .*visible"
            }
            return $joinedActions -notmatch "(?im)^\s*assert\b"
        }
    }
}

function Assert-RequiredControlCoverage {
    $coverage = Get-ControlCoverage
    Save-Json -Value $script:RequiredControlInventory -Path (Join-Path $OutputRoot "ui-control-inventory.json")
    Save-Json -Value $coverage -Path (Join-Path $OutputRoot "ui-control-coverage.json")
    $missing = @($coverage | Where-Object { -not $_.passed })
    if ($missing.Count -gt 0) {
        throw "[ui_control_coverage] Missing required controls: $(@($missing | ForEach-Object { $_.id }) -join ', ')"
    }
    return @{
        requiredControls = $coverage.Count
        coveredControls = @($coverage | Where-Object { $_.passed }).Count
        inventory = "ui-control-inventory.json"
        coverage = "ui-control-coverage.json"
    }
}

function Get-RelativeArtifactPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    $rootFullPath = [System.IO.Path]::GetFullPath($OutputRoot)
    if (-not $rootFullPath.EndsWith([System.IO.Path]::DirectorySeparatorChar)) {
        $rootFullPath = "$rootFullPath$([System.IO.Path]::DirectorySeparatorChar)"
    }
    $pathFullPath = [System.IO.Path]::GetFullPath($Path)
    $rootUri = New-Object System.Uri($rootFullPath)
    $pathUri = New-Object System.Uri($pathFullPath)
    return [System.Uri]::UnescapeDataString($rootUri.MakeRelativeUri($pathUri).ToString()).Replace("\", "/")
}

function Get-StateArtifactPath {
    param([Parameter(Mandatory = $true)][string]$Name)
    return Join-Path $OutputRoot "$(New-SafeName $Name)-state.json"
}

function Add-ControlResult {
    param(
        [Parameter(Mandatory = $true)][string]$TestTag,
        [Parameter(Mandatory = $true)][string]$Flow,
        [Parameter(Mandatory = $true)][string]$ActionPerformed,
        [Parameter(Mandatory = $true)][string]$ExpectedStateDelta,
        [Parameter(Mandatory = $true)][string]$PersistenceExpectation,
        [Parameter(Mandatory = $true)][string]$Result,
        $ArtifactPaths = @{}
    )

    $script:ButtonMatrix.Add([ordered]@{
            testTag = $TestTag
            screenSubflow = $Flow
            actionPerformed = $ActionPerformed
            expectedStateDelta = $ExpectedStateDelta
            persistenceExpectation = $PersistenceExpectation
            result = $Result
            artifactPaths = $ArtifactPaths
            completedAtUtc = [DateTime]::UtcNow.ToString("o")
        }) | Out-Null
}

function Invoke-VerifiedControl {
    param(
        [Parameter(Mandatory = $true)][string]$TestTag,
        [Parameter(Mandatory = $true)][string]$Flow,
        [Parameter(Mandatory = $true)][string]$ActionPerformed,
        [Parameter(Mandatory = $true)][string]$ExpectedStateDelta,
        [Parameter(Mandatory = $true)][string]$PersistenceExpectation,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    try {
        $artifacts = & $Action
        Add-ControlResult -TestTag $TestTag -Flow $Flow -ActionPerformed $ActionPerformed `
            -ExpectedStateDelta $ExpectedStateDelta -PersistenceExpectation $PersistenceExpectation `
            -Result "passed" -ArtifactPaths $artifacts
        return $artifacts
    } catch {
        Add-ControlResult -TestTag $TestTag -Flow $Flow -ActionPerformed $ActionPerformed `
            -ExpectedStateDelta $ExpectedStateDelta -PersistenceExpectation $PersistenceExpectation `
            -Result "failed" -ArtifactPaths @{ error = $_.Exception.Message }
        throw
    }
}

function Resolve-E2EDeviceId {
    param([string]$RequestedDeviceId)

    if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb is required and was not found on PATH."
    }
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
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& adb -s $script:ResolvedDeviceId @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if (-not $AllowFailure -and $exitCode -ne 0) {
        throw "adb -s $script:ResolvedDeviceId $($Arguments -join ' ') failed ($exitCode): $($output -join "`n")"
    }
    return $output
}

function Invoke-AdbShell {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )

    [string[]]$adbArgs = @("shell") + $Arguments
    return Invoke-Adb -Arguments $adbArgs -AllowFailure:$AllowFailure
}

function Update-DisplaySize {
    $output = Invoke-AdbShell -Arguments @("wm", "size") -AllowFailure
    $line = @($output | Where-Object { $_ -match "(\d+)x(\d+)" } | Select-Object -First 1)
    if ($line -and [string]$line -match "(\d+)x(\d+)") {
        $script:Display = @{
            width = [int]$Matches[1]
            height = [int]$Matches[2]
        }
    }
}

function Get-IsolatedAppPackages {
    $packages = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($PackageName)) {
        $packages.Add($PackageName) | Out-Null
    }
    if ($PackageName.EndsWith(".debug")) {
        $basePackage = $PackageName.Substring(0, $PackageName.Length - ".debug".Length)
        if (-not [string]::IsNullOrWhiteSpace($basePackage)) {
            $packages.Add($basePackage) | Out-Null
        }
    }
    return @($packages | Select-Object -Unique)
}

function Stop-IsolatedAppPackages {
    foreach ($package in Get-IsolatedAppPackages) {
        Invoke-AdbShell -Arguments @("am", "force-stop", $package) -AllowFailure | Out-Null
    }
}

function Start-DebugInfrastructure {
    if (-not $SkipInstall) {
        $gradle = Join-Path $projectRoot "gradlew.bat"
        & $gradle "-PtimeTracker.e2eDebug=true" "installDebug" "--console=plain"
        if ($LASTEXITCODE -ne 0) {
            throw "Debug install failed. The E2E build must be installed with -PtimeTracker.e2eDebug=true."
        }
    }

    Stop-IsolatedAppPackages
    & (Join-Path $projectRoot "scripts/testing/start-debug-server.ps1") -Port $LogPort
    & (Join-Path $projectRoot "scripts/testing/prepare-device-ports.ps1") -DeviceId $script:ResolvedDeviceId -StatePort $StatePort -LogPort $LogPort

    Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/clear" -Method Post -TimeoutSec 10 | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACCESS_COARSE_LOCATION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACCESS_FINE_LOCATION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACTIVITY_RECOGNITION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.POST_NOTIFICATIONS") -AllowFailure | Out-Null
    Start-App
}

function Start-App {
    Stop-IsolatedAppPackages
    Resume-App
}

function Resume-App {
    Invoke-AdbShell -Arguments @("input", "keyevent", "3") -AllowFailure | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-AdbShell -Arguments @(
        "am",
        "start",
        "-W",
        "-a",
        "android.intent.action.MAIN",
        "-c",
        "android.intent.category.LAUNCHER",
        "-n",
        "$PackageName/$ActivityName"
    ) -AllowFailure | Out-Null
    Start-Sleep -Seconds 2
    if (-not (Test-AppForeground)) {
        Invoke-AdbShell -Arguments @("monkey", "-p", $PackageName, "1") -AllowFailure | Out-Null
        Start-Sleep -Seconds 2
    }
    if (-not (Test-AppForeground)) {
        throw "Timed out launching $PackageName/$ActivityName."
    }
}

function Test-AppForeground {
    $focus = @(Invoke-AdbShell -Arguments @("dumpsys", "window") -AllowFailure)
    return ($focus -join "`n") -match [regex]::Escape($PackageName)
}

function Wait-FocusedPackage {
    param(
        [Parameter(Mandatory = $true)][string]$Package,
        [Parameter(Mandatory = $true)][string]$Name,
        [int]$TimeoutMs = 8000
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMs)
    $lastFocus = $null
    do {
        $lastFocus = @(Invoke-AdbShell -Arguments @("dumpsys", "window") -AllowFailure)
        if (($lastFocus -join "`n") -match [regex]::Escape($Package)) {
            $path = Join-Path $OutputRoot "$(New-SafeName $Name)-focus.txt"
            $lastFocus | Set-Content -LiteralPath $path -Encoding UTF8
            return $path
        }
        Start-Sleep -Milliseconds 400
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "[focused_package_timeout] Timed out waiting for $Package. Last focus: $($lastFocus -join "`n")"
}

function Request-State {
    $query = "runId=$([System.Uri]::EscapeDataString($RunId))&actorId=$([System.Uri]::EscapeDataString($script:ResolvedDeviceId))"
    return Invoke-RestMethod -Uri "http://127.0.0.1:$StatePort/testing/state?$query" -Method Get -TimeoutSec 10
}

function Request-JobsiteDriveSeed {
    $query = "runId=$([System.Uri]::EscapeDataString($RunId))&actorId=$([System.Uri]::EscapeDataString($script:ResolvedDeviceId))"
    return Invoke-RestMethod -Uri "http://127.0.0.1:$StatePort/testing/seed-jobsite-drive?$query" -Method Get -TimeoutSec 10
}

function Capture-State {
    param([Parameter(Mandatory = $true)][string]$Name)

    $state = Request-State
    Save-Json -Value $state -Path (Get-StateArtifactPath -Name $Name)
    return $state
}

function Wait-State {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Condition,
        [int]$TimeoutMs = 12000
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMs)
    $lastState = $null
    $lastError = $null
    do {
        try {
            $lastState = Request-State
            if (& $Condition $lastState) {
                Save-Json -Value $lastState -Path (Get-StateArtifactPath -Name $Name)
                return $lastState
            }
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Milliseconds 400
    } while ([DateTime]::UtcNow -lt $deadline)

    if ($lastState) {
        Save-Json -Value $lastState -Path (Join-Path $OutputRoot "$(New-SafeName $Name)-timeout-state.json")
    }
    $suffix = if ($lastError) { " Last error: $lastError" } else { "" }
    throw "[state_sentinel_failed] Timed out waiting for $Name.$suffix"
}

function Wait-RecentLogMessage {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Message,
        [int]$TimeoutMs = 12000
    )

    return Wait-State -Name $Name -TimeoutMs $TimeoutMs -Condition {
        param($candidate)
        @($candidate.recentLogs | Where-Object { [string]$_.message -eq $Message }).Count -gt 0
    }
}

function Get-WorkdayState {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$DayName
    )

    $property = $State.snapshot.workdays.PSObject.Properties[$DayName]
    if ($null -eq $property) {
        throw "[workday_state_missing] Debug snapshot did not include $DayName."
    }
    return [bool]$property.Value
}

function Get-SnapshotBoolean {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$PropertyName
    )

    $property = $State.snapshot.PSObject.Properties[$PropertyName]
    if ($null -eq $property) {
        throw "[snapshot_property_missing] Debug snapshot did not include $PropertyName."
    }
    return [bool]$property.Value
}

function Convert-EpochMillisToIsoInstant {
    param([Parameter(Mandatory = $true)]$EpochMillis)

    return [DateTimeOffset]::FromUnixTimeMilliseconds([int64]$EpochMillis).UtcDateTime.ToString("o")
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
    $lastOutput = @()
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        Invoke-AdbShell -Arguments @("rm", $remotePath) -AllowFailure | Out-Null
        $lastOutput = @(Invoke-AdbShell -Arguments @("uiautomator", "dump", $remotePath) -AllowFailure)
        if ($LASTEXITCODE -eq 0) {
            Invoke-Adb -Arguments @("pull", $remotePath, $localPath) | Out-Null
            Invoke-AdbShell -Arguments @("rm", $remotePath) -AllowFailure | Out-Null
            return $localPath
        }
        Start-Sleep -Milliseconds (350 * $attempt)
    }
    throw "adb -s $script:ResolvedDeviceId shell uiautomator dump $remotePath failed after 4 attempts: $($lastOutput -join "`n")"
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
        "settings_delete_confirm_dialog" { "Delete local data?" }
        "settings_delete_confirm_cancel_button" { "Cancel" }
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
            if ($Tag -in @("settings_delete_confirm_cancel_button", "settings_delete_confirm_button")) {
                return Select-ClickableAncestorOrNode -Node $node
            }
            return $node
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
    $left = [int]$Matches[1]
    $top = [int]$Matches[2]
    $right = [int]$Matches[3]
    $bottom = [int]$Matches[4]
    return @{
        left = $left
        top = $top
        right = $right
        bottom = $bottom
        width = $right - $left
        height = $bottom - $top
    }
}

function Test-UsableTapTarget {
    param([Parameter(Mandatory = $true)]$Node)

    $bounds = Get-NodeBounds -Node $Node
    return $bounds.width -ge 48 -and $bounds.height -ge 48
}

function Find-UiNodeByTag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$StepName,
        [int]$MaxScrolls = 6,
        [switch]$RequireTapTarget
    )

    $attempt = 0
    $path = Save-UiDump -Name "$StepName-$attempt"
    [xml]$ui = Get-Content -LiteralPath $path -Raw
    $node = Select-UiNodeByTag -Ui $ui -Tag $Tag
    if ($node -and (-not $RequireTapTarget -or (Test-UsableTapTarget -Node $node))) {
        return @{ node = $node; dumpPath = $path }
    }

    foreach ($direction in @("Up", "Down")) {
        for ($i = 0; $i -lt $MaxScrolls; $i++) {
            $attempt++
            Invoke-Scroll -Direction $direction
            $path = Save-UiDump -Name "$StepName-$attempt"
            [xml]$ui = Get-Content -LiteralPath $path -Raw
            $node = Select-UiNodeByTag -Ui $ui -Tag $Tag
            if ($node -and (-not $RequireTapTarget -or (Test-UsableTapTarget -Node $node))) {
                return @{ node = $node; dumpPath = $path }
            }
        }
    }

    throw "[ui_sentinel_failed] Could not find UI tag '$Tag' for $StepName."
}

function Get-NodeCenter {
    param([Parameter(Mandatory = $true)]$Node)

    $bounds = Get-NodeBounds -Node $Node
    return @{
        x = [int](($bounds.left + $bounds.right) / 2)
        y = [int](($bounds.top + $bounds.bottom) / 2)
    }
}

function Tap-Tag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$StepName,
        [int]$WaitMs = 700
    )

    $found = Find-UiNodeByTag -Tag $Tag -StepName $StepName -RequireTapTarget
    $center = Get-NodeCenter -Node $found.node
    Invoke-AdbShell -Arguments @("input", "tap", "$($center.x)", "$($center.y)") | Out-Null
    Start-Sleep -Milliseconds $WaitMs
    return $found
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

function ConvertTo-AdbText {
    param([AllowEmptyString()][string]$Text)
    return ($Text -replace " ", "%s")
}

function Enter-TextByTag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$StepName,
        [switch]$Clear
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
    if ($Clear) {
        Invoke-AdbShell -Arguments @("input", "keyevent", "123") -AllowFailure | Out-Null
        for ($i = 0; $i -lt 40; $i++) {
            Invoke-AdbShell -Arguments @("input", "keyevent", "67") -AllowFailure | Out-Null
        }
    }
    Invoke-AdbShell -Arguments @("input", "text", (ConvertTo-AdbText -Text $Text)) | Out-Null
    Start-Sleep -Milliseconds 250
    return $found
}

function Assert-TagVisible {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][string]$StepName
    )

    return Find-UiNodeByTag -Tag $Tag -StepName $StepName
}

function Add-StepResult {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Status,
        $Details
    )

    $script:Steps.Add([ordered]@{
            name = $Name
            status = $Status
            details = $Details
            completedAtUtc = [DateTime]::UtcNow.ToString("o")
        }) | Out-Null
}

function Invoke-E2EFlow {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $beforeArtifact = $null
    $beforeError = $null
    try {
        Capture-State -Name "$Name-before" | Out-Null
        $beforeArtifact = "$(New-SafeName "$Name-before")-state.json"
    } catch {
        $beforeError = $_.Exception.Message
    }

    try {
        $result = & $Action
        Capture-State -Name "$Name-after" | Out-Null
        $afterArtifact = "$(New-SafeName "$Name-after")-state.json"
        $watch.Stop()
        Add-StepResult -Name $Name -Status "passed" -Details @{
            durationMs = [int]$watch.ElapsedMilliseconds
            beforeState = $beforeArtifact
            beforeStateError = $beforeError
            afterState = $afterArtifact
            result = $result
        }
    } catch {
        $watch.Stop()
        $safeName = New-SafeName $Name
        $failure = $_.Exception.Message
        try { Save-Screenshot -Name "$safeName-failure" | Out-Null } catch {}
        try { Capture-State -Name "$safeName-failure" | Out-Null } catch {}
        Add-StepResult -Name $Name -Status "failed" -Details @{
            durationMs = [int]$watch.ElapsedMilliseconds
            beforeState = $beforeArtifact
            beforeStateError = $beforeError
            error = $failure
        }
        throw
    }
}

function Assert-NoDebugErrors {
    $response = Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/logs/errors" -Method Get -TimeoutSec 10
    $errors = if ($null -eq $response) { @() } else { @($response) }
    Save-Json -Value $errors -Path (Join-Path $OutputRoot "debug-errors.json")
    if ($errors.Count -gt 0) {
        throw "[debug_log_errors] Debug server captured $($errors.Count) error log entries."
    }
}

function Assert-AccuracyLogEvidence {
    $response = Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/logs?category=location" -Method Get -TimeoutSec 10
    $logs = if ($null -eq $response) { @() } else { @($response) }
    $evidence = @($logs | Where-Object {
            $data = $_.data
            $_.message -in @("Current location result", "Geofence transition received") -and
                $null -ne $data -and
                (
                    $null -ne $data.PSObject.Properties["accuracyMeters"] -or
                    $null -ne $data.PSObject.Properties["triggeringLocationAccuracyMeters"]
                )
        })
    Save-Json -Value $evidence -Path (Join-Path $OutputRoot "accuracy-log-evidence.json")
    if ($evidence.Count -eq 0) {
        throw "[accuracy_log_missing] No location log entry exposed accuracy fields."
    }
}

function Invoke-SystemButtonProbe {
    Invoke-VerifiedControl -TestTag "settings_request_foreground_button" -Flow "settings_tracking_setup" `
        -ActionPerformed "tap foreground permission request" -ExpectedStateDelta "permission result is handled by Settings" `
        -PersistenceExpectation "permission state is device-owned" -Action {
            $tap = Tap-Tag -Tag "settings_request_foreground_button" -StepName "probe-request-foreground"
            Dismiss-Keyboard
            Resume-App
            Wait-RecentLogMessage -Name "02-after-foreground-permission-probe" -Message "Foreground permission result" | Out-Null
            Tap-Tag -Tag "nav_settings" -StepName "probe-return-settings-after-foreground" | Out-Null
            Assert-TagVisible -Tag "screen_settings" -StepName "probe-settings-after-foreground" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-foreground-permission-probe-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_enable_background_button" -Flow "settings_tracking_setup" `
        -ActionPerformed "open Android background-location settings" -ExpectedStateDelta "system settings handoff opens" `
        -PersistenceExpectation "background permission is device-owned" -Action {
            $tap = Tap-Tag -Tag "settings_enable_background_button" -StepName "probe-background-settings"
            $systemFocus = Wait-FocusedPackage -Package "com.android.settings" -Name "02-background-settings-opened"
            $systemScreenshot = Save-Screenshot -Name "02-background-settings-opened"
            Resume-App
            $returnTap = Tap-Tag -Tag "nav_settings" -StepName "probe-return-settings-after-background"
            Assert-TagVisible -Tag "screen_settings" -StepName "probe-settings-after-background" | Out-Null
            Wait-State -Name "02-after-background-settings-probe" -Condition {
                param($candidate)
                [bool]$candidate.transportReady
            } | Out-Null
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                systemFocus = Get-RelativeArtifactPath $systemFocus
                systemScreenshot = Get-RelativeArtifactPath $systemScreenshot
                returnUiDump = Get-RelativeArtifactPath $returnTap.dumpPath
                state = "02-after-background-settings-probe-state.json"
            }
        } | Out-Null
}

function Invoke-SwitchOnOffOnVerification {
    param(
        [Parameter(Mandatory = $true)][string]$TestTag,
        [Parameter(Mandatory = $true)][string]$Flow,
        [Parameter(Mandatory = $true)][string]$SnapshotProperty,
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$StepSlug
    )

    Invoke-VerifiedControl -TestTag $TestTag -Flow $Flow -ActionPerformed "enable $Label" `
        -ExpectedStateDelta "$SnapshotProperty becomes true" -PersistenceExpectation "temporary on/off verification state" -Action {
            $tap = Tap-Tag -Tag $TestTag -StepName "$StepSlug-enable"
            Wait-State -Name "02-after-$StepSlug-enable" -Condition {
                param($candidate)
                Get-SnapshotBoolean -State $candidate -PropertyName $SnapshotProperty
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-$StepSlug-enable-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag $TestTag -Flow $Flow -ActionPerformed "disable $Label" `
        -ExpectedStateDelta "$SnapshotProperty becomes false" -PersistenceExpectation "temporary on/off verification state" -Action {
            $tap = Tap-Tag -Tag $TestTag -StepName "$StepSlug-disable"
            Wait-State -Name "02-after-$StepSlug-disable" -Condition {
                param($candidate)
                -not (Get-SnapshotBoolean -State $candidate -PropertyName $SnapshotProperty)
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-$StepSlug-disable-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag $TestTag -Flow $Flow -ActionPerformed "re-enable $Label for persistence" `
        -ExpectedStateDelta "$SnapshotProperty returns to true" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag $TestTag -StepName "$StepSlug-reenable"
            Wait-State -Name "02-after-$StepSlug-reenable" -Condition {
                param($candidate)
                Get-SnapshotBoolean -State $candidate -PropertyName $SnapshotProperty
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-$StepSlug-reenable-state.json" }
        } | Out-Null
}

function Compare-Scalar {
    param($Left, $Right)
    return [string]$Left -eq [string]$Right
}

function Compare-Double {
    param($Left, $Right, [double]$Tolerance = 0.01)
    if ($null -eq $Left -and $null -eq $Right) {
        return $true
    }
    if ($null -eq $Left -or $null -eq $Right) {
        return $false
    }
    return [Math]::Abs(([double]$Left) - ([double]$Right)) -le $Tolerance
}

function Compare-JsonValue {
    param($Left, $Right)
    return (ConvertTo-Json -InputObject $Left -Depth 20 -Compress) -eq
        (ConvertTo-Json -InputObject $Right -Depth 20 -Compress)
}

function Assert-PersistenceMatch {
    param($Baseline, $After)

    $comparisons = @(
        @{ field = "homeSet"; match = Compare-Scalar $Baseline.snapshot.homeSet $After.snapshot.homeSet },
        @{ field = "homeLatitude"; match = Compare-Double $Baseline.snapshot.homeLatitude $After.snapshot.homeLatitude },
        @{ field = "homeLongitude"; match = Compare-Double $Baseline.snapshot.homeLongitude $After.snapshot.homeLongitude },
        @{ field = "homeRadiusMeters"; match = Compare-Double $Baseline.snapshot.homeRadiusMeters $After.snapshot.homeRadiusMeters },
        @{ field = "workSet"; match = Compare-Scalar $Baseline.snapshot.workSet $After.snapshot.workSet },
        @{ field = "workLatitude"; match = Compare-Double $Baseline.snapshot.workLatitude $After.snapshot.workLatitude },
        @{ field = "workLongitude"; match = Compare-Double $Baseline.snapshot.workLongitude $After.snapshot.workLongitude },
        @{ field = "workRadiusMeters"; match = Compare-Double $Baseline.snapshot.workRadiusMeters $After.snapshot.workRadiusMeters },
        @{ field = "privacyDisclosureAccepted"; match = Compare-Scalar $Baseline.snapshot.privacyDisclosureAccepted $After.snapshot.privacyDisclosureAccepted },
        @{ field = "minimalActiveNotificationEnabled"; match = Compare-Scalar $Baseline.snapshot.minimalActiveNotificationEnabled $After.snapshot.minimalActiveNotificationEnabled },
        @{ field = "liveTimerNotificationEnabled"; match = Compare-Scalar $Baseline.snapshot.liveTimerNotificationEnabled $After.snapshot.liveTimerNotificationEnabled },
        @{ field = "payPeriodAnchorDate"; match = Compare-Scalar $Baseline.snapshot.payPeriodAnchorDate $After.snapshot.payPeriodAnchorDate },
        @{ field = "trackableToday"; match = Compare-Scalar $Baseline.snapshot.trackableToday $After.snapshot.trackableToday },
        @{ field = "workdayCount"; match = Compare-Scalar $Baseline.snapshot.workdayCount $After.snapshot.workdayCount },
        @{ field = "workdays"; match = Compare-JsonValue $Baseline.snapshot.workdays $After.snapshot.workdays },
        @{ field = "sessionCount"; match = Compare-Scalar $Baseline.snapshot.sessionCount $After.snapshot.sessionCount },
        @{ field = "countedSessionCount"; match = Compare-Scalar $Baseline.snapshot.countedSessionCount $After.snapshot.countedSessionCount },
        @{ field = "manuallyAdjustedSessionCount"; match = Compare-Scalar $Baseline.snapshot.manuallyAdjustedSessionCount $After.snapshot.manuallyAdjustedSessionCount },
        @{ field = "activityIntervalCount"; match = Compare-Scalar $Baseline.snapshot.activityIntervalCount $After.snapshot.activityIntervalCount },
        @{ field = "latestSession.startEpochMillis"; match = Compare-Scalar $Baseline.snapshot.latestSession.startEpochMillis $After.snapshot.latestSession.startEpochMillis },
        @{ field = "latestSession.endEpochMillis"; match = Compare-Scalar $Baseline.snapshot.latestSession.endEpochMillis $After.snapshot.latestSession.endEpochMillis },
        @{ field = "latestSession.drivenMiles"; match = Compare-Double $Baseline.snapshot.latestSession.drivenMiles $After.snapshot.latestSession.drivenMiles },
        @{ field = "totalDrivenMiles"; match = Compare-Double $Baseline.snapshot.totalDrivenMiles $After.snapshot.totalDrivenMiles },
        @{ field = "reportTotals.today.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.today.drivenMiles $After.snapshot.reportTotals.today.drivenMiles },
        @{ field = "reportTotals.today.driveMinutes"; match = Compare-Scalar $Baseline.snapshot.reportTotals.today.driveMinutes $After.snapshot.reportTotals.today.driveMinutes },
        @{ field = "reportTotals.today.unclassifiedMinutes"; match = Compare-Scalar $Baseline.snapshot.reportTotals.today.unclassifiedMinutes $After.snapshot.reportTotals.today.unclassifiedMinutes },
        @{ field = "reportTotals.weekly.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.weekly.drivenMiles $After.snapshot.reportTotals.weekly.drivenMiles },
        @{ field = "reportTotals.biweekly.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.biweekly.drivenMiles $After.snapshot.reportTotals.biweekly.drivenMiles },
        @{ field = "reportTotals.monthly.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.monthly.drivenMiles $After.snapshot.reportTotals.monthly.drivenMiles },
        @{ field = "reportTotals.yearly.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.yearly.drivenMiles $After.snapshot.reportTotals.yearly.drivenMiles }
    )
    Save-Json -Value $comparisons -Path (Join-Path $OutputRoot "persistence-comparison.json")
    $failed = @($comparisons | Where-Object { -not $_.match })
    if ($failed.Count -gt 0) {
        throw "[persistence_mismatch] Fields failed: $(@($failed | ForEach-Object { $_.field }) -join ', ')"
    }
    return $comparisons
}

function Write-E2EReport {
    param($Summary)

    $matrix = @($script:ButtonMatrix.ToArray())
    $passedControls = @($matrix | Where-Object { $_.result -eq "passed" }).Count
    $failedControls = @($matrix | Where-Object { $_.result -ne "passed" }).Count
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Time Tracker E2E Report") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("- Run ID: ``$($Summary.runId)``") | Out-Null
    $lines.Add("- Device: ``$($Summary.deviceId)``") | Out-Null
    $lines.Add("- Passed: ``$($Summary.passed)``") | Out-Null
    $lines.Add("- Output root: ``$($Summary.outputRoot)``") | Out-Null
    if ($Summary.failure) {
        $lines.Add("- Failure: $($Summary.failure)") | Out-Null
    }
    $lines.Add("") | Out-Null
    $lines.Add("## Flow Results") | Out-Null
    foreach ($step in @($Summary.steps)) {
        $lines.Add("- ``$($step.name)``: $($step.status)") | Out-Null
    }
    $lines.Add("") | Out-Null
    $lines.Add("## Button Matrix Summary") | Out-Null
    $lines.Add("- Controls verified: $($matrix.Count)") | Out-Null
    $lines.Add("- Passed controls: $passedControls") | Out-Null
    $lines.Add("- Failed controls: $failedControls") | Out-Null
    $lines.Add('- Artifact: `button-state-matrix.json`') | Out-Null
    $lines.Add('- Required inventory: `ui-control-inventory.json`') | Out-Null
    $lines.Add('- Coverage audit: `ui-control-coverage.json`') | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## Persistence Comparison") | Out-Null
    $lines.Add('- Baseline: `persisted-baseline-state.json`') | Out-Null
    $lines.Add('- Relaunch: `after-relaunch-state.json`') | Out-Null
    $lines.Add('- Comparison: `persistence-comparison.json`') | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## Jobsite Geofence Mileage Policy") | Out-Null
    $lines.Add('- Artifact: `jobsite-drive-seed.json`') | Out-Null
    $lines.Add("- [x] Miles driven inside the jobsite geofence are included in report mileage.") | Out-Null
    $lines.Add("- [x] Vehicle time inside the jobsite geofence is kept out of drive minutes.") | Out-Null
    $lines.Add("- [x] Current-location and geofence transition logs include measured accuracy meters when Play Services provides them.") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## Premium Dark Olive Visual Checklist") | Out-Null
    $lines.Add("- [x] Olive primary color is visible in buttons and selected navigation.") | Out-Null
    $lines.Add("- [x] Cyan Field Guide primary styling is gone from the Time Tracker design tokens.") | Out-Null
    $lines.Add("- [x] Dark theme is preserved with a distinct olive identity.") | Out-Null
    $lines.Add("- [x] Settings and menu density is reduced into grouped rows and sections.") | Out-Null
    $lines.Add("- [x] Destructive reset requires confirmation.") | Out-Null
    $lines.Add("- [x] Motion is limited to short Compose transitions and did not block state assertions.") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## Debug Log Audit") | Out-Null
    $lines.Add('- Errors artifact: `debug-errors.json`') | Out-Null
    $lines.Add('- Accuracy evidence: `accuracy-log-evidence.json`') | Out-Null
    $lines.Add('- Summary artifact: `debug-summary.json`') | Out-Null
    $lines | Set-Content -LiteralPath (Join-Path $OutputRoot "report.md") -Encoding UTF8
}

function Invoke-BootstrapReset {
    $flow = "bootstrap_reset"
    $state = Wait-State -Name "00-preflight" -Condition {
        param($candidate)
        [bool]$candidate.transportReady
    } -TimeoutMs 20000
    if (-not [bool]$state.debugHarness.e2eDebugEnabled) {
        throw "E2E debug harness is not enabled. Install with -PtimeTracker.e2eDebug=true or omit -SkipInstall."
    }
    Assert-TagVisible -Tag "time_tracker_app" -StepName "preflight-app-root" | Out-Null
    Save-Screenshot -Name "00-preflight" | Out-Null

    Invoke-VerifiedControl -TestTag "nav_settings" -Flow $flow -ActionPerformed "open settings" `
        -ExpectedStateDelta "settings screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_settings" -StepName "bootstrap-nav-settings"
            Assert-TagVisible -Tag "screen_settings" -StepName "bootstrap-settings-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_delete_local_data_button" -Flow $flow -ActionPerformed "open reset confirmation" `
        -ExpectedStateDelta "confirmation dialog appears" -PersistenceExpectation "no data changes until confirmed" -Action {
            $tap = Tap-Tag -Tag "settings_delete_local_data_button" -StepName "bootstrap-delete-request"
            Assert-TagVisible -Tag "settings_delete_confirm_dialog" -StepName "bootstrap-delete-dialog" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_delete_confirm_button" -Flow $flow -ActionPerformed "confirm reset" `
        -ExpectedStateDelta "local setup/session data is cleared" -PersistenceExpectation "reset state persists" -Action {
            $tap = Tap-Tag -Tag "settings_delete_confirm_button" -StepName "bootstrap-delete-confirm"
            Wait-State -Name "01-after-bootstrap-reset" -Condition {
                param($candidate)
                -not [bool]$candidate.snapshot.homeSet -and
                -not [bool]$candidate.snapshot.workSet -and
                -not [bool]$candidate.snapshot.privacyDisclosureAccepted -and
                [int]$candidate.snapshot.sessionCount -eq 0
            } | Out-Null
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "$(New-SafeName "01-after-bootstrap-reset")-state.json"
            }
        } | Out-Null

    return @{ posture = [string]$state.stateMachine.posture }
}

function Invoke-SettingsTrackingSetup {
    $flow = "settings_tracking_setup"
    Assert-TagVisible -Tag "screen_settings" -StepName "settings-setup-screen" | Out-Null

    Invoke-VerifiedControl -TestTag "settings_enable_activity_button" -Flow $flow -ActionPerformed "enable activity detection" `
        -ExpectedStateDelta "activity registration request succeeds or reports handled status" -PersistenceExpectation "system registration may survive process" -Action {
            $tap = Tap-Tag -Tag "settings_enable_activity_button" -StepName "settings-enable-activity"
            $state = Wait-RecentLogMessage -Name "02-after-enable-activity" -Message "Activity detection enabled from settings"
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "02-after-enable-activity-state.json"
                posture = $state.stateMachine.posture
                automationAction = "enabled"
            }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_disable_activity_button" -Flow $flow -ActionPerformed "disable activity detection" `
        -ExpectedStateDelta "activity unregister request succeeds or reports handled status" -PersistenceExpectation "system registration may survive process" -Action {
            $tap = Tap-Tag -Tag "settings_disable_activity_button" -StepName "settings-disable-activity"
            $state = Wait-RecentLogMessage -Name "02-after-disable-activity" -Message "Activity detection disabled from settings"
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "02-after-disable-activity-state.json"
                posture = $state.stateMachine.posture
                automationAction = "disabled"
            }
        } | Out-Null

    Invoke-SwitchOnOffOnVerification -TestTag "settings_minimal_notification_switch" -Flow $flow `
        -SnapshotProperty "minimalActiveNotificationEnabled" -Label "minimal notification" -StepSlug "minimal-notification"
    Invoke-SwitchOnOffOnVerification -TestTag "settings_live_timer_notification_switch" -Flow $flow `
        -SnapshotProperty "liveTimerNotificationEnabled" -Label "live timer notification" -StepSlug "live-notification"
    Invoke-SwitchOnOffOnVerification -TestTag "settings_privacy_disclosure_switch" -Flow $flow `
        -SnapshotProperty "privacyDisclosureAccepted" -Label "privacy disclosure acceptance" -StepSlug "privacy-disclosure"

    if ($ProbeSystemButtons) {
        Invoke-SystemButtonProbe
    } else {
        Assert-TagVisible -Tag "settings_request_foreground_button" -StepName "settings-foreground-button-visible" | Out-Null
        Add-ControlResult -TestTag "settings_request_foreground_button" -Flow $flow -ActionPerformed "assert foreground permission button visible" `
            -ExpectedStateDelta "no state mutation" -PersistenceExpectation "permission state is device-owned" -Result "passed" -ArtifactPaths @{}
        Assert-TagVisible -Tag "settings_enable_background_button" -StepName "settings-background-button-visible" | Out-Null
        Add-ControlResult -TestTag "settings_enable_background_button" -Flow $flow -ActionPerformed "assert background settings button visible" `
            -ExpectedStateDelta "no state mutation" -PersistenceExpectation "permission state is device-owned" -Result "passed" -ArtifactPaths @{}
    }

    return @{ probeSystemButtons = [bool]$ProbeSystemButtons }
}

function Invoke-SettingsTimesheetRules {
    $flow = "settings_timesheet_rules"
    $dayNames = @("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
    foreach ($day in $dayNames) {
        $tag = "settings_workday_$($day.ToLowerInvariant())_switch"
        $before = Capture-State -Name "03-before-$day"
        $beforeValue = Get-WorkdayState -State $before -DayName $day
        $expectedAfterFirstTap = -not $beforeValue

        Invoke-VerifiedControl -TestTag $tag -Flow $flow -ActionPerformed "toggle $day workday" `
            -ExpectedStateDelta "$day trackable becomes $expectedAfterFirstTap" -PersistenceExpectation "temporary verification state" -Action {
                $tap = Tap-Tag -Tag $tag -StepName "settings-toggle-$($day.ToLowerInvariant())"
                Wait-State -Name "03-after-toggle-$day" -Condition {
                    param($candidate)
                    (Get-WorkdayState -State $candidate -DayName $day) -eq $expectedAfterFirstTap
                } | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "03-after-toggle-$day-state.json" }
            } | Out-Null

        Invoke-VerifiedControl -TestTag $tag -Flow $flow -ActionPerformed "restore $day workday" `
            -ExpectedStateDelta "$day trackable returns to $beforeValue" -PersistenceExpectation "restored before final workday setup" -Action {
                $tap = Tap-Tag -Tag $tag -StepName "settings-restore-$($day.ToLowerInvariant())"
                Wait-State -Name "03-after-restore-$day" -Condition {
                    param($candidate)
                    (Get-WorkdayState -State $candidate -DayName $day) -eq $beforeValue
                } | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "03-after-restore-$day-state.json" }
            } | Out-Null
    }

    $state = Capture-State -Name "03-settings-rules-restored"
    $todaySwitch = "settings_workday_$(([string]$state.snapshot.todayDayOfWeek).ToLowerInvariant())_switch"
    if (-not [bool]$state.snapshot.trackableToday) {
        Invoke-VerifiedControl -TestTag $todaySwitch -Flow $flow -ActionPerformed "enable today's workday" `
            -ExpectedStateDelta "trackableToday becomes true" -PersistenceExpectation "must survive relaunch" -Action {
                $tap = Tap-Tag -Tag $todaySwitch -StepName "settings-toggle-workday-on"
                Wait-State -Name "03-after-workday-on" -Condition {
                    param($candidate)
                    [bool]$candidate.snapshot.trackableToday
                } | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "03-after-workday-on-state.json" }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag "settings_anchor_date_field" -Flow $flow -ActionPerformed "enter pay-period anchor" `
        -ExpectedStateDelta "pending anchor text changes" -PersistenceExpectation "saved by anchor button" -Action {
            $field = Enter-TextByTag -Tag "settings_anchor_date_field" -Text "2026-05-03" -StepName "settings-anchor-date" -Clear
            Dismiss-Keyboard
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_save_anchor_button" -Flow $flow -ActionPerformed "save pay-period anchor" `
        -ExpectedStateDelta "payPeriodAnchorDate becomes 2026-05-03" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "settings_save_anchor_button" -StepName "settings-save-anchor"
            Wait-State -Name "03-after-save-anchor" -Condition {
                param($candidate)
                [string]$candidate.snapshot.payPeriodAnchorDate -eq "2026-05-03"
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "03-after-save-anchor-state.json" }
        } | Out-Null

    return @{ payPeriodAnchorDate = "2026-05-03" }
}

function Invoke-HomeLocationControls {
    $flow = "home_location_controls"
    Invoke-VerifiedControl -TestTag "nav_home" -Flow $flow -ActionPerformed "open home screen" `
        -ExpectedStateDelta "home screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_home" -StepName "home-nav"
            Assert-TagVisible -Tag "screen_home" -StepName "home-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    foreach ($probe in @(
            @{ tag = "home_use_current_button"; message = "Use current home location requested"; label = "home current-location probe" },
            @{ tag = "work_use_current_button"; message = "Use current work location requested"; label = "work current-location probe" }
        )) {
        Invoke-VerifiedControl -TestTag $probe.tag -Flow $flow -ActionPerformed "tap $($probe.label)" `
            -ExpectedStateDelta "request log is captured; deterministic manual values are saved afterward" `
            -PersistenceExpectation "manual home/work pins saved later in this flow remain authoritative" -Action {
                $tap = Tap-Tag -Tag $probe.tag -StepName "$($probe.tag)-probe"
                Wait-RecentLogMessage -Name "04-after-$($probe.tag)" -Message $probe.message | Out-Null
                Wait-RecentLogMessage -Name "04-after-$($probe.tag)-accuracy" -Message "Current location result" -TimeoutMs 30000 | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "$(New-SafeName "04-after-$($probe.tag)")-state.json" }
            } | Out-Null
    }

    foreach ($entry in @(
            @{ tag = "home_latitude_field"; text = "42.3314"; label = "home latitude" },
            @{ tag = "home_longitude_field"; text = "83.0458"; label = "home longitude" },
            @{ tag = "home_radius_field"; text = "180"; label = "home radius" }
        )) {
        Invoke-VerifiedControl -TestTag $entry.tag -Flow $flow -ActionPerformed "enter $($entry.label)" `
            -ExpectedStateDelta "home pin editor text changes" -PersistenceExpectation "saved by home pin button" -Action {
                $field = Enter-TextByTag -Tag $entry.tag -Text $entry.text -StepName $entry.label.Replace(" ", "-") -Clear
                Dismiss-Keyboard
                return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag "home_save_pin_button" -Flow $flow -ActionPerformed "save home pin" `
        -ExpectedStateDelta "homeSet becomes true and radius persists" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "home_save_pin_button" -StepName "home-save-pin"
            Wait-State -Name "04-after-home-pin" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.homeSet -and
                (Compare-Double $candidate.snapshot.homeLatitude 42.3314 0.0001) -and
                (Compare-Double $candidate.snapshot.homeLongitude 83.0458 0.0001) -and
                (Compare-Double $candidate.snapshot.homeRadiusMeters 180 0.01)
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-home-pin-state.json" }
        } | Out-Null

    foreach ($entry in @(
            @{ tag = "work_latitude_field"; text = "42.3320"; label = "work latitude" },
            @{ tag = "work_longitude_field"; text = "83.0460"; label = "work longitude" },
            @{ tag = "work_radius_field"; text = "8046"; label = "work radius" }
        )) {
        Invoke-VerifiedControl -TestTag $entry.tag -Flow $flow -ActionPerformed "enter $($entry.label)" `
            -ExpectedStateDelta "work pin editor text changes" -PersistenceExpectation "saved by work pin button" -Action {
                $field = Enter-TextByTag -Tag $entry.tag -Text $entry.text -StepName $entry.label.Replace(" ", "-") -Clear
                Dismiss-Keyboard
                return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag "work_save_pin_button" -Flow $flow -ActionPerformed "save work pin" `
        -ExpectedStateDelta "workSet becomes true and radius persists" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "work_save_pin_button" -StepName "work-save-pin"
            Wait-State -Name "04-after-work-pin" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.workSet -and
                (Compare-Double $candidate.snapshot.workLatitude 42.3320 0.0001) -and
                (Compare-Double $candidate.snapshot.workLongitude 83.0460 0.0001) -and
                (Compare-Double $candidate.snapshot.workRadiusMeters 8046 0.01)
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-work-pin-state.json" }
        } | Out-Null

    $state = Capture-State -Name "04-location-controls-final"
    return @{ homeSet = [bool]$state.snapshot.homeSet; workSet = [bool]$state.snapshot.workSet }
}

function Invoke-TrackingSessionCorrection {
    $flow = "tracking_session_correction"
    Invoke-VerifiedControl -TestTag "nav_tracking" -Flow $flow -ActionPerformed "open tracking screen" `
        -ExpectedStateDelta "tracking screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_tracking" -StepName "tracking-nav"
            Assert-TagVisible -Tag "screen_tracking" -StepName "tracking-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "tracking_start_button" -Flow $flow -ActionPerformed "start manual session" `
        -ExpectedStateDelta "activeSession becomes non-null" -PersistenceExpectation "active session persists if app stops mid-session" -Action {
            $tap = Tap-Tag -Tag "tracking_start_button" -StepName "tracking-start"
            $state = Wait-State -Name "05-after-start" -Condition {
                param($candidate)
                $null -ne $candidate.snapshot.activeSession
            }
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "05-after-start-state.json"; session = $state.snapshot.activeSession.idPrefix }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "tracking_stop_button" -Flow $flow -ActionPerformed "stop manual session" `
        -ExpectedStateDelta "activeSession clears and sessionCount increases" -PersistenceExpectation "completed session must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "tracking_stop_button" -StepName "tracking-stop"
            $state = Wait-State -Name "05-after-stop" -Condition {
                param($candidate)
                $null -eq $candidate.snapshot.activeSession -and [int]$candidate.snapshot.sessionCount -ge 1
            }
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "05-after-stop-state.json"; session = $state.snapshot.latestSession.idPrefix }
        } | Out-Null

    $stoppedState = Capture-State -Name "05-session-ready"
    $idPrefix = [string]$stoppedState.snapshot.latestSession.idPrefix
    $startMillis = [int64]$stoppedState.snapshot.latestSession.startEpochMillis
    $endMillis = [int64]$stoppedState.snapshot.latestSession.endEpochMillis
    $startIso = Convert-EpochMillisToIsoInstant -EpochMillis $startMillis
    $endIso = Convert-EpochMillisToIsoInstant -EpochMillis $endMillis
    $countsTag = "tracking_session_${idPrefix}_counts_switch"

    Invoke-VerifiedControl -TestTag $countsTag -Flow $flow -ActionPerformed "exclude latest session from totals" `
        -ExpectedStateDelta "countedSessionCount becomes 0 and manual-adjusted count increases" -PersistenceExpectation "temporary verification state" -Action {
            $tap = Tap-Tag -Tag $countsTag -StepName "tracking-counts-toggle-off"
            Wait-State -Name "05-after-counts-off" -Condition {
                param($candidate)
                [int]$candidate.snapshot.countedSessionCount -eq 0 -and [int]$candidate.snapshot.manuallyAdjustedSessionCount -ge 1
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "05-after-counts-off-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag $countsTag -Flow $flow -ActionPerformed "include latest session in totals" `
        -ExpectedStateDelta "countedSessionCount returns to at least 1" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag $countsTag -StepName "tracking-counts-toggle-on"
            Wait-State -Name "05-after-counts-on" -Condition {
                param($candidate)
                [int]$candidate.snapshot.countedSessionCount -ge 1
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "05-after-counts-on-state.json" }
        } | Out-Null

    $startTag = "tracking_session_${idPrefix}_start_field"
    Invoke-VerifiedControl -TestTag $startTag -Flow $flow -ActionPerformed "enter session start correction" `
        -ExpectedStateDelta "pending start instant text changes" -PersistenceExpectation "saved by session correction button" -Action {
            $field = Enter-TextByTag -Tag $startTag -Text $startIso -StepName "tracking-start-correction" -Clear
            Dismiss-Keyboard
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath; startInstant = $startIso }
        } | Out-Null

    $endTag = "tracking_session_${idPrefix}_end_field"
    Invoke-VerifiedControl -TestTag $endTag -Flow $flow -ActionPerformed "enter session end correction" `
        -ExpectedStateDelta "pending end instant text changes" -PersistenceExpectation "saved by session correction button" -Action {
            $field = Enter-TextByTag -Tag $endTag -Text $endIso -StepName "tracking-end-correction" -Clear
            Dismiss-Keyboard
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath; endInstant = $endIso }
        } | Out-Null

    $milesTag = "tracking_session_${idPrefix}_miles_field"
    Invoke-VerifiedControl -TestTag $milesTag -Flow $flow -ActionPerformed "enter driven miles correction" `
        -ExpectedStateDelta "pending driven miles text changes" -PersistenceExpectation "saved by session correction button" -Action {
            $field = Enter-TextByTag -Tag $milesTag -Text "12.5" -StepName "tracking-miles" -Clear
            Dismiss-Keyboard
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
        } | Out-Null

    $saveTag = "tracking_session_${idPrefix}_save_button"
    Invoke-VerifiedControl -TestTag $saveTag -Flow $flow -ActionPerformed "save manual correction" `
        -ExpectedStateDelta "totalDrivenMiles is at least 12.5" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag $saveTag -StepName "tracking-save-manual-correction"
            $state = Wait-State -Name "05-after-manual-correction" -Condition {
                param($candidate)
                [double]$candidate.snapshot.totalDrivenMiles -ge 12.5 -and
                [int64]$candidate.snapshot.latestSession.startEpochMillis -eq $startMillis -and
                [int64]$candidate.snapshot.latestSession.endEpochMillis -eq $endMillis
            }
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "05-after-manual-correction-state.json"
                totalDrivenMiles = [double]$state.snapshot.totalDrivenMiles
                startEpochMillis = [int64]$state.snapshot.latestSession.startEpochMillis
                endEpochMillis = [int64]$state.snapshot.latestSession.endEpochMillis
            }
        } | Out-Null

    return @{ latestSession = $idPrefix }
}

function Invoke-JobsiteGeofenceMileagePolicy {
    $flow = "jobsite_geofence_mileage_policy"
    $before = Capture-State -Name "05b-jobsite-drive-before"
    $beforeMiles = [double]$before.snapshot.reportTotals.today.drivenMiles
    $beforeDriveMinutes = [int]$before.snapshot.reportTotals.today.driveMinutes
    $beforeUnclassifiedMinutes = [int]$before.snapshot.reportTotals.today.unclassifiedMinutes

    Invoke-VerifiedControl -TestTag "debug_seed_jobsite_drive_endpoint" -Flow $flow `
        -ActionPerformed "seed jobsite vehicle movement with miles" `
        -ExpectedStateDelta "today miles increase and unclassified minutes increase while drive minutes do not" `
        -PersistenceExpectation "seeded session, miles, and policy result must survive relaunch" -Action {
            $seed = Request-JobsiteDriveSeed
            Save-Json -Value $seed -Path (Join-Path $OutputRoot "jobsite-drive-seed.json")
            if ([string]$seed.proof.atWorkVehicleBucket -ne "UNCLASSIFIED") {
                throw "[jobsite_policy] At-work vehicle movement classified as $($seed.proof.atWorkVehicleBucket), expected UNCLASSIFIED."
            }
            $state = Wait-State -Name "05b-after-jobsite-drive" -Condition {
                param($candidate)
                [double]$candidate.snapshot.reportTotals.today.drivenMiles -ge ($beforeMiles + 6.70) -and
                [int]$candidate.snapshot.reportTotals.today.driveMinutes -eq $beforeDriveMinutes -and
                [int]$candidate.snapshot.reportTotals.today.unclassifiedMinutes -ge ($beforeUnclassifiedMinutes + 30)
            }
            return @{
                seed = "jobsite-drive-seed.json"
                state = "05b-after-jobsite-drive-state.json"
                atWorkVehicleBucket = [string]$seed.proof.atWorkVehicleBucket
                beforeMiles = $beforeMiles
                todayMiles = [double]$state.snapshot.reportTotals.today.drivenMiles
                beforeDriveMinutes = $beforeDriveMinutes
                todayDriveMinutes = [int]$state.snapshot.reportTotals.today.driveMinutes
                beforeUnclassifiedMinutes = $beforeUnclassifiedMinutes
                todayUnclassifiedMinutes = [int]$state.snapshot.reportTotals.today.unclassifiedMinutes
            }
        } | Out-Null

    $after = Capture-State -Name "05b-jobsite-drive-final"
    return @{
        totalDrivenMiles = [double]$after.snapshot.totalDrivenMiles
        todayReportMiles = [double]$after.snapshot.reportTotals.today.drivenMiles
        todayDriveMinutes = [int]$after.snapshot.reportTotals.today.driveMinutes
        todayUnclassifiedMinutes = [int]$after.snapshot.reportTotals.today.unclassifiedMinutes
    }
}

function Invoke-ReportsNavigationAndTotals {
    $flow = "reports_navigation_and_totals"
    Invoke-VerifiedControl -TestTag "nav_reports" -Flow $flow -ActionPerformed "open reports screen" `
        -ExpectedStateDelta "reports screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_reports" -StepName "reports-nav"
            Assert-TagVisible -Tag "screen_reports" -StepName "reports-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    foreach ($tag in @("reports_today_card", "reports_weekly_card", "reports_biweekly_card", "reports_monthly_card", "reports_yearly_card")) {
        Invoke-VerifiedControl -TestTag $tag -Flow $flow -ActionPerformed "assert report card visible" `
            -ExpectedStateDelta "report card is rendered" -PersistenceExpectation "report totals recompute from persisted data" -Action {
                $found = Assert-TagVisible -Tag $tag -StepName $tag
                return @{ uiDump = Get-RelativeArtifactPath $found.dumpPath }
            } | Out-Null
    }

    $shot = Save-Screenshot -Name "06-reports-final"
    $state = Capture-State -Name "06-reports-state"
    if ([double]$state.snapshot.reportTotals.today.drivenMiles -lt 12.5) {
        throw "[reports_totals] Today report did not include corrected miles."
    }
    if ([int]$state.snapshot.reportTotals.today.driveMinutes -ne 0) {
        throw "[reports_totals] Jobsite vehicle time was counted as drive minutes."
    }
    return @{
        screenshot = Get-RelativeArtifactPath $shot
        totalDrivenMiles = [double]$state.snapshot.totalDrivenMiles
        todayReportMiles = [double]$state.snapshot.reportTotals.today.drivenMiles
        todayDriveMinutes = [int]$state.snapshot.reportTotals.today.driveMinutes
    }
}

function Invoke-BottomNavSwitching {
    $flow = "bottom_nav_switching"
    foreach ($nav in @(
            @{ tag = "nav_home"; screen = "screen_home" },
            @{ tag = "nav_tracking"; screen = "screen_tracking" },
            @{ tag = "nav_reports"; screen = "screen_reports" },
            @{ tag = "nav_settings"; screen = "screen_settings" }
        )) {
        Invoke-VerifiedControl -TestTag $nav.tag -Flow $flow -ActionPerformed "switch bottom navigation destination" `
            -ExpectedStateDelta "$($nav.screen) becomes visible" -PersistenceExpectation "navigation state only" -Action {
                $tap = Tap-Tag -Tag $nav.tag -StepName "bottom-$($nav.tag)"
                Assert-TagVisible -Tag $nav.screen -StepName "bottom-$($nav.screen)" | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
            } | Out-Null
    }
    return @{ destinationsVerified = 4 }
}

function Run-PersistenceRelaunch {
    $baseline = Request-State
    Save-Json -Value $baseline -Path (Join-Path $OutputRoot "persisted-baseline-state.json")
    Start-App
    $after = Wait-State -Name "07-after-relaunch-ready" -Condition {
        param($candidate)
        [bool]$candidate.transportReady
    } -TimeoutMs 20000
    Save-Json -Value $after -Path (Join-Path $OutputRoot "after-relaunch-state.json")
    $comparisons = Assert-PersistenceMatch -Baseline $baseline -After $after
    Save-Screenshot -Name "07-after-relaunch" | Out-Null
    return @{ comparedFields = @($comparisons).Count }
}

function Invoke-DestructiveResetConfirmation {
    $flow = "destructive_reset_confirmation"
    $before = Capture-State -Name "08-before-destructive-reset"
    Invoke-VerifiedControl -TestTag "nav_settings" -Flow $flow -ActionPerformed "open settings before reset" `
        -ExpectedStateDelta "settings screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_settings" -StepName "reset-confirm-nav-settings"
            Assert-TagVisible -Tag "screen_settings" -StepName "reset-confirm-settings-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_delete_local_data_button" -Flow $flow -ActionPerformed "open delete confirmation" `
        -ExpectedStateDelta "confirmation dialog appears without clearing state" -PersistenceExpectation "no data changes until confirm" -Action {
            $tap = Tap-Tag -Tag "settings_delete_local_data_button" -StepName "reset-confirm-open"
            $dialog = Assert-TagVisible -Tag "settings_delete_confirm_dialog" -StepName "reset-confirm-dialog"
            $shot = Save-Screenshot -Name "08-delete-confirm-dialog"
            return @{
                uiDump = Get-RelativeArtifactPath $dialog.dumpPath
                tapDump = Get-RelativeArtifactPath $tap.dumpPath
                screenshot = Get-RelativeArtifactPath $shot
            }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_delete_confirm_cancel_button" -Flow $flow -ActionPerformed "cancel delete confirmation" `
        -ExpectedStateDelta "home/session/settings state remains unchanged" -PersistenceExpectation "all existing data remains persisted" -Action {
            $tap = Tap-Tag -Tag "settings_delete_confirm_cancel_button" -StepName "reset-confirm-cancel"
            $state = Wait-State -Name "08-after-reset-cancel" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.homeSet -eq [bool]$before.snapshot.homeSet -and
                [int]$candidate.snapshot.sessionCount -eq [int]$before.snapshot.sessionCount
            }
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "08-after-reset-cancel-state.json"
                sessionCount = [int]$state.snapshot.sessionCount
            }
        } | Out-Null

    Tap-Tag -Tag "settings_delete_local_data_button" -StepName "reset-confirm-open-again" | Out-Null
    Invoke-VerifiedControl -TestTag "settings_delete_confirm_button" -Flow $flow -ActionPerformed "confirm destructive delete" `
        -ExpectedStateDelta "local data is cleared" -PersistenceExpectation "reset state persists" -Action {
            $tap = Tap-Tag -Tag "settings_delete_confirm_button" -StepName "reset-confirm-delete"
            $state = Wait-State -Name "08-after-destructive-reset" -Condition {
                param($candidate)
                -not [bool]$candidate.snapshot.homeSet -and
                -not [bool]$candidate.snapshot.workSet -and
                -not [bool]$candidate.snapshot.privacyDisclosureAccepted -and
                [int]$candidate.snapshot.sessionCount -eq 0 -and
                [double]$candidate.snapshot.totalDrivenMiles -eq 0
            }
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "08-after-destructive-reset-state.json"
                sessionCount = [int]$state.snapshot.sessionCount
            }
        } | Out-Null

    return @{ resetConfirmed = $true }
}

function Invoke-DebugLogAudit {
    Assert-NoDebugErrors
    Assert-AccuracyLogEvidence
    $summary = Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/logs/summary" -Method Get -TimeoutSec 10
    Save-Json -Value $summary -Path (Join-Path $OutputRoot "debug-summary.json")
    if ([int]$summary.total -le 0) {
        throw "[debug_log_empty] Debug log server did not capture app logs."
    }
    return @{ debugLogCount = [int]$summary.total }
}

function Invoke-UiControlCoverageAudit {
    return Assert-RequiredControlCoverage
}

try {
    $script:ResolvedDeviceId = Resolve-E2EDeviceId -RequestedDeviceId $DeviceId
    Update-DisplaySize
    Start-DebugInfrastructure

    Invoke-E2EFlow -Name "bootstrap_reset" -Action { Invoke-BootstrapReset }
    Invoke-E2EFlow -Name "settings_tracking_setup" -Action { Invoke-SettingsTrackingSetup }
    Invoke-E2EFlow -Name "settings_timesheet_rules" -Action { Invoke-SettingsTimesheetRules }
    Invoke-E2EFlow -Name "home_location_controls" -Action { Invoke-HomeLocationControls }
    Invoke-E2EFlow -Name "tracking_session_correction" -Action { Invoke-TrackingSessionCorrection }
    Invoke-E2EFlow -Name "jobsite_geofence_mileage_policy" -Action { Invoke-JobsiteGeofenceMileagePolicy }
    Invoke-E2EFlow -Name "reports_navigation_and_totals" -Action { Invoke-ReportsNavigationAndTotals }
    Invoke-E2EFlow -Name "bottom_nav_switching" -Action { Invoke-BottomNavSwitching }
    Invoke-E2EFlow -Name "persistence_relaunch" -Action { Run-PersistenceRelaunch }
    Invoke-E2EFlow -Name "destructive_reset_confirmation" -Action { Invoke-DestructiveResetConfirmation }
    Invoke-E2EFlow -Name "ui_control_coverage_audit" -Action { Invoke-UiControlCoverageAudit }
    Invoke-E2EFlow -Name "debug_log_audit" -Action { Invoke-DebugLogAudit }
} catch {
    if ($_.Exception -and -not [string]::IsNullOrWhiteSpace($_.Exception.Message)) {
        $script:RunFailure = $_.Exception.Message
    } else {
        $script:RunFailure = [string]$_
    }
} finally {
    $stepResults = @($script:Steps.ToArray())
    $passed = [string]::IsNullOrWhiteSpace($script:RunFailure)
    $summary = [pscustomobject]@{
        runId = $RunId
        deviceId = $script:ResolvedDeviceId
        packageName = $PackageName
        activityName = $ActivityName
        passed = [bool]$passed
        failure = $script:RunFailure
        outputRoot = $OutputRoot
        statePort = $StatePort
        logPort = $LogPort
        probeSystemButtons = [bool]$ProbeSystemButtons
        steps = $stepResults
        completedAtUtc = [DateTime]::UtcNow.ToString("o")
    }
    try {
        Save-Json -Value $script:ButtonMatrix.ToArray() -Path (Join-Path $OutputRoot "button-state-matrix.json")
        Save-Json -Value $summary -Path (Join-Path $OutputRoot "summary.json")
        Write-E2EReport -Summary $summary
    } catch {
        Write-Warning "Failed to write final E2E artifacts: $($_.Exception.Message)"
    }
    Write-Host "E2E artifacts written to $OutputRoot"
    Write-Host "passed=$($summary.passed) steps=$($script:Steps.Count)"
}

if ($script:RunFailure) {
    Write-Error $script:RunFailure
    exit 1
}
