param()

$ErrorActionPreference = "Stop"

$repoRootOutput = @(& git rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) {
    Write-Host "Failed to resolve repository root." -ForegroundColor Red
    exit 1
}

$repoRoot = (($repoRootOutput | Select-Object -First 1) -as [string]).Trim()
$violations = New-Object System.Collections.Generic.List[string]

function Get-RepoRelativePath {
    param([string]$Path)

    $resolved = (Resolve-Path -LiteralPath $Path).Path
    return [System.IO.Path]::GetRelativePath($repoRoot, $resolved).Replace("\", "/")
}

function Add-Violation {
    param(
        [string]$Path,
        [int]$LineNumber,
        [string]$Message
    )

    if ($LineNumber -gt 0) {
        $violations.Add("${Path}:${LineNumber}: ${Message}")
    } else {
        $violations.Add("${Path}: ${Message}")
    }
}

function Get-KotlinFileLimit {
    param([string]$RelativePath)

    if ($RelativePath -match "/src/(test|androidTest)/") {
        return 360
    }
    if ($RelativePath -match "^feature/.+/src/main/.+Route\.kt$") {
        return 320
    }
    return 260
}

function Get-KotlinImportLimit {
    param([string]$RelativePath)

    if ($RelativePath -match "/src/(test|androidTest)/") {
        return 32
    }
    if ($RelativePath -match "^feature/.+/src/main/.+Route\.kt$") {
        return 36
    }
    return 28
}

function Test-IgnoredPath {
    param([string]$Path)

    return $Path -match "[\\/](build|generated)[\\/]" -or
        $Path -match "[\\/]\.gradle[\\/]" -or
        $Path -match "[\\/]\.idea[\\/]"
}

function Test-ForbiddenMainImport {
    param(
        [string]$RelativePath,
        [string]$Line
    )

    if ($Line -notmatch "^\s*import\s+(.+)") {
        return $null
    }

    $import = $Matches[1]
    if ($RelativePath -notmatch "/src/main/") {
        return $null
    }

    if ($RelativePath -match "^feature/" -and $import -match "^com\.robertochavez\.timetracker\.core\.(database|datastore|testing)\b") {
        return "feature production code must depend on contracts, not persistence or test modules."
    }
    if ($RelativePath -match "^feature/" -and $import -match "^androidx\.(room|datastore)\b") {
        return "feature production code must not import persistence framework APIs."
    }
    if ($RelativePath -match "^core/common/" -and $import -match "^(android|androidx|com\.google\.android\.gms|dagger|javax\.inject)\b") {
        return ":core:common must stay plain Kotlin/JVM."
    }
    if ($RelativePath -match "^core/(database|datastore|location|notifications)/" -and $import -match "^com\.robertochavez\.timetracker\.feature\.") {
        return "core modules must not import feature modules."
    }
    if ($RelativePath -match "^core/(database|datastore|location|notifications)/" -and $import -match "^androidx\.compose\b") {
        return "core implementation modules must not import Compose UI APIs."
    }
    if ($RelativePath -notmatch "^core/testing/" -and $import -match "^com\.robertochavez\.timetracker\.core\.testing\b") {
        return ":core:testing is for test source sets only."
    }

    return $null
}

function Test-KotlinPackagePath {
    param(
        [string]$RelativePath,
        [string[]]$Lines
    )

    $match = [regex]::Match($RelativePath, "^(?<module>.+?)/src/(?<sourceSet>main|test|androidTest)/(kotlin|java)/(?<sourcePath>.+)\.kt$")
    if (-not $match.Success) {
        return
    }

    $packageName = $null
    $packageLine = 0
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match "^package\s+([A-Za-z0-9_.]+)") {
            $packageName = $Matches[1]
            $packageLine = $index + 1
            break
        }
    }

    if (-not $packageName) {
        Add-Violation -Path $RelativePath -LineNumber 1 -Message "Kotlin files must declare a package."
        return
    }

    $sourcePath = $match.Groups["sourcePath"].Value
    $actualDirectory = $sourcePath -replace "/[^/]+$", ""
    $expectedDirectory = $packageName.Replace(".", "/")
    if ($actualDirectory -ne $expectedDirectory) {
        Add-Violation -Path $RelativePath -LineNumber $packageLine -Message "package path must match source directory '${expectedDirectory}'."
    }
}

