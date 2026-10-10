[CmdletBinding()]
param([string]$EnvFile = ".env")

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$resolvedEnv = Join-Path $repositoryRoot $EnvFile
if (-not (Test-Path -LiteralPath $resolvedEnv)) { $resolvedEnv = Join-Path $repositoryRoot ".env.example" }
$configuration = @{}
Get-Content -LiteralPath $resolvedEnv | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $configuration[$matches[1].Trim()] = $matches[2].Trim() }
}
$sourceDatabase = $configuration["POSTGRES_DB"]
$user = $configuration["POSTGRES_USER"]
if ([string]::IsNullOrWhiteSpace($sourceDatabase) -or [string]::IsNullOrWhiteSpace($user)) {
    throw "POSTGRES_DB et POSTGRES_USER sont requis."
}

$stamp = Get-Date -Format "yyyyMMddHHmmss"
$testDatabase = "schoolz_restore_test_$stamp"
if ($testDatabase -notmatch '^schoolz_restore_test_[0-9]{14}$') { throw "Nom de base de test non sûr." }
$containerDump = "/tmp/$testDatabase.dump"
$created = $false

Push-Location $repositoryRoot
try {
    & docker compose --env-file $resolvedEnv exec -T postgres pg_dump -U $user -d $sourceDatabase --format=custom --no-owner --no-privileges --file=$containerDump
    if ($LASTEXITCODE -ne 0) { throw "La création du dump de test a échoué." }
    & docker compose --env-file $resolvedEnv exec -T postgres createdb -U $user $testDatabase
    if ($LASTEXITCODE -ne 0) { throw "La base isolée de restauration n’a pas pu être créée." }
    $created = $true
    & docker compose --env-file $resolvedEnv exec -T postgres pg_restore -U $user -d $testDatabase --no-owner --no-privileges --exit-on-error $containerDump
    if ($LASTEXITCODE -ne 0) { throw "La restauration réelle a échoué." }

    $migration = & docker compose --env-file $resolvedEnv exec -T postgres psql -U $user -d $testDatabase -Atc "SELECT version FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1"
    $tenants = & docker compose --env-file $resolvedEnv exec -T postgres psql -U $user -d $testDatabase -Atc "SELECT count(*) FROM identity_tenant"
    $users = & docker compose --env-file $resolvedEnv exec -T postgres psql -U $user -d $testDatabase -Atc "SELECT count(*) FROM identity_user_account"
    if ([string]::IsNullOrWhiteSpace($migration) -or [int]$tenants -lt 1 -or [int]$users -lt 1) {
        throw "La vérification fonctionnelle de la base restaurée a échoué."
    }
    Write-Host "RESTORE_OK database=$testDatabase migration=$migration tenants=$tenants users=$users"
}
finally {
    if ($created -and $testDatabase -match '^schoolz_restore_test_[0-9]{14}$') {
        & docker compose --env-file $resolvedEnv exec -T postgres dropdb -U $user --force $testDatabase
    }
    & docker compose --env-file $resolvedEnv exec -T postgres rm -f $containerDump
    Pop-Location
}
