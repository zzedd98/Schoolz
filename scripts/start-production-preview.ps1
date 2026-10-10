[CmdletBinding()]
param([ValidateRange(1024, 65535)][int]$Port = 8089)

$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot

Push-Location $workspace
try {
    $env:HTTP_PORT = "$Port"
    $env:ALLOWED_ORIGINS = "http://localhost:$Port,http://127.0.0.1:$Port"
    $env:FRONTEND_BUILD_CONFIGURATION = "production"

    docker compose --env-file .env.example up -d --build
    if ($LASTEXITCODE -ne 0) {
        throw "La prévisualisation de production n'a pas pu démarrer."
    }

    Write-Host ""
    Write-Host "SCHOOLZ_PRODUCTION_PREVIEW_READY http://localhost:$Port/login"
    Write-Host "Frontend : build de production sans encart ni identifiants de démonstration."
    Write-Host "Backend : données locales de recette conservées pour permettre la connexion."
} finally {
    Remove-Item Env:HTTP_PORT -ErrorAction SilentlyContinue
    Remove-Item Env:ALLOWED_ORIGINS -ErrorAction SilentlyContinue
    Remove-Item Env:FRONTEND_BUILD_CONFIGURATION -ErrorAction SilentlyContinue
    Pop-Location
}