function Test-GradleDependencies {
    $gradleFiles = @()
    foreach ($rootName in @("app", "core", "feature")) {
        $root = Join-Path -Path $repoRoot -ChildPath $rootName
        if (Test-Path -LiteralPath $root) {
            $gradleFiles += Get-ChildItem -LiteralPath $root -Recurse -Filter "build.gradle.kts" -File
        }
    }

    foreach ($file in $gradleFiles) {
        $relativePath = Get-RepoRelativePath -Path $file.FullName
        $lines = @(Get-Content -LiteralPath $file.FullName)
        for ($index = 0; $index -lt $lines.Count; $index++) {
            $line = $lines[$index]
            $lineNumber = $index + 1

            if ($relativePath -match "^feature/" -and $line -match '^\s*(api|implementation|compileOnly|runtimeOnly)\(project\(":core:(database|datastore|testing)"\)\)') {
                Add-Violation -Path $relativePath -LineNumber $lineNumber -Message "feature production dependencies must not point at persistence or test modules."
            }
            if ($relativePath -match "^core/" -and $line -match 'project\(":feature:') {
                Add-Violation -Path $relativePath -LineNumber $lineNumber -Message "core modules must not depend on feature modules."
            }
            if ($relativePath -notmatch "^core/testing/" -and $line -match '^\s*(api|implementation|compileOnly|runtimeOnly)\(project\(":core:testing"\)\)') {
                Add-Violation -Path $relativePath -LineNumber $lineNumber -Message ":core:testing may only be used from test dependency configurations."
            }
            if ($relativePath -match "^core/common/" -and $line -match "libs\.(activity|compose|coroutines\.android|datastore|hilt|navigation|play\.services|room|work)") {
                Add-Violation -Path $relativePath -LineNumber $lineNumber -Message ":core:common must stay free of Android framework/toolkit dependencies."
            }
        }
    }
}

$kotlinFiles = @()
foreach ($rootName in @("app", "core", "feature")) {
    $root = Join-Path -Path $repoRoot -ChildPath $rootName
    if (Test-Path -LiteralPath $root) {
        $kotlinFiles += Get-ChildItem -LiteralPath $root -Recurse -Filter "*.kt" -File |
            Where-Object { -not (Test-IgnoredPath -Path $_.FullName) }
    }
}

foreach ($file in $kotlinFiles) {
    $relativePath = Get-RepoRelativePath -Path $file.FullName
    $lines = @(Get-Content -LiteralPath $file.FullName)
    $lineLimit = Get-KotlinFileLimit -RelativePath $relativePath
    if ($lines.Count -gt $lineLimit) {
        Add-Violation -Path $relativePath -LineNumber 1 -Message "file has $($lines.Count) lines; LIMP limit is ${lineLimit}."
    }

    $importCount = @($lines | Where-Object { $_ -match "^\s*import\s+" }).Count
    $importLimit = Get-KotlinImportLimit -RelativePath $relativePath
    if ($importCount -gt $importLimit) {
        Add-Violation -Path $relativePath -LineNumber 1 -Message "file has ${importCount} imports; LIMP limit is ${importLimit}."
    }

    Test-KotlinPackagePath -RelativePath $relativePath -Lines $lines

    for ($index = 0; $index -lt $lines.Count; $index++) {
        $message = Test-ForbiddenMainImport -RelativePath $relativePath -Line $lines[$index]
        if ($message) {
            Add-Violation -Path $relativePath -LineNumber ($index + 1) -Message $message
        }
    }
}

Test-GradleDependencies

if ($violations.Count -gt 0) {
    Write-Host "LIMP policy violations found:" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    exit 1
}

Write-Host "PASSED: LIMP policies" -ForegroundColor Green
