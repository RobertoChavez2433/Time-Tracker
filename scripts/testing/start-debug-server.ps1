param(
    [int]$Port = 3947
)

$ErrorActionPreference = "Stop"

$repoRootOutput = @(& git rev-parse --show-toplevel)
if ($LASTEXITCODE -ne 0) {
    throw "Run this script from inside the Time Tracker git repository."
}

$repoRoot = (($repoRootOutput | Select-Object -First 1) -as [string]).Trim()
$serverScript = Join-Path $repoRoot "tools\debug-server\server.js"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js is required to run the debug log server."
}

try {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2 -ErrorAction Stop
    if ($health.status -eq "ok") {
        Write-Host "Debug log server already running on port $Port."
        return
    }
} catch {
    # Server is not running yet.
}

Start-Process -FilePath "node" -ArgumentList @($serverScript, "--port", $Port) -WindowStyle Hidden
Start-Sleep -Seconds 1
Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 5 -ErrorAction Stop | Out-Null
Write-Host "Debug log server running on http://127.0.0.1:$Port."
