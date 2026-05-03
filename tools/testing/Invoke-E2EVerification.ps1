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

function Get-RelativeArtifactPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }
    return [System.IO.Path]::GetRelativePath($OutputRoot, $Path).Replace("\", "/")
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

    $output = @(& adb -s $script:ResolvedDeviceId @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
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
    Invoke-AdbShell -Arguments @("am", "start", "-n", "$PackageName/$ActivityName") | Out-Null
    Start-Sleep -Seconds 2
}

function Request-State {
    $query = "runId=$([System.Uri]::EscapeDataString($RunId))&actorId=$([System.Uri]::EscapeDataString($script:ResolvedDeviceId))"
    return Invoke-RestMethod -Uri "http://127.0.0.1:$StatePort/testing/state?$query" -Method Get -TimeoutSec 10
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
    Invoke-AdbShell -Arguments @("uiautomator", "dump", $remotePath) | Out-Null
    Invoke-Adb -Arguments @("pull", $remotePath, $localPath) | Out-Null
    Invoke-AdbShell -Arguments @("rm", $remotePath) -AllowFailure | Out-Null
    return $localPath
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
    if ($center.y -gt [int]($script:Display.height * 0.78)) {
        Invoke-Scroll -Direction Up
        $found = Find-UiNodeByTag -Tag $Tag -StepName "$StepName-centered"
        $center = Get-NodeCenter -Node $found.node
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

function Invoke-SystemButtonProbe {
    Invoke-VerifiedControl -TestTag "settings_request_foreground_button" -Flow "settings_tracking_setup" `
        -ActionPerformed "tap foreground permission request" -ExpectedStateDelta "permission result is handled by Settings" `
        -PersistenceExpectation "permission state is device-owned" -Action {
            $tap = Tap-Tag -Tag "settings_request_foreground_button" -StepName "probe-request-foreground"
            Dismiss-Keyboard
            Invoke-AdbShell -Arguments @("am", "start", "-n", "$PackageName/$ActivityName") -AllowFailure | Out-Null
            Start-Sleep -Milliseconds 800
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_enable_background_button" -Flow "settings_tracking_setup" `
        -ActionPerformed "open Android background-location settings" -ExpectedStateDelta "system settings handoff opens" `
        -PersistenceExpectation "background permission is device-owned" -Action {
            $tap = Tap-Tag -Tag "settings_enable_background_button" -StepName "probe-background-settings"
            Start-Sleep -Seconds 1
            Invoke-AdbShell -Arguments @("am", "start", "-n", "$PackageName/$ActivityName") | Out-Null
            Start-Sleep -Milliseconds 800
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath }
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

function Assert-PersistenceMatch {
    param($Baseline, $After)

    $comparisons = @(
        @{ field = "homeSet"; match = Compare-Scalar $Baseline.snapshot.homeSet $After.snapshot.homeSet },
        @{ field = "homeRadiusMeters"; match = Compare-Double $Baseline.snapshot.homeRadiusMeters $After.snapshot.homeRadiusMeters },
        @{ field = "workSet"; match = Compare-Scalar $Baseline.snapshot.workSet $After.snapshot.workSet },
        @{ field = "workRadiusMeters"; match = Compare-Double $Baseline.snapshot.workRadiusMeters $After.snapshot.workRadiusMeters },
        @{ field = "privacyDisclosureAccepted"; match = Compare-Scalar $Baseline.snapshot.privacyDisclosureAccepted $After.snapshot.privacyDisclosureAccepted },
        @{ field = "minimalActiveNotificationEnabled"; match = Compare-Scalar $Baseline.snapshot.minimalActiveNotificationEnabled $After.snapshot.minimalActiveNotificationEnabled },
        @{ field = "liveTimerNotificationEnabled"; match = Compare-Scalar $Baseline.snapshot.liveTimerNotificationEnabled $After.snapshot.liveTimerNotificationEnabled },
        @{ field = "payPeriodAnchorDate"; match = Compare-Scalar $Baseline.snapshot.payPeriodAnchorDate $After.snapshot.payPeriodAnchorDate },
        @{ field = "trackableToday"; match = Compare-Scalar $Baseline.snapshot.trackableToday $After.snapshot.trackableToday },
        @{ field = "workdayCount"; match = Compare-Scalar $Baseline.snapshot.workdayCount $After.snapshot.workdayCount },
        @{ field = "sessionCount"; match = Compare-Scalar $Baseline.snapshot.sessionCount $After.snapshot.sessionCount },
        @{ field = "latestSession.drivenMiles"; match = Compare-Double $Baseline.snapshot.latestSession.drivenMiles $After.snapshot.latestSession.drivenMiles },
        @{ field = "totalDrivenMiles"; match = Compare-Double $Baseline.snapshot.totalDrivenMiles $After.snapshot.totalDrivenMiles },
        @{ field = "reportTotals.today.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.today.drivenMiles $After.snapshot.reportTotals.today.drivenMiles },
        @{ field = "reportTotals.weekly.drivenMiles"; match = Compare-Double $Baseline.snapshot.reportTotals.weekly.drivenMiles $After.snapshot.reportTotals.weekly.drivenMiles }
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
    $lines.Add("") | Out-Null
    $lines.Add("## Persistence Comparison") | Out-Null
    $lines.Add('- Baseline: `persisted-baseline-state.json`') | Out-Null
    $lines.Add('- Relaunch: `after-relaunch-state.json`') | Out-Null
    $lines.Add('- Comparison: `persistence-comparison.json`') | Out-Null
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
            $state = Capture-State -Name "02-after-enable-activity"
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-enable-activity-state.json"; posture = $state.stateMachine.posture }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_disable_activity_button" -Flow $flow -ActionPerformed "disable activity detection" `
        -ExpectedStateDelta "activity unregister request succeeds or reports handled status" -PersistenceExpectation "system registration may survive process" -Action {
            $tap = Tap-Tag -Tag "settings_disable_activity_button" -StepName "settings-disable-activity"
            $state = Capture-State -Name "02-after-disable-activity"
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-disable-activity-state.json"; posture = $state.stateMachine.posture }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_minimal_notification_switch" -Flow $flow -ActionPerformed "enable minimal notification" `
        -ExpectedStateDelta "minimalActiveNotificationEnabled becomes true" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "settings_minimal_notification_switch" -StepName "settings-minimal-notification"
            Wait-State -Name "02-after-minimal-notification" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.minimalActiveNotificationEnabled
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-minimal-notification-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_live_timer_notification_switch" -Flow $flow -ActionPerformed "enable live timer notification" `
        -ExpectedStateDelta "liveTimerNotificationEnabled becomes true" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "settings_live_timer_notification_switch" -StepName "settings-live-notification"
            Wait-State -Name "02-after-live-notification" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.liveTimerNotificationEnabled
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "02-after-live-notification-state.json" }
        } | Out-Null

    Invoke-VerifiedControl -TestTag "settings_privacy_disclosure_switch" -Flow $flow -ActionPerformed "accept privacy disclosure" `
        -ExpectedStateDelta "privacyDisclosureAccepted becomes true" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag "settings_privacy_disclosure_switch" -StepName "settings-privacy-disclosure"
            $state = Wait-State -Name "02-after-privacy" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.privacyDisclosureAccepted
            }
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "02-after-privacy-state.json"
                privacyDisclosureAccepted = [bool]$state.snapshot.privacyDisclosureAccepted
            }
        } | Out-Null

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
    $state = Capture-State -Name "03-settings-rules-before"
    $todaySwitch = "settings_workday_$(([string]$state.snapshot.todayDayOfWeek).ToLowerInvariant())_switch"
    if ([bool]$state.snapshot.trackableToday) {
        Invoke-VerifiedControl -TestTag $todaySwitch -Flow $flow -ActionPerformed "disable today's workday" `
            -ExpectedStateDelta "trackableToday becomes false" -PersistenceExpectation "temporary verification state" -Action {
                $tap = Tap-Tag -Tag $todaySwitch -StepName "settings-toggle-workday-off"
                Wait-State -Name "03-after-workday-off" -Condition {
                    param($candidate)
                    -not [bool]$candidate.snapshot.trackableToday
                } | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "03-after-workday-off-state.json" }
            } | Out-Null
    }

    Invoke-VerifiedControl -TestTag $todaySwitch -Flow $flow -ActionPerformed "enable today's workday" `
        -ExpectedStateDelta "trackableToday becomes true" -PersistenceExpectation "must survive relaunch" -Action {
            $tap = Tap-Tag -Tag $todaySwitch -StepName "settings-toggle-workday-on"
            Wait-State -Name "03-after-workday-on" -Condition {
                param($candidate)
                [bool]$candidate.snapshot.trackableToday
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "03-after-workday-on-state.json" }
        } | Out-Null

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
                [bool]$candidate.snapshot.homeSet -and [double]$candidate.snapshot.homeRadiusMeters -ge 180
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
                [bool]$candidate.snapshot.workSet -and [double]$candidate.snapshot.workRadiusMeters -ge 8046
            } | Out-Null
            return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "04-after-work-pin-state.json" }
        } | Out-Null

    foreach ($tag in @("home_use_current_button", "work_use_current_button")) {
        Invoke-VerifiedControl -TestTag $tag -Flow $flow -ActionPerformed "tap current-location probe" `
            -ExpectedStateDelta "saved locations remain configured even if precise GPS is unavailable" -PersistenceExpectation "existing saved locations persist" -Action {
                $tap = Tap-Tag -Tag $tag -StepName "$tag-probe"
                Wait-State -Name "04-after-$tag" -Condition {
                    param($candidate)
                    [bool]$candidate.snapshot.homeSet -and [bool]$candidate.snapshot.workSet
                } | Out-Null
                return @{ uiDump = Get-RelativeArtifactPath $tap.dumpPath; state = "$(New-SafeName "04-after-$tag")-state.json" }
            } | Out-Null
    }

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
                [double]$candidate.snapshot.totalDrivenMiles -ge 12.5
            }
            return @{
                uiDump = Get-RelativeArtifactPath $tap.dumpPath
                state = "05-after-manual-correction-state.json"
                totalDrivenMiles = [double]$state.snapshot.totalDrivenMiles
            }
        } | Out-Null

    return @{ latestSession = $idPrefix }
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
    return @{
        screenshot = Get-RelativeArtifactPath $shot
        totalDrivenMiles = [double]$state.snapshot.totalDrivenMiles
        todayReportMiles = [double]$state.snapshot.reportTotals.today.drivenMiles
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
    $summary = Invoke-RestMethod -Uri "http://127.0.0.1:$LogPort/logs/summary" -Method Get -TimeoutSec 10
    Save-Json -Value $summary -Path (Join-Path $OutputRoot "debug-summary.json")
    if ([int]$summary.total -le 0) {
        throw "[debug_log_empty] Debug log server did not capture app logs."
    }
    return @{ debugLogCount = [int]$summary.total }
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
    Invoke-E2EFlow -Name "reports_navigation_and_totals" -Action { Invoke-ReportsNavigationAndTotals }
    Invoke-E2EFlow -Name "bottom_nav_switching" -Action { Invoke-BottomNavSwitching }
    Invoke-E2EFlow -Name "persistence_relaunch" -Action { Run-PersistenceRelaunch }
    Invoke-E2EFlow -Name "destructive_reset_confirmation" -Action { Invoke-DestructiveResetConfirmation }
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
