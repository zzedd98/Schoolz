$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFile = Join-Path $projectRoot '.env'
$environmentExample = Join-Path $projectRoot '.env.example'

if (-not (Test-Path -LiteralPath $environmentFile)) {
    Copy-Item -LiteralPath $environmentExample -Destination $environmentFile
    Write-Host 'Created .env from the development template.'
}

& docker compose --project-directory $projectRoot --env-file $environmentFile up --build

