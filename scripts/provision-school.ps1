[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9-]{2,79}$')]
    [string]$TenantSlug,

    [Parameter(Mandatory = $true)]
    [ValidateLength(1, 180)]
    [string]$SchoolName,

    [Parameter(Mandatory = $true)]
    [ValidateLength(1, 180)]
    [string]$OwnerName,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[^@\s]+@[^@\s]+\.[^@\s]+$')]
    [string]$OwnerEmail,

    [ValidateSet('fr-MA', 'ar-MA')]
    [string]$Locale = 'fr-MA',

    [string]$CampusName = 'Campus principal',
    [string]$City = '',

    [ValidatePattern('^[A-Za-z]{2}$')]
    [string]$CountryCode = 'MA',

    [string]$Timezone = 'Africa/Casablanca',
    [string]$IdempotencyKey = '',
    [string]$EnvironmentFile = '.env.production'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $projectRoot 'compose.prod.yaml'
$environmentPath = Join-Path $projectRoot $EnvironmentFile

if (-not (Test-Path -LiteralPath $environmentPath)) {
    throw "Fichier d'environnement introuvable : $environmentPath"
}

if ([string]::IsNullOrWhiteSpace($IdempotencyKey)) {
    $IdempotencyKey = "school:$($TenantSlug.ToLowerInvariant()):v1"
}

$payload = @{
    idempotencyKey = $IdempotencyKey
    tenantSlug = $TenantSlug.ToLowerInvariant()
    schoolName = $SchoolName
    ownerName = $OwnerName
    ownerEmail = $OwnerEmail.ToLowerInvariant()
    locale = $Locale
    campusName = $CampusName
    city = $City
    countryCode = $CountryCode.ToUpperInvariant()
    timezone = $Timezone
} | ConvertTo-Json -Compress

$payloadBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload))
$containerCommand = @'
set -eu
payload_file=/tmp/schoolz-provisioning.json
trap 'rm -f "$payload_file"' EXIT
printf '%s' "$SCHOOLZ_PROVISION_BODY_B64" | base64 -d > "$payload_file"
wget -qO- \
  --header='Content-Type: application/json' \
  --header="X-SchoolZ-Provisioning-Key: $SCHOOLZ_PROVISIONING_KEY" \
  --post-file="$payload_file" \
  http://127.0.0.1:8080/api/v1/platform/provisioning/schools
'@

$dockerArguments = @(
    'compose',
    '--env-file', $environmentPath,
    '-f', $composeFile,
    'exec', '-T',
    '-e', "SCHOOLZ_PROVISION_BODY_B64=$payloadBase64",
    'backend',
    'sh', '-c', $containerCommand
)

$rawResponse = & docker @dockerArguments
if ($LASTEXITCODE -ne 0) {
    throw 'Le provisionnement a échoué. Vérifiez que la plateforme est démarrée et consultez les journaux du backend.'
}

$result = ($rawResponse -join "`n") | ConvertFrom-Json
Write-Host ''
Write-Host "École prête : $($result.schoolName)" -ForegroundColor Green
Write-Host "Identifiant établissement : $($result.tenantSlug)"
Write-Host "Administrateur : $($result.ownerEmail)"
Write-Host "Création nouvelle : $($result.created)"
if ($result.activationUrl) {
    Write-Host ''
    Write-Host "Lien d'activation à transmettre à l'administrateur :" -ForegroundColor Yellow
    Write-Host $result.activationUrl
} else {
    Write-Host "Le compte administrateur est déjà actif."
}
