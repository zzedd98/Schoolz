$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

Write-Host 'Verifying backend...'
& (Join-Path $projectRoot 'backend\mvnw.cmd') -f (Join-Path $projectRoot 'backend\pom.xml') verify

Write-Host 'Verifying frontend...'
& npm --prefix (Join-Path $projectRoot 'frontend') run check

Write-Host 'Validating Docker Compose...'
& docker compose --project-directory $projectRoot --env-file (Join-Path $projectRoot '.env.example') config --quiet

Write-Host 'SchoolZ verification passed through Milestone 51.'
