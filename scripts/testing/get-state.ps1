param(
    [int]$StatePort = 4958,
    [string]$RunId = "manual",
    [string]$ActorId = "local"
)

$ErrorActionPreference = "Stop"

$query = "runId=$([System.Uri]::EscapeDataString($RunId))&actorId=$([System.Uri]::EscapeDataString($ActorId))"
$uri = "http://127.0.0.1:$StatePort/testing/state?$query"
Invoke-RestMethod -Uri $uri -Method Get -TimeoutSec 10
