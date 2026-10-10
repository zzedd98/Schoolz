[CmdletBinding()]
param(
    [string]$EnvFile = ".env",
    [string]$OutputDirectory = "backups",
    [switch]$SkipObjects
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnv = Join-Path $repositoryRoot $EnvFile
if (-not (Test-Path -LiteralPath $resolvedEnv)) {
    $resolvedEnv = Join-Path $repositoryRoot ".env.example"
}
$resolvedOutput = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputDirectory))
if (-not $resolvedOutput.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Le dossier de sauvegarde doit rester dans le projet SchoolZ."
}
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null

$configuration = @{}
Get-Content -LiteralPath $resolvedEnv | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $configuration[$matches[1].Trim()] = $matches[2].Trim() }
}
$database = $configuration["POSTGRES_DB"]
$user = $configuration["POSTGRES_USER"]
if ([string]::IsNullOrWhiteSpace($database) -or [string]::IsNullOrWhiteSpace($user)) {
    throw "POSTGRES_DB et POSTGRES_USER sont requis."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$databaseName = "schoolz-postgres-$stamp.dump"
$databaseTarget = Join-Path $resolvedOutput $databaseName
$containerTemp = "/tmp/$databaseName"

Push-Location $repositoryRoot
try {
    & docker compose --env-file $resolvedEnv exec -T postgres pg_dump -U $user -d $database --format=custom --no-owner --no-privileges --file=$containerTemp
    if ($LASTEXITCODE -ne 0) { throw "pg_dump a échoué." }
    & docker compose --env-file $resolvedEnv cp "postgres:$containerTemp" $databaseTarget
    if ($LASTEXITCODE -ne 0) { throw "La copie de la sauvegarde PostgreSQL a échoué." }
    & docker compose --env-file $resolvedEnv exec -T postgres rm -f $containerTemp

    $objectTarget = $null
    if (-not $SkipObjects) {
        $objectName = "schoolz-objects-$stamp.tar.gz"
        $objectTarget = Join-Path $resolvedOutput $objectName
        $objectTemp = "/tmp/$objectName"
        & docker compose --env-file $resolvedEnv exec -T minio sh -c "tar -czf $objectTemp -C /data ."
        if ($LASTEXITCODE -ne 0) { throw "La sauvegarde du stockage objet a échoué." }
        & docker compose --env-file $resolvedEnv cp "minio:$objectTemp" $objectTarget
        if ($LASTEXITCODE -ne 0) { throw "La copie de la sauvegarde objet a échoué." }
        & docker compose --env-file $resolvedEnv exec -T minio rm -f $objectTemp
    }

    $manifest = [ordered]@{
        createdAt = (Get-Date).ToUniversalTime().ToString("o")
        database = [ordered]@{ file = $databaseName; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $databaseTarget).Hash.ToLowerInvariant() }
        objects = if ($objectTarget) { [ordered]@{ file = Split-Path -Leaf $objectTarget; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $objectTarget).Hash.ToLowerInvariant() } } else { $null }
        redis = "ephemeral-not-backed-up"
        envFile = Split-Path -Leaf $resolvedEnv
    }
    $manifestPath = Join-Path $resolvedOutput "schoolz-backup-$stamp.json"
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8
    Write-Host "Sauvegarde créée : $databaseTarget"
    Write-Host "Manifeste : $manifestPath"
}
finally {
    Pop-Location
}
