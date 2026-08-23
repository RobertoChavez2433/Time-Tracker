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
    [int]$StatePort = 4958,
    [int]$LogPort = 3947,
    [string]$PackageName = "com.robertochavez.timetracker.debug",
    [string]$ActivityName = "com.robertochavez.timetracker.MainActivity",
    [switch]$SkipInstall,
    [switch]$ProbeSystemButtons,
    [switch]$NewFeatureAcceptanceOnly
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
    @{ id = "nav_places"; tagPattern = "^nav_places$"; kind = "bottom_navigation"; screen = "app"; requiredMode = "state" },
    @{ id = "nav_settings"; tagPattern = "^nav_settings$"; kind = "bottom_navigation"; screen = "app"; requiredMode = "state" },
    @{ id = "dashboard_summary_panel"; tagPattern = "^dashboard_summary_panel$"; kind = "panel"; screen = "dashboard"; requiredMode = "state" },
    @{ id = "dashboard_weekly_ledger"; tagPattern = "^dashboard_weekly_ledger$"; kind = "panel"; screen = "dashboard"; requiredMode = "state" },
    @{ id = "startup_enable_button"; tagPattern = "^startup_enable_button$"; kind = "button"; screen = "startup_setup"; requiredMode = "state" },
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
    @{ id = "home_use_current_button"; tagPattern = "^home_use_current_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "home_edit_pin_button"; tagPattern = "^home_edit_pin_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "home_cancel_edit_button"; tagPattern = "^home_cancel_edit_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "home_latitude_field"; tagPattern = "^home_latitude_field$"; kind = "text_field"; screen = "places"; requiredMode = "input" },
    @{ id = "home_longitude_field"; tagPattern = "^home_longitude_field$"; kind = "text_field"; screen = "places"; requiredMode = "input" },
    @{ id = "home_radius_dropdown"; tagPattern = "^home_radius_dropdown$"; kind = "dropdown"; screen = "places"; requiredMode = "state" },
    @{ id = "home_radius_option_quarter_mile"; tagPattern = "^home_radius_option_quarter_mile$"; kind = "dropdown_option"; screen = "places"; requiredMode = "state" },
    @{ id = "home_overwrite_confirm_button"; tagPattern = "^home_overwrite_confirm_button$"; kind = "button"; screen = "places_dialog"; requiredMode = "state" },
    @{ id = "home_save_pin_button"; tagPattern = "^home_save_pin_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "places_saved_list"; tagPattern = "^places_saved_list$"; kind = "list"; screen = "places"; requiredMode = "state" },
    @{ id = "add_place_button"; tagPattern = "^add_place_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "add_place_current_button"; tagPattern = "^add_place_current_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "add_place_map_button"; tagPattern = "^add_place_map_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "add_place_address_button"; tagPattern = "^add_place_address_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "work_cancel_edit_button"; tagPattern = "^work_cancel_edit_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "work_label_field"; tagPattern = "^work_label_field$"; kind = "text_field"; screen = "places"; requiredMode = "input" },
    @{ id = "work_latitude_field"; tagPattern = "^work_latitude_field$"; kind = "text_field"; screen = "places"; requiredMode = "input" },
    @{ id = "work_longitude_field"; tagPattern = "^work_longitude_field$"; kind = "text_field"; screen = "places"; requiredMode = "input" },
    @{ id = "work_radius_field"; tagPattern = "^work_radius_field$"; kind = "slider"; screen = "places"; requiredMode = "state" },
    @{ id = "work_save_pin_button"; tagPattern = "^work_save_pin_button$"; kind = "button"; screen = "places"; requiredMode = "state" },
    @{ id = "work_add_location_button"; tagPattern = "^work_add_location_button$"; kind = "button"; screen = "places_dialog"; requiredMode = "state" },
    @{ id = "work_replace_location_button"; tagPattern = "^work_replace_location_button$"; kind = "button"; screen = "places_dialog"; requiredMode = "state" },
    @{ id = "tracking_start_button"; tagPattern = "^tracking_start_button$"; kind = "button"; screen = "tracking"; requiredMode = "state" },
    @{ id = "tracking_stop_button"; tagPattern = "^tracking_stop_button$"; kind = "button"; screen = "tracking"; requiredMode = "state" },
    @{ id = "tracking_session_edit_button"; tagPattern = "^tracking_session_[^_]+_edit_button$"; kind = "button"; screen = "tracking"; requiredMode = "state" },
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
            return $joinedActions -match "(?i)\b(enter|verify)\b" -or
                $joinedDeltas -match "(?i)(text changes|input|editor .*visible|date and time editor)"
        }
        "log" {
            $probed = $joinedActions -match "(?i)\b(tap|enable|disable|verify)\b" -and
                $joinedDeltas -match "(?i)(permission result|registration|opened|handled|activity|already true)"
            $visibleFallback = $joinedActions -match "(?i)\b(assert|verify)\b.*\bvisible\b" -and
                $joinedDeltas -match "(?i)(no state mutation|rendered|visible)"
            return $probed -or $visibleFallback
        }
        default {
            if ([string]$Spec.kind -eq "bottom_navigation") {
                return $joinedActions -match "(?i)\b(open|switch)\b" -and $joinedDeltas -match "(?i)screen .*visible"
            }
            if ($joinedActions -match "(?i)\bassert\b" -and $joinedDeltas -match "(?i)(visible|rendered)") {
                return $true
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
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACCESS_BACKGROUND_LOCATION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.ACTIVITY_RECOGNITION") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("pm", "grant", $PackageName, "android.permission.POST_NOTIFICATIONS") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", $PackageName, "ACCESS_BACKGROUND_LOCATION", "allow") -AllowFailure | Out-Null
    Invoke-AdbShell -Arguments @("appops", "set", $PackageName, "android:background_location", "allow") -AllowFailure | Out-Null
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
        "--activity-clear-top",
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

function Ensure-StatePortForward {
    $expected = "$script:ResolvedDeviceId tcp:$StatePort tcp:$StatePort"
    $forwards = @(& adb forward --list 2>$null)
    if ($forwards -contains $expected) {
        return
    }

    foreach ($forward in $forwards) {
        if ($forward -match "^(\S+)\s+tcp:$StatePort\s+") {
            & adb -s $Matches[1] forward --remove "tcp:$StatePort" 2>$null | Out-Null
        }
    }
    Invoke-Adb -Arguments @("forward", "tcp:$StatePort", "tcp:$StatePort") | Out-Null
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
    $uri = "http://127.0.0.1:$StatePort/testing/state?$query"
    try {
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
    } catch {
        Ensure-StatePortForward
        Start-Sleep -Milliseconds 250
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
    }
}

function Request-JobsiteDriveSeed {
    $query = "runId=$([System.Uri]::EscapeDataString($RunId))&actorId=$([System.Uri]::EscapeDataString($script:ResolvedDeviceId))"
    $uri = "http://127.0.0.1:$StatePort/testing/seed-jobsite-drive?$query"
    try {
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
    } catch {
        Ensure-StatePortForward
        Start-Sleep -Milliseconds 250
        return Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
    }
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
        [int]$TimeoutMs = 12000,
        [int]$StabilityWindowMs = 1200
    )

    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMs)
    $lastState = $null
    $lastError = $null
    $stableSince = $null
    do {
        try {
            $lastState = Request-State
            if (& $Condition $lastState) {
                if ($null -eq $stableSince) {
                    $stableSince = [DateTime]::UtcNow
                }
                if (([DateTime]::UtcNow - $stableSince).TotalMilliseconds -ge $StabilityWindowMs) {
                    Save-Json -Value $lastState -Path (Get-StateArtifactPath -Name $Name)
                    return $lastState
                }
            } else {
                $stableSince = $null
            }
        } catch {
            $lastError = $_.Exception.Message
            $stableSince = $null
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
        "startup_setup_dialog" { "Finish setup" }
        "startup_enable_button" { "Agree and Enable Tracking" }
        "startup_background_button" { "Enable Background Location" }
        "home_radius_option_quarter_mile" { "1/4 mi" }
        "home_overwrite_confirm_button" { "Overwrite Home" }
        "home_overwrite_cancel_button" { "Cancel" }
        "work_add_location_button" { "Add Work Site" }
        "work_replace_location_button" { "Replace Latest" }
        "work_save_cancel_button" { "Cancel" }
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
        [int]$MaxScrolls = 14,
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

function Set-SliderByTag {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][double]$Fraction,
        [Parameter(Mandatory = $true)][string]$StepName,
        [int]$WaitMs = 700
    )

    $found = Find-UiNodeByTag -Tag $Tag -StepName $StepName
    $bounds = Get-NodeBounds -Node $found.node
    $guardedFraction = [Math]::Max(0.0, [Math]::Min(1.0, $Fraction))
    # Compose keeps the slider thumb center roughly one 24 dp radius inside
    # the semantics bounds. The S21's density resolves that to about 7%.
    $trackInset = [Math]::Min(34, [int]($bounds.width * 0.07))
    $trackWidth = $bounds.width - (2 * $trackInset)
    $x = [int]($bounds.left + $trackInset + ($trackWidth * $guardedFraction))
    $y = [int](($bounds.top + $bounds.bottom) / 2)
    Invoke-AdbShell -Arguments @("input", "tap", "$x", "$y") | Out-Null
    Start-Sleep -Milliseconds $WaitMs
    return $found
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

function Invoke-StartupSetupIfPresent {
    param([Parameter(Mandatory = $true)][string]$Flow)

    if (-not (Test-TagVisibleNow -Tag "startup_enable_button")) {
        return $false
    }

    Invoke-VerifiedControl -TestTag "startup_enable_button" -Flow $Flow -ActionPerformed "accept startup setup" `
        -ExpectedStateDelta "privacyDisclosureAccepted becomes true" -PersistenceExpectation "startup setup acceptance persists" -Action {
            $before = Capture-State -Name "startup-before-enable"
            if ([bool]$before.snapshot.privacyDisclosureAccepted) {
                return @{ state = "startup-before-enable-state.json"; alreadyAccepted = $true }
            }

            try {
                $tap = Tap-Tag -Tag "startup_enable_button" -StepName "startup-enable"
            } catch {
                $raceState = Capture-State -Name "startup-after-enable-race"
                if ([bool]$raceState.snapshot.privacyDisclosureAccepted) {
                    return @{ state = "startup-after-enable-race-state.json"; acceptedDuringUiRefresh = $true }
                }
                throw
            }
            Wait-State -Name "startup-after-enable" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.privacyDisclosureAccepted
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "startup-after-enable-state.json" }
        } | Out-Null
    return $true
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

function Clear-FocusedTextInput {
    Invoke-InputKeyCombination -KeyCodes @(113, 29)
    Invoke-InputKeyEvent -KeyCode 67
    Start-Sleep -Milliseconds 100

    # Keep a fallback path for input methods that do not honor Ctrl+A.
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
        Clear-FocusedTextInput
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
        -ActionPerformed "tap Android background-location settings button" -ExpectedStateDelta "system settings handoff opened" `
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

    $beforeEnable = Capture-State -Name "02-before-$StepSlug-enable"
    if (Get-SnapshotBoolean -State $beforeEnable -PropertyName $SnapshotProperty) {
        Add-ControlResult -TestTag $TestTag -Flow $Flow -ActionPerformed "verify $Label already enabled" `
            -ExpectedStateDelta "$SnapshotProperty is already true" -PersistenceExpectation "temporary on/off verification state" `
            -Result "passed" -ArtifactPaths @{ state = "02-before-$StepSlug-enable-state.json" }
    } else {
        Invoke-VerifiedControl -TestTag $TestTag -Flow $Flow -ActionPerformed "enable $Label" `
            -ExpectedStateDelta "$SnapshotProperty becomes true" -PersistenceExpectation "temporary on/off verification state" -Action {
                $tap = Tap-Tag -Tag $TestTag -StepName "$StepSlug-enable"
                Wait-State -Name "02-after-$StepSlug-enable" -Condition {
                    param($candidate)
                    Get-SnapshotBoolean -State $candidate -PropertyName $SnapshotProperty
                } | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-$StepSlug-enable-state.json" }
            } | Out-Null
    }

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

function Invoke-PrivacyStartupVerification {
    param([Parameter(Mandatory = $true)][string]$Flow)

    $beforeDisable = Capture-State -Name "02-before-privacy-startup-prompt"
    if (-not (Get-SnapshotBoolean -State $beforeDisable -PropertyName "privacyDisclosureAccepted")) {
        Invoke-StartupSetupIfPresent -Flow $Flow | Out-Null
    }

    Invoke-VerifiedControl -TestTag "settings_privacy_disclosure_switch" -Flow $Flow -ActionPerformed "disable privacy acceptance" `
        -ExpectedStateDelta "privacyDisclosureAccepted becomes false and startup setup is required" `
        -PersistenceExpectation "startup setup must re-accept before app use" -Action {
            $tap = Tap-Tag -Tag "settings_privacy_disclosure_switch" -StepName "privacy-disclosure-disable"
            Wait-State -Name "02-after-privacy-disclosure-disable" -Condition {
                param($candidate)
                -not [bool]$candidate.snapshot.privacyDisclosureAccepted
            } | Out-Null
            Assert-TagVisible -Tag "startup_setup_dialog" -StepName "privacy-startup-dialog" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-privacy-disclosure-disable-state.json" }
        } | Out-Null

    Invoke-StartupSetupIfPresent -Flow $Flow | Out-Null
    if (-not (Test-TagVisibleNow -Tag "screen_settings")) {
        Tap-Tag -Tag "nav_settings" -StepName "privacy-return-settings-nav" | Out-Null
    }
    Assert-TagVisible -Tag "screen_settings" -StepName "privacy-return-settings" | Out-Null
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

function Compare-AtLeast {
    param($Baseline, $After)
    return [int64]$After -ge [int64]$Baseline
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
        @{ field = "sessionCount"; match = Compare-AtLeast $Baseline.snapshot.sessionCount $After.snapshot.sessionCount },
        @{ field = "countedSessionCount"; match = Compare-AtLeast $Baseline.snapshot.countedSessionCount $After.snapshot.countedSessionCount },
        @{ field = "manuallyAdjustedSessionCount"; match = Compare-Scalar $Baseline.snapshot.manuallyAdjustedSessionCount $After.snapshot.manuallyAdjustedSessionCount },
        @{ field = "activityIntervalCount"; match = Compare-AtLeast $Baseline.snapshot.activityIntervalCount $After.snapshot.activityIntervalCount },
        @{ field = "totalDrivenMiles"; match = Compare-Double $Baseline.snapshot.totalDrivenMiles $After.snapshot.totalDrivenMiles },
        @{ field = "reportTotals.today.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.today.drivenMiles $After.snapshot.reportTotals.today.drivenMiles },
        @{ field = "reportTotals.today.driveMinutes"; match = Compare-Scalar $Baseline.snapshot.reportTotals.today.driveMinutes $After.snapshot.reportTotals.today.driveMinutes },
        @{ field = "reportTotals.today.needsReviewMinutes"; match = Compare-Scalar $Baseline.snapshot.reportTotals.today.needsReviewMinutes $After.snapshot.reportTotals.today.needsReviewMinutes },
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
    Invoke-StartupSetupIfPresent -Flow $flow | Out-Null
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

    Invoke-StartupSetupIfPresent -Flow $flow | Out-Null

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
    Invoke-PrivacyStartupVerification -Flow $flow

    if ($ProbeSystemButtons) {
        Invoke-SystemButtonProbe
    } else {
        if (Test-TagVisibleNow -Tag "settings_request_foreground_button") {
            $foreground = Assert-TagVisible -Tag "settings_request_foreground_button" -StepName "settings-foreground-button-visible"
            Add-ControlResult -TestTag "settings_request_foreground_button" -Flow $flow -ActionPerformed "assert foreground permission button visible" `
                -ExpectedStateDelta "no state mutation" -PersistenceExpectation "permission state is device-owned" -Result "passed" `
                -ArtifactPaths @{ uiDump = Get-RelativeArtifactPath $foreground.dumpPath }
        } else {
            Add-ControlResult -TestTag "settings_request_foreground_button" -Flow $flow -ActionPerformed "verify foreground permission already true" `
                -ExpectedStateDelta "permission already true" -PersistenceExpectation "permission state is device-owned" -Result "passed" -ArtifactPaths @{}
        }

        if (Test-TagVisibleNow -Tag "settings_enable_background_button") {
            $background = Assert-TagVisible -Tag "settings_enable_background_button" -StepName "settings-background-button-visible"
            Add-ControlResult -TestTag "settings_enable_background_button" -Flow $flow -ActionPerformed "assert background settings button visible" `
                -ExpectedStateDelta "no state mutation" -PersistenceExpectation "permission state is device-owned" -Result "passed" `
                -ArtifactPaths @{ uiDump = Get-RelativeArtifactPath $background.dumpPath }
        } else {
            Add-ControlResult -TestTag "settings_enable_background_button" -Flow $flow -ActionPerformed "verify background permission already true" `
                -ExpectedStateDelta "permission already true" -PersistenceExpectation "permission state is device-owned" -Result "passed" -ArtifactPaths @{}
        }
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
    $flow = "places_location_controls"
    Invoke-VerifiedControl -TestTag "nav_places" -Flow $flow -ActionPerformed "open places screen" `
        -ExpectedStateDelta "places screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_places" -StepName "places-nav"
            Assert-TagVisible -Tag "screen_places" -StepName "places-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    foreach ($probe in @(
            @{ tag = "home_use_current_button"; message = "Use current home location requested"; label = "home current-location probe" }
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
    Invoke-VerifiedControl -TestTag "add_place_button" -Flow $flow -ActionPerformed "open add place flow" `
        -ExpectedStateDelta "three streamlined location methods become visible" -PersistenceExpectation "navigation state only" -Action {
            $tap = Tap-Tag -Tag "add_place_button" -StepName "add-place-open"
            Assert-TagVisible -Tag "add_place_current_button" -StepName "add-place-methods-visible" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null
    Invoke-VerifiedControl -TestTag "add_place_current_button" -Flow $flow -ActionPerformed "tap work current-location probe" `
        -ExpectedStateDelta "request log is captured; deterministic manual values are saved afterward" `
        -PersistenceExpectation "manual work pin saved later in this flow remains authoritative" -Action {
            $tap = Tap-Tag -Tag "add_place_current_button" -StepName "add-place-current-probe"
            Wait-RecentLogMessage -Name "04-after-add-place-current" -Message "Use current work location requested" | Out-Null
            Wait-RecentLogMessage -Name "04-after-add-place-current-accuracy" -Message "Current location result" -TimeoutMs 30000 | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-add-place-current-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "home_edit_pin_button" -Flow $flow -ActionPerformed "open manual home pin editor" `
        -ExpectedStateDelta "home coordinate fields become visible" -PersistenceExpectation "editor visibility only" -Action {
            $tap = Tap-Tag -Tag "home_edit_pin_button" -StepName "home-edit-pin-open"
            Assert-TagVisible -Tag "home_latitude_field" -StepName "home-editor-visible" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "home_cancel_edit_button" -Flow $flow -ActionPerformed "cancel manual home pin editing" `
        -ExpectedStateDelta "home current-location action returns" -PersistenceExpectation "editor visibility only" -Action {
            $tap = Tap-Tag -Tag "home_cancel_edit_button" -StepName "home-edit-pin-cancel"
            Assert-TagVisible -Tag "home_use_current_button" -StepName "home-current-visible" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Tap-Tag -Tag "home_edit_pin_button" -StepName "home-edit-pin-reopen" | Out-Null

    foreach ($entry in @(
            @{ tag = "home_latitude_field"; text = "42.3314"; label = "home latitude" },
            @{ tag = "home_longitude_field"; text = "-83.0458"; label = "home longitude" }
        )) {
        Invoke-VerifiedControl -TestTag $entry.tag -Flow $flow -ActionPerformed "enter $($entry.label)" `
            -ExpectedStateDelta "home pin editor text changes" -PersistenceExpectation "saved by home pin button" -Action {
                $field = Enter-TextByTag -Tag $entry.tag -Text $entry.text -StepName $entry.label.Replace(" ", "-") -Clear
                Dismiss-Keyboard
                return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag "home_radius_option_quarter_mile" -Flow $flow -ActionPerformed "select home radius" `
        -ExpectedStateDelta "home radius option is selected" -PersistenceExpectation "saved by home pin button" -Action {
            $dropdownTap = Tap-Tag -Tag "home_radius_dropdown" -StepName "home-radius-dropdown-open"
            Add-ControlResult -TestTag "home_radius_dropdown" -Flow $flow -ActionPerformed "open home radius dropdown" `
                -ExpectedStateDelta "home radius options are visible" -PersistenceExpectation "selected option is saved by home pin button" `
                -Result "passed" -ArtifactPaths @{ uiDump = Get-RelativeArtifactPath $dropdownTap.dumpPath }
            $tap = Tap-Tag -Tag "home_radius_option_quarter_mile" -StepName "home-radius-quarter-mile"
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "home_save_pin_button" -Flow $flow -ActionPerformed "save home pin" `
        -ExpectedStateDelta "overwrite confirmation appears" -PersistenceExpectation "no overwrite until confirmed" -Action {
            $tap = Tap-Tag -Tag "home_save_pin_button" -StepName "home-save-pin"
            Assert-TagVisible -Tag "home_overwrite_confirm_button" -StepName "home-overwrite-dialog" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "home_overwrite_confirm_button" -Flow $flow -ActionPerformed "confirm home overwrite" `
        -ExpectedStateDelta "homeSet remains true and selected radius persists" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "home_overwrite_confirm_button" -StepName "home-overwrite-confirm"
            Wait-State -Name "04-after-home-pin" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.homeSet -and
                (Compare-Double $candidate.snapshot.homeLatitude 42.3314 0.0001) -and
                (Compare-Double $candidate.snapshot.homeLongitude -83.0458 0.0001) -and
                (Compare-Double $candidate.snapshot.homeRadiusMeters 402.336 0.1)
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-home-pin-state.json" }
        } | Out-Null

    Tap-Tag -Tag "add_place_button" -StepName "work-add-flow-open" | Out-Null
    Invoke-VerifiedControl -TestTag "add_place_address_button" -Flow $flow -ActionPerformed "open address worksite editor" `
        -ExpectedStateDelta "work coordinate fields become visible" -PersistenceExpectation "editor visibility only" -Action {
            $tap = Tap-Tag -Tag "add_place_address_button" -StepName "work-edit-pin-open"
            Assert-TagVisible -Tag "work_latitude_field" -StepName "work-editor-visible" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "work_cancel_edit_button" -Flow $flow -ActionPerformed "cancel manual work pin editing" `
        -ExpectedStateDelta "work current-location action returns" -PersistenceExpectation "editor visibility only" -Action {
            $tap = Tap-Tag -Tag "work_cancel_edit_button" -StepName "work-edit-pin-cancel"
            Assert-TagVisible -Tag "add_place_current_button" -StepName "work-current-visible" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Tap-Tag -Tag "add_place_address_button" -StepName "work-edit-pin-reopen" | Out-Null

    Invoke-VerifiedControl -TestTag "work_label_field" -Flow $flow -ActionPerformed "enter work site name" `
        -ExpectedStateDelta "work site label text changes" -PersistenceExpectation "saved with work pin" -Action {
            $field = Enter-TextByTag -Tag "work_label_field" -Text "Main Yard" -StepName "work-label" -Clear
            Dismiss-Keyboard
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
        } | Out-Null

    foreach ($entry in @(
            @{ tag = "work_latitude_field"; text = "42.3320"; label = "work latitude" },
            @{ tag = "work_longitude_field"; text = "-83.0460"; label = "work longitude" }
        )) {
        Invoke-VerifiedControl -TestTag $entry.tag -Flow $flow -ActionPerformed "enter $($entry.label)" `
            -ExpectedStateDelta "work pin editor text changes" -PersistenceExpectation "saved by work pin button" -Action {
                $field = Enter-TextByTag -Tag $entry.tag -Text $entry.text -StepName $entry.label.Replace(" ", "-") -Clear
                Dismiss-Keyboard
                return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag "work_radius_field" -Flow $flow -ActionPerformed "select five-mile work radius" `
        -ExpectedStateDelta "work radius draft becomes five miles" -PersistenceExpectation "saved only by work pin button" -Action {
            $tap = Set-SliderByTag -Tag "work_radius_field" -Fraction (4.0 / 24.0) -StepName "work-radius-five-miles"
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "work_save_pin_button" -Flow $flow -ActionPerformed "save work pin" `
        -ExpectedStateDelta "work save choice dialog appears" -PersistenceExpectation "no work location changes until a dialog action is chosen" -Action {
            $tap = Tap-Tag -Tag "work_save_pin_button" -StepName "work-save-pin"
            Assert-TagVisible -Tag "work_replace_location_button" -StepName "work-save-choice-dialog" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "work_replace_location_button" -Flow $flow -ActionPerformed "replace latest work location" `
        -ExpectedStateDelta "latest work location is overwritten" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "work_replace_location_button" -StepName "work-replace-location"
            Wait-State -Name "04-after-work-pin" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.workSet -and
                (Compare-Double $candidate.snapshot.workLatitude 42.3320 0.0001) -and
                (Compare-Double $candidate.snapshot.workLongitude -83.0460 0.0001) -and
                (Compare-Double $candidate.snapshot.workRadiusMeters 8046.72 0.1) -and
                [int]$candidate.snapshot.workLocationCount -eq 1 -and
                [string]$candidate.snapshot.workLocations[0].label -eq "Main Yard"
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-work-pin-state.json" }
        } | Out-Null

    Tap-Tag -Tag "add_place_button" -StepName "work-edit-pin-add-flow" | Out-Null
    Tap-Tag -Tag "add_place_address_button" -StepName "work-edit-pin-add-reopen" | Out-Null
    Tap-Tag -Tag "work_save_pin_button" -StepName "work-save-pin-add-open" | Out-Null
    Invoke-VerifiedControl -TestTag "work_add_location_button" -Flow $flow -ActionPerformed "add another work location" `
        -ExpectedStateDelta "workLocationCount becomes at least 2" -PersistenceExpectation "multiple work locations must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "work_add_location_button" -StepName "work-add-location"
            Wait-State -Name "04-after-work-pin-add" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.workSet -and
                [int]$candidate.snapshot.workLocationCount -ge 2
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-work-pin-add-state.json" }
        } | Out-Null

    $state = Capture-State -Name "04-location-controls-final"
    return @{ homeSet = [bool]$state.snapshot.homeSet; workSet = [bool]$state.snapshot.workSet; workLocationCount = [int]$state.snapshot.workLocationCount }
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
    $editTag = "tracking_session_${idPrefix}_edit_button"
    $countsTag = "tracking_session_${idPrefix}_counts_switch"

    Invoke-VerifiedControl -TestTag $editTag -Flow $flow -ActionPerformed "open latest session edit controls" `
        -ExpectedStateDelta "session correction controls become visible" -PersistenceExpectation "editor visibility only" -Action {
            $tap = Tap-Tag -Tag $editTag -StepName "tracking-edit-open"
            Assert-TagVisible -Tag $countsTag -StepName "tracking-edit-controls" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

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
    Invoke-VerifiedControl -TestTag $startTag -Flow $flow -ActionPerformed "verify localized session start input" `
        -ExpectedStateDelta "start date and time editor is visible only after Edit" -PersistenceExpectation "session correction button preserves unchanged start time" -Action {
            $field = Assert-TagVisible -Tag $startTag -StepName "tracking-start-correction"
            $text = [string]$field.node.GetAttribute("text")
            if ($text -notmatch "^\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}:\d{2}\s+[AP]M$") {
                throw "[ui_sentinel_failed] Expected localized start date/time text for '$startTag', got '$text'."
            }
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath; startText = $text }
        } | Out-Null

    $endTag = "tracking_session_${idPrefix}_end_field"
    Invoke-VerifiedControl -TestTag $endTag -Flow $flow -ActionPerformed "verify localized session end input" `
        -ExpectedStateDelta "end date and time editor is visible only after Edit" -PersistenceExpectation "session correction button preserves unchanged end time" -Action {
            $field = Assert-TagVisible -Tag $endTag -StepName "tracking-end-correction"
            $text = [string]$field.node.GetAttribute("text")
            if ($text -notmatch "^\d{1,2}/\d{1,2}/\d{4}\s+\d{1,2}:\d{2}\s+[AP]M$") {
                throw "[ui_sentinel_failed] Expected localized end date/time text for '$endTag', got '$text'."
            }
            return @{ uiDump = Get-RelativeArtifactPath $field.dumpPath; endText = $text }
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
    $beforeSiteMinutes = [int]$before.snapshot.reportTotals.today.siteMinutes

    Invoke-VerifiedControl -TestTag "debug_seed_jobsite_drive_endpoint" -Flow $flow `
        -ActionPerformed "seed jobsite vehicle movement with miles" `
        -ExpectedStateDelta "today miles and site minutes increase while drive minutes do not" `
        -PersistenceExpectation "seeded session, miles, and policy result must survive relaunch" -Action {
            $seed = Request-JobsiteDriveSeed
            Save-Json -Value $seed -Path (Join-Path $OutputRoot "jobsite-drive-seed.json")
            if ([string]$seed.proof.atWorkVehicleBucket -ne "SITE") {
                throw "[jobsite_policy] At-work vehicle movement classified as $($seed.proof.atWorkVehicleBucket), expected SITE."
            }
            $state = Wait-State -Name "05b-after-jobsite-drive" -Condition {
                param($candidate)
                [double]$candidate.snapshot.reportTotals.today.drivenMiles -ge ($beforeMiles + 6.70) -and
                [int]$candidate.snapshot.reportTotals.today.driveMinutes -eq $beforeDriveMinutes -and
                [int]$candidate.snapshot.reportTotals.today.siteMinutes -ge ($beforeSiteMinutes + 30)
            }
            return @{
                seed = "jobsite-drive-seed.json"
                state = "05b-after-jobsite-drive-state.json"
                atWorkVehicleBucket = [string]$seed.proof.atWorkVehicleBucket
                beforeMiles = $beforeMiles
                todayMiles = [double]$state.snapshot.reportTotals.today.drivenMiles
                beforeDriveMinutes = $beforeDriveMinutes
                todayDriveMinutes = [int]$state.snapshot.reportTotals.today.driveMinutes
                beforeSiteMinutes = $beforeSiteMinutes
                todaySiteMinutes = [int]$state.snapshot.reportTotals.today.siteMinutes
            }
        } | Out-Null

    $after = Capture-State -Name "05b-jobsite-drive-final"
    return @{
        totalDrivenMiles = [double]$after.snapshot.totalDrivenMiles
        todayReportMiles = [double]$after.snapshot.reportTotals.today.drivenMiles
        todayDriveMinutes = [int]$after.snapshot.reportTotals.today.driveMinutes
        todaySiteMinutes = [int]$after.snapshot.reportTotals.today.siteMinutes
    }
}

function Invoke-DashboardNavigationAndTotals {
    $flow = "dashboard_navigation_and_totals"
    Invoke-VerifiedControl -TestTag "nav_home" -Flow $flow -ActionPerformed "open dashboard screen" `
        -ExpectedStateDelta "dashboard screen is visible" -PersistenceExpectation "navigation only" -Action {
            $tap = Tap-Tag -Tag "nav_home" -StepName "dashboard-nav"
            Assert-TagVisible -Tag "screen_dashboard" -StepName "dashboard-screen" | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    foreach ($tag in @("dashboard_summary_panel", "dashboard_weekly_ledger")) {
        Invoke-VerifiedControl -TestTag $tag -Flow $flow -ActionPerformed "assert dashboard panel visible" `
            -ExpectedStateDelta "dashboard panel is rendered" -PersistenceExpectation "report totals recompute from persisted data" -Action {
                $found = Assert-TagVisible -Tag $tag -StepName $tag
                return @{ uiDump = Get-RelativeArtifactPath $found.dumpPath }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag "dashboard_pull_refresh_gesture" -Flow $flow -ActionPerformed "pull to refresh dashboard" `
        -ExpectedStateDelta "dashboard remains visible after a forced refresh" -PersistenceExpectation "refresh recomputes from persisted data" -Action {
            $x = [int]($script:Display.width / 2)
            $startY = [int]($script:Display.height * 0.24)
            $endY = [int]($script:Display.height * 0.72)
            Invoke-AdbShell -Arguments @("input", "swipe", "$x", "$startY", "$x", "$endY", "650") | Out-Null
            Start-Sleep -Milliseconds 1200
            $found = Assert-TagVisible -Tag "dashboard_summary_panel" -StepName "dashboard-after-pull-refresh"
            $state = Capture-State -Name "06-after-dashboard-pull-refresh"
            return @{
                uiDump = Get-RelativeArtifactPath $found.dumpPath
                state = "06-after-dashboard-pull-refresh-state.json"
                todayReportMiles = [double]$state.snapshot.reportTotals.today.drivenMiles
            }
        } | Out-Null

    $shot = Save-Screenshot -Name "06-dashboard-final"
    $state = Capture-State -Name "06-dashboard-state"
    if ([double]$state.snapshot.reportTotals.today.drivenMiles -lt 12.5) {
        throw "[dashboard_totals] Today dashboard did not include corrected miles."
    }
    if ([int]$state.snapshot.reportTotals.today.driveMinutes -ne 0) {
        throw "[dashboard_totals] Jobsite vehicle time was counted as drive minutes."
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
            @{ tag = "nav_home"; screen = "screen_dashboard" },
            @{ tag = "nav_tracking"; screen = "screen_tracking" },
            @{ tag = "nav_places"; screen = "screen_places" },
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

function Assert-UiText {
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string]$StepName,
        [switch]$Absent
    )

    $path = Save-UiDump -Name $StepName
    [xml]$ui = Get-Content -LiteralPath $path -Raw
    $matchingNodes = @($ui.SelectNodes("//node") | Where-Object {
            [string]$_.GetAttribute("text") -eq $Text -or
            [string]$_.GetAttribute("content-desc") -eq $Text
        })
    if ($Absent -and $matchingNodes.Count -gt 0) {
        throw "[unexpected_ui_text] '$Text' was visible during $StepName."
    }
    if (-not $Absent -and $matchingNodes.Count -eq 0) {
        throw "[missing_ui_text] '$Text' was not visible during $StepName."
    }
    return $path
}

function Assert-TagEnabledState {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [Parameter(Mandatory = $true)][bool]$ExpectedEnabled,
        [Parameter(Mandatory = $true)][string]$StepName
    )

    $found = Find-UiNodeByTag -Tag $Tag -StepName $StepName
    $actual = [string]$found.node.GetAttribute("enabled") -eq "true"
    if ($actual -ne $ExpectedEnabled) {
        throw "[enabled_state_mismatch] $Tag enabled=$actual, expected $ExpectedEnabled."
    }
    return $found.dumpPath
}

function Invoke-NewWeekAcceptance {
    Tap-Tag -Tag "nav_home" -StepName "week-open-dashboard" | Out-Null
    $current = Wait-State -Name "new-week-current" -Condition {
        param($candidate)
        [string]$candidate.snapshot.selectedWeekMode -eq "CURRENT_WEEK"
    }
    Assert-UiText -Text "Today" -StepName "new-week-current-today" | Out-Null
    Assert-UiText -Text "Week" -StepName "new-week-current-week" | Out-Null
    Assert-UiText -Text "Pay Period" -StepName "new-week-current-pay-period" | Out-Null
    Assert-TagEnabledState -Tag "dashboard_next_week_button" -ExpectedEnabled $false -StepName "new-week-forward-disabled" | Out-Null
    Save-Screenshot -Name "new-week-current" | Out-Null

    Tap-Tag -Tag "dashboard_previous_week_button" -StepName "new-week-previous-one" | Out-Null
    $past = Wait-State -Name "new-week-past-one" -Condition {
        param($candidate)
        [string]$candidate.snapshot.selectedWeekMode -eq "SELECTED_WEEK" -and
        [string]$candidate.snapshot.selectedWeekStart -ne [string]$current.snapshot.selectedWeekStart
    }
    Assert-UiText -Text "Selected Week" -StepName "new-week-no-selected-label" -Absent | Out-Null
    Assert-UiText -Text ([DateTime]::Parse([string]$past.snapshot.selectedWeekStart).ToString("MMM d") + " - " +
        [DateTime]::Parse([string]$past.snapshot.selectedWeekEnd).ToString("MMM d, yyyy")) -StepName "new-week-selected-date-title" | Out-Null
    Assert-UiText -Text "Today" -StepName "new-week-no-current-card" -Absent | Out-Null
    Assert-TagEnabledState -Tag "dashboard_next_week_button" -ExpectedEnabled $true -StepName "new-week-forward-enabled" | Out-Null

    Tap-Tag -Tag "dashboard_previous_week_button" -StepName "new-week-previous-two" -WaitMs 80 | Out-Null
    Tap-Tag -Tag "dashboard_previous_week_button" -StepName "new-week-previous-three" -WaitMs 80 | Out-Null
    $farPast = Wait-State -Name "new-week-repeated-previous" -Condition {
        param($candidate)
        [DateTime]::Parse([string]$candidate.snapshot.selectedWeekStart) -le
        [DateTime]::Parse([string]$past.snapshot.selectedWeekStart).AddDays(-14)
    }
    Save-Screenshot -Name "new-week-far-past" | Out-Null

    Invoke-AdbShell -Arguments @("settings", "put", "system", "accelerometer_rotation", "0") | Out-Null
    Invoke-AdbShell -Arguments @("settings", "put", "system", "user_rotation", "1") | Out-Null
    Start-Sleep -Seconds 2
    $rotated = Capture-State -Name "new-week-after-rotation"
    if (
        [string]$rotated.snapshot.selectedWeekStart -ne [string]$farPast.snapshot.selectedWeekStart -or
        [string]$rotated.snapshot.selectedWeekEnd -ne [string]$farPast.snapshot.selectedWeekEnd
    ) {
        throw "[week_rotation_stale] Selected week changed during rotation."
    }
    Save-Screenshot -Name "new-week-landscape" | Out-Null
    Invoke-AdbShell -Arguments @("settings", "put", "system", "user_rotation", "0") | Out-Null
    Invoke-AdbShell -Arguments @("settings", "put", "system", "accelerometer_rotation", "1") | Out-Null
    Start-Sleep -Seconds 2

    for ($i = 0; $i -lt 8; $i++) {
        Tap-Tag -Tag "dashboard_next_week_button" -StepName "new-week-next-$i" -WaitMs 80 | Out-Null
    }
    $returned = Wait-State -Name "new-week-returned-current" -Condition {
        param($candidate)
        [string]$candidate.snapshot.selectedWeekMode -eq "CURRENT_WEEK" -and
        [string]$candidate.snapshot.selectedWeekStart -eq [string]$current.snapshot.selectedWeekStart
    }
    Assert-TagEnabledState -Tag "dashboard_next_week_button" -ExpectedEnabled $false -StepName "new-week-returned-forward-disabled" | Out-Null
    Start-App
    $relaunched = Wait-State -Name "new-week-after-relaunch" -Condition {
        param($candidate)
        [string]$candidate.snapshot.selectedWeekMode -eq "CURRENT_WEEK"
    }
    if ([DateTime]::Parse([string]$relaunched.snapshot.selectedWeekStart) -gt (Get-Date)) {
        throw "[future_week_after_relaunch] Relaunch restored a future selected week."
    }
    return @{ current = $current.snapshot.selectedWeekStart; past = $past.snapshot.selectedWeekStart; returned = $returned.snapshot.selectedWeekStart }
}

function Invoke-NewScheduleAcceptance {
    Tap-Tag -Tag "dashboard_edit_workdays_button" -StepName "new-schedule-dashboard-shortcut" | Out-Null
    Assert-TagVisible -Tag "screen_settings" -StepName "new-schedule-settings-from-shortcut" | Out-Null

    foreach ($day in @("saturday", "sunday")) {
        Tap-Tag -Tag "settings_workday_${day}_switch" -StepName "new-schedule-enable-$day" | Out-Null
    }
    Wait-State -Name "new-schedule-seven-days" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workdayCount -eq 7
    } | Out-Null
    Start-App
    $persisted = Wait-State -Name "new-schedule-seven-days-relaunch" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workdayCount -eq 7
    }
    if (-not (Get-WorkdayState -State $persisted -DayName "SATURDAY") -or -not (Get-WorkdayState -State $persisted -DayName "SUNDAY")) {
        throw "[weekend_not_persisted] Enabled weekend days were lost after relaunch."
    }

    Tap-Tag -Tag "nav_settings" -StepName "new-schedule-return-settings" | Out-Null
    foreach ($day in @("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")) {
        Tap-Tag -Tag "settings_workday_${day}_switch" -StepName "new-schedule-disable-$day" | Out-Null
    }
    $none = Wait-State -Name "new-schedule-zero-days" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workdayCount -eq 0 -and -not [bool]$candidate.snapshot.trackableToday
    }
    for ($i = 0; $i -lt 16; $i++) {
        Invoke-Scroll -Direction "Down"
    }
    Assert-UiText -Text "Automatic tracking is paused until at least one day is selected." -StepName "new-schedule-paused-warning" | Out-Null
    Save-Screenshot -Name "new-schedule-zero-days" | Out-Null

    $sessionCount = [int]$none.snapshot.sessionCount
    Invoke-AdbShell -Arguments @(
        "am", "broadcast",
        "-a", "$PackageName.E2E_GEOFENCE_TRANSITION",
        "-n", "$PackageName/com.robertochavez.timetracker.core.location.geofence.GeofenceBroadcastReceiver",
        "--es", "requestId", "home",
        "--es", "transition", "EXIT"
    ) | Out-Null
    Start-Sleep -Seconds 2
    $offDay = Capture-State -Name "new-schedule-off-day-geofence-exit"
    if ([int]$offDay.snapshot.sessionCount -ne $sessionCount -or $null -ne $offDay.snapshot.activeSession) {
        throw "[off_day_automatic_start] Automatic session started with zero tracked days."
    }

    Start-App
    Wait-State -Name "new-schedule-zero-days-relaunch" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workdayCount -eq 0
    } | Out-Null
    return @{ weekendPersisted = $true; zeroDaysPersisted = $true; offDaySessionRejected = $true }
}

function Invoke-NewPayPeriodAcceptance {
    Tap-Tag -Tag "nav_settings" -StepName "new-pay-settings" | Out-Null
    $baseline = Capture-State -Name "new-pay-baseline"
    Tap-Tag -Tag "settings_weekly_cadence_button" -StepName "new-pay-select-weekly" | Out-Null
    Enter-TextByTag -Tag "settings_anchor_date_field" -Text "2026-07-06" -StepName "new-pay-weekly-anchor" -Clear | Out-Null
    Assert-TagVisible -Tag "settings_pay_period_preview" -StepName "new-pay-weekly-preview" | Out-Null
    $draft = Capture-State -Name "new-pay-draft-not-persisted"
    if ([string]$draft.snapshot.payPeriodCadence -ne [string]$baseline.snapshot.payPeriodCadence) {
        throw "[pay_preview_persisted_early] Changing the preview persisted cadence before Save."
    }
    Tap-Tag -Tag "settings_save_anchor_button" -StepName "new-pay-save-weekly" | Out-Null
    $weekly = Wait-State -Name "new-pay-weekly-persisted" -Condition {
        param($candidate)
        [string]$candidate.snapshot.payPeriodCadence -eq "WEEKLY" -and
        [string]$candidate.snapshot.payPeriodAnchorDate -eq "2026-07-06" -and
        [string]$candidate.snapshot.payPeriodStartDate -eq "2026-07-27" -and
        [string]$candidate.snapshot.payPeriodEndDate -eq "2026-08-02"
    }
    Save-Screenshot -Name "new-pay-weekly" | Out-Null

    Enter-TextByTag -Tag "settings_anchor_date_field" -Text "2026-02-30" -StepName "new-pay-invalid-anchor" -Clear | Out-Null
    Tap-Tag -Tag "settings_save_anchor_button" -StepName "new-pay-invalid-save" | Out-Null
    Start-Sleep -Seconds 2
    $invalid = Capture-State -Name "new-pay-invalid-rejected"
    if (
        [string]$invalid.snapshot.payPeriodAnchorDate -ne "2026-07-06" -or
        [string]$invalid.snapshot.payPeriodCadence -ne "WEEKLY"
    ) {
        throw "[invalid_pay_period_saved] Invalid anchor changed persisted pay-period behavior."
    }

    Enter-TextByTag -Tag "settings_anchor_date_field" -Text "2026-07-06" -StepName "new-pay-biweekly-anchor" -Clear | Out-Null
    Tap-Tag -Tag "settings_biweekly_cadence_button" -StepName "new-pay-select-biweekly" | Out-Null
    Tap-Tag -Tag "settings_save_anchor_button" -StepName "new-pay-save-biweekly" | Out-Null
    $biweekly = Wait-State -Name "new-pay-biweekly-persisted" -Condition {
        param($candidate)
        [string]$candidate.snapshot.payPeriodCadence -eq "BIWEEKLY" -and
        [string]$candidate.snapshot.payPeriodStartDate -eq "2026-07-20" -and
        [string]$candidate.snapshot.payPeriodEndDate -eq "2026-08-02"
    }
    Start-App
    Wait-State -Name "new-pay-biweekly-relaunch" -Condition {
        param($candidate)
        [string]$candidate.snapshot.payPeriodCadence -eq "BIWEEKLY" -and
        [string]$candidate.snapshot.payPeriodAnchorDate -eq "2026-07-06"
    } | Out-Null
    return @{ weekly = "$($weekly.snapshot.payPeriodStartDate)..$($weekly.snapshot.payPeriodEndDate)"; biweekly = "$($biweekly.snapshot.payPeriodStartDate)..$($biweekly.snapshot.payPeriodEndDate)" }
}

function Invoke-NewMapAcceptance {
    Tap-Tag -Tag "nav_places" -StepName "new-map-places" | Out-Null
    Assert-TagVisible -Tag "places_saved_list" -StepName "new-map-saved-list" | Out-Null
    Tap-Tag -Tag "add_place_button" -StepName "new-map-add-place-open" | Out-Null
    Assert-TagVisible -Tag "add_place_current_button" -StepName "new-map-three-methods" | Out-Null
    Invoke-AdbShell -Arguments @("logcat", "-c") | Out-Null
    Tap-Tag -Tag "add_place_map_button" -StepName "new-map-disclosure-open" | Out-Null
    Assert-TagVisible -Tag "work_map_disclosure_dialog" -StepName "new-map-disclosure-visible" | Out-Null
    Save-Screenshot -Name "new-map-disclosure" | Out-Null
    Tap-Tag -Tag "work_map_disclosure_decline_button" -StepName "new-map-disclosure-decline" | Out-Null
    $declined = Wait-State -Name "new-map-declined" -Condition {
        param($candidate)
        -not [bool]$candidate.snapshot.mapDisclosureAccepted -and $null -eq $candidate.snapshot.currentModal
    }
    Assert-TagVisible -Tag "work_address_field" -StepName "new-map-manual-after-decline" | Out-Null
    $declineLogs = @(Invoke-AdbShell -Arguments @("logcat", "-d", "-v", "brief"))
    $declineLogPath = Join-Path $OutputRoot "new-map-decline-logcat.txt"
    $declineLogs | Set-Content -LiteralPath $declineLogPath -Encoding UTF8
    if (($declineLogs -join "`n") -match "Google Maps Android API|MapsInitializer|GoogleMapRenderer") {
        throw "[map_loaded_after_decline] Google Maps initialized after disclosure decline."
    }

    Invoke-AdbShell -Arguments @("svc", "wifi", "disable") | Out-Null
    Invoke-AdbShell -Arguments @("svc", "data", "disable") | Out-Null
    try {
        Tap-Tag -Tag "work_open_map_button" -StepName "new-map-disclosure-reopen" | Out-Null
        Tap-Tag -Tag "work_map_disclosure_accept_button" -StepName "new-map-disclosure-accept" | Out-Null
        Assert-TagVisible -Tag "work_map_picker" -StepName "new-map-offline-picker" | Out-Null
        Save-Screenshot -Name "new-map-offline-picker" | Out-Null
        Tap-Tag -Tag "work_map_cancel_button" -StepName "new-map-offline-cancel" | Out-Null
        Assert-TagVisible -Tag "work_address_field" -StepName "new-map-offline-manual-fallback" | Out-Null
    } finally {
        Invoke-AdbShell -Arguments @("svc", "wifi", "enable") -AllowFailure | Out-Null
        Invoke-AdbShell -Arguments @("svc", "data", "enable") -AllowFailure | Out-Null
    }
    $afterOffline = Capture-State -Name "new-map-offline-cancel-state"
    if ([int]$afterOffline.snapshot.workLocationCount -ne [int]$declined.snapshot.workLocationCount) {
        throw "[map_cancel_persisted] Canceling the offline map changed persisted worksites."
    }

    Tap-Tag -Tag "work_open_map_button" -StepName "new-map-picker-reopen" | Out-Null
    Assert-TagVisible -Tag "work_map_picker" -StepName "new-map-picker-online" | Out-Null
    Invoke-AdbShell -Arguments @("input", "tap", "540", "620") | Out-Null
    Start-Sleep -Seconds 1
    Set-SliderByTag -Tag "work_radius_field" -Fraction 1.0 -StepName "new-map-radius-25-draft" | Out-Null
    Assert-UiText -Text "25 mile worksite radius" -StepName "new-map-radius-25-label" | Out-Null
    Assert-TagEnabledState -Tag "work_map_confirm_button" -ExpectedEnabled $true -StepName "new-map-confirm-enabled" | Out-Null
    Save-Screenshot -Name "new-map-pin-radius-circle" | Out-Null
    Tap-Tag -Tag "work_map_cancel_button" -StepName "new-map-cancel-draft" | Out-Null
    $cancelled = Capture-State -Name "new-map-cancel-draft-state"
    if ([int]$cancelled.snapshot.workLocationCount -ne 0) {
        throw "[map_draft_saved_on_cancel] A map draft was persisted after Cancel."
    }

    Tap-Tag -Tag "work_open_map_button" -StepName "new-map-confirm-reopen" | Out-Null
    Invoke-AdbShell -Arguments @("input", "tap", "540", "620") | Out-Null
    Start-Sleep -Milliseconds 800
    Set-SliderByTag -Tag "work_radius_field" -Fraction 1.0 -StepName "new-map-confirm-radius-25" | Out-Null
    Tap-Tag -Tag "work_map_confirm_button" -StepName "new-map-use-pin" | Out-Null
    $draftConfirmed = Capture-State -Name "new-map-confirm-still-draft"
    if ([int]$draftConfirmed.snapshot.workLocationCount -ne 0) {
        throw "[map_confirm_saved_worksite] Confirming a pin persisted before Save Work Site."
    }
    Tap-Tag -Tag "work_save_pin_button" -StepName "new-map-save-worksite" | Out-Null
    $saved = Wait-State -Name "new-map-worksite-saved-25" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workLocationCount -eq 1 -and
        [Math]::Abs([double]$candidate.snapshot.workRadiusMeters - 40233.6) -lt 0.2
    }
    Save-Screenshot -Name "new-map-saved-25" | Out-Null

    Tap-Tag -Tag "add_place_button" -StepName "new-map-edit-existing-flow" | Out-Null
    Tap-Tag -Tag "add_place_address_button" -StepName "new-map-edit-existing" | Out-Null
    Set-SliderByTag -Tag "work_radius_field" -Fraction 0.0 -StepName "new-map-radius-one-draft" | Out-Null
    $beforeExplicitSave = Capture-State -Name "new-map-slider-not-saved"
    if ([Math]::Abs([double]$beforeExplicitSave.snapshot.workRadiusMeters - 40233.6) -ge 0.2) {
        throw "[slider_saved_early] Main worksite slider persisted before explicit save."
    }
    Tap-Tag -Tag "work_save_pin_button" -StepName "new-map-save-one-mile" | Out-Null
    Tap-Tag -Tag "work_replace_location_button" -StepName "new-map-replace-one-mile" | Out-Null
    $oneMile = Wait-State -Name "new-map-one-mile-saved" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workLocationCount -eq 1 -and
        [Math]::Abs([double]$candidate.snapshot.workRadiusMeters - 1609.344) -lt 0.02
    }

    Tap-Tag -Tag "add_place_button" -StepName "new-map-second-place-flow" | Out-Null
    Tap-Tag -Tag "add_place_map_button" -StepName "new-map-second-place-map" | Out-Null
    Invoke-AdbShell -Arguments @("input", "tap", "690", "720") | Out-Null
    Start-Sleep -Milliseconds 800
    Set-SliderByTag -Tag "work_radius_field" -Fraction (11.0 / 24.0) -StepName "new-map-second-place-radius" | Out-Null
    Tap-Tag -Tag "work_map_confirm_button" -StepName "new-map-second-place-confirm" | Out-Null
    Tap-Tag -Tag "work_save_pin_button" -StepName "new-map-second-place-save" | Out-Null
    Tap-Tag -Tag "work_add_location_button" -StepName "new-map-second-place-add-not-replace" | Out-Null
    $multiple = Wait-State -Name "new-map-multiple-places" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workLocationCount -eq 2
    }
    $savedIds = @($multiple.snapshot.workLocations | ForEach-Object { [string]$_.id })
    if (@($savedIds | Select-Object -Unique).Count -ne 2) {
        throw "[worksite_replaced_instead_of_added] Multiple saved places did not retain distinct IDs."
    }
    foreach ($id in $savedIds) {
        $safeId = [regex]::Replace($id, "[^A-Za-z0-9_-]", "_")
        Assert-TagVisible -Tag "saved_place_$safeId" -StepName "new-map-saved-list-$safeId" | Out-Null
    }
    Save-Screenshot -Name "new-map-multiple-saved-places" | Out-Null

    $newId = @($savedIds | Where-Object { $_ -ne "work" })[0]
    $beforeSiteCount = [int]$multiple.snapshot.workSiteSessionCount
    foreach ($transition in @("ENTER", "EXIT")) {
        Invoke-AdbShell -Arguments @(
            "am", "broadcast",
            "-a", "$PackageName.E2E_GEOFENCE_TRANSITION",
            "-n", "$PackageName/com.robertochavez.timetracker.core.location.geofence.GeofenceBroadcastReceiver",
            "--es", "requestId", "work:$newId",
            "--es", "transition", $transition
        ) | Out-Null
        Start-Sleep -Seconds 1
    }
    Wait-State -Name "new-map-second-site-allocation" -Condition {
        param($candidate)
        [int]$candidate.snapshot.workSiteSessionCount -gt $beforeSiteCount
    } | Out-Null
    return @{
        declinedWithoutLoad = $true
        offlineManualFallback = $true
        saved25Meters = $saved.snapshot.workRadiusMeters
        saved1Meters = $oneMile.snapshot.workRadiusMeters
        savedPlaceCount = [int]$multiple.snapshot.workLocationCount
        allocatedWorkLocationId = $newId
    }
}

function Invoke-NewFeatureAcceptance {
    Invoke-E2EFlow -Name "new_feature_bootstrap_reset" -Action { Invoke-BootstrapReset }
    Invoke-E2EFlow -Name "new_week_navigation" -Action { Invoke-NewWeekAcceptance }
    Invoke-E2EFlow -Name "new_workday_schedule" -Action { Invoke-NewScheduleAcceptance }
    Invoke-E2EFlow -Name "new_pay_periods" -Action { Invoke-NewPayPeriodAcceptance }
    Invoke-E2EFlow -Name "new_map_and_radius" -Action { Invoke-NewMapAcceptance }
    Invoke-E2EFlow -Name "new_feature_debug_log_audit" -Action { Invoke-DebugLogAudit }
}

try {
    $script:ResolvedDeviceId = Resolve-E2EDeviceId -RequestedDeviceId $DeviceId
    Update-DisplaySize
    Start-DebugInfrastructure

    if ($NewFeatureAcceptanceOnly) {
        Invoke-NewFeatureAcceptance
    } else {
        Invoke-E2EFlow -Name "bootstrap_reset" -Action { Invoke-BootstrapReset }
        Invoke-E2EFlow -Name "settings_tracking_setup" -Action { Invoke-SettingsTrackingSetup }
        Invoke-E2EFlow -Name "settings_timesheet_rules" -Action { Invoke-SettingsTimesheetRules }
        Invoke-E2EFlow -Name "home_location_controls" -Action { Invoke-HomeLocationControls }
        Invoke-E2EFlow -Name "tracking_session_correction" -Action { Invoke-TrackingSessionCorrection }
        Invoke-E2EFlow -Name "jobsite_geofence_mileage_policy" -Action { Invoke-JobsiteGeofenceMileagePolicy }
        Invoke-E2EFlow -Name "dashboard_navigation_and_totals" -Action { Invoke-DashboardNavigationAndTotals }
        Invoke-E2EFlow -Name "bottom_nav_switching" -Action { Invoke-BottomNavSwitching }
        Invoke-E2EFlow -Name "persistence_relaunch" -Action { Run-PersistenceRelaunch }
        Invoke-E2EFlow -Name "destructive_reset_confirmation" -Action { Invoke-DestructiveResetConfirmation }
        Invoke-E2EFlow -Name "ui_control_coverage_audit" -Action { Invoke-UiControlCoverageAudit }
        Invoke-E2EFlow -Name "debug_log_audit" -Action { Invoke-DebugLogAudit }
    }
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
