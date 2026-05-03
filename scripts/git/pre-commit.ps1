# Kotlin/Android pre-commit quality gate.
#
# The hook is intentionally Gradle-task based. The app scaffold is expected to
# provide formatting, static analysis, and unit-test tasks before Kotlin feature
# work begins.

param()

$ErrorActionPreference = "Stop"

function Write-Section {
    param([string]$Message)

    Write-Host ""
    Write-Host $Message -ForegroundColor Cyan
    Write-Host ("=" * $Message.Length) -ForegroundColor Cyan
}

function Fail {
    param([string]$Message)

    Write-Host $Message -ForegroundColor Red
    exit 1
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Fail "Git is required for the pre-commit hook."
}

$insideGitOutput = @(& git rev-parse --is-inside-work-tree 2>$null)
$insideGitExitCode = $LASTEXITCODE
$insideGit = (($insideGitOutput | Select-Object -First 1) -as [string]).Trim()
if ($insideGitExitCode -ne 0 -or $insideGit -ne "true") {
    Write-Host "Not a Git work tree - skipping pre-commit checks." -ForegroundColor Yellow
    exit 0
}

$repoRootOutput = @(& git rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) {
    Fail "Failed to resolve repository root."
}

$repoRoot = (($repoRootOutput | Select-Object -First 1) -as [string]).Trim()
Set-Location -LiteralPath $repoRoot

$stagedFiles = @(git diff --cached --name-only --diff-filter=ACM)
if ($LASTEXITCODE -ne 0) {
    Fail "Failed to read staged files."
}

$stagedEnv = @($stagedFiles | Where-Object {
    $_ -match '(^|/)\.env($|\.)' -or $_ -match '\.env$'
})

if ($stagedEnv.Count -gt 0) {
    foreach ($file in $stagedEnv) {
        Write-Host "BLOCKED: environment file staged for commit: $file" -ForegroundColor Red
    }
    exit 1
}

$stagedKotlin = @($stagedFiles | Where-Object {
    ($_ -match '\.(kt|kts)$') -and
    ($_ -notmatch '(^|/)build/') -and
    ($_ -notmatch '(^|/)generated/')
})

$stagedAndroidXml = @($stagedFiles | Where-Object {
    ($_ -match '\.xml$') -and
    ($_ -notmatch '(^|/)build/') -and
    ($_ -notmatch '(^|/)generated/')
})

$stagedGradle = @($stagedFiles | Where-Object {
    $_ -match '(^|/)(settings|build)\.gradle\.kts$' -or
    $_ -match '(^|/)gradle\.properties$' -or
    $_ -match '(^|/)libs\.versions\.toml$'
})

$qualityFiles = @($stagedKotlin + $stagedAndroidXml + $stagedGradle)

if ($qualityFiles.Count -eq 0) {
    Write-Host "No staged Kotlin, Android XML, Gradle, or version catalog files - skipping pre-commit checks." -ForegroundColor Yellow
    exit 0
}

Write-Section "Pre-commit: Android/Kotlin quality gate"
Write-Host "Staged Kotlin files: $($stagedKotlin.Count)"
Write-Host "Staged Android XML files: $($stagedAndroidXml.Count)"
Write-Host "Staged Gradle/catalog files: $($stagedGradle.Count)"

$gradleCandidates = @(
    (Join-Path -Path (Get-Location) -ChildPath "gradlew.bat")
    (Join-Path -Path (Get-Location) -ChildPath "gradlew")
)

$script:gradleExecutable = $gradleCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $script:gradleExecutable) {
    Fail "Gradle wrapper not found. Add the Android Gradle scaffold before committing Kotlin/Android build changes."
}

$script:gradleTasks = $null

function Get-GradleTasks {
    if ($null -eq $script:gradleTasks) {
        Write-Section "Discovering Gradle tasks"
        $output = & $script:gradleExecutable "-q" "tasks" "--all" "--console=plain" 2>&1
        if ($LASTEXITCODE -ne 0) {
            $output | ForEach-Object { Write-Host $_ }
            Fail "Failed to list Gradle tasks."
        }
        $script:gradleTasks = @($output)
    }

    return $script:gradleTasks
}

function Find-GradleTask {
    param([string[]]$Candidates)

    $tasks = Get-GradleTasks
    foreach ($candidate in $Candidates) {
        $pattern = "(^|\s|:)" + [regex]::Escape($candidate) + "(\s|$)"
        $match = $tasks | Where-Object { $_ -match $pattern } | Select-Object -First 1
        if ($null -ne $match) {
            return $candidate
        }
    }

    return $null
}

function Invoke-GradleTask {
    param(
        [string]$TaskName,
        [string]$Label
    )

    Write-Section "Running ${Label}: $TaskName"
    $output = & $script:gradleExecutable $TaskName "--console=plain" 2>&1
    if ($LASTEXITCODE -ne 0) {
        $output | ForEach-Object { Write-Host $_ }
        Fail "FAILED: $Label failed."
    }

    Write-Host "PASSED: $Label" -ForegroundColor Green
}

$qualitySteps = @(
    @{
        Label = "formatting check"
        Candidates = @("spotlessCheck", "ktlintCheck")
    },
    @{
        Label = "static analysis"
        Candidates = @("detekt", "lintDebug")
    }
)

foreach ($step in $qualitySteps) {
    $task = Find-GradleTask -Candidates $step.Candidates
    if (-not $task) {
        Fail "No Gradle task found for $($step.Label). Expected one of: $($step.Candidates -join ', ')."
    }

    Invoke-GradleTask -TaskName $task -Label $step.Label
}

if ($stagedKotlin.Count -gt 0) {
    $testTask = Find-GradleTask -Candidates @("testDebugUnitTest", "test")
    if (-not $testTask) {
        Fail "No unit test Gradle task found. Expected testDebugUnitTest or test for staged Kotlin files."
    }

    Invoke-GradleTask -TaskName $testTask -Label "unit tests"
}

Write-Host ""
Write-Host "All pre-commit checks passed." -ForegroundColor Green
exit 0
