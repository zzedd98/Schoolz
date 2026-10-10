[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8088",
    [int]$Requests = 240,
    [int]$Concurrency = 12,
    [int]$TargetRps = 24
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http
if ($Requests -lt 1 -or $Requests -gt 10000) { throw "Requests doit être compris entre 1 et 10000." }
if ($Concurrency -lt 1 -or $Concurrency -gt 100) { throw "Concurrency doit être compris entre 1 et 100." }
if ($TargetRps -lt 1 -or $TargetRps -gt 30) { throw "TargetRps doit être compris entre 1 et 30 pour respecter le proxy local." }
$base = $BaseUrl.TrimEnd('/')
$loginBody = @{ tenantSlug = "ecole-demo"; email = "admin@schoolz.ma"; password = "SchoolZDemo2026!" } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $loginBody

$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(10)
$client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $login.accessToken)
$uri = "$base/api/v1/students/page?page=0&size=40"
$failures = 0
$statuses = @{}
$latencies = [System.Collections.Generic.List[double]]::new()
$total = [System.Diagnostics.Stopwatch]::StartNew()
try {
    for ($offset = 0; $offset -lt $Requests; $offset += $Concurrency) {
        $count = [Math]::Min($Concurrency, $Requests - $offset)
        $batch = [System.Collections.Generic.List[System.Threading.Tasks.Task[System.Net.Http.HttpResponseMessage]]]::new()
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        for ($index = 0; $index -lt $count; $index++) { $batch.Add($client.GetAsync($uri)) }
        [System.Threading.Tasks.Task]::WaitAll($batch.ToArray())
        $watch.Stop()
        $perRequest = $watch.Elapsed.TotalMilliseconds
        foreach ($task in $batch) {
            $latencies.Add($perRequest)
            $status = [int]$task.Result.StatusCode
            if (-not $statuses.ContainsKey($status)) { $statuses[$status] = 0 }
            $statuses[$status]++
            if (-not $task.Result.IsSuccessStatusCode) { $failures++ }
            $task.Result.Dispose()
        }
        $targetBatchMilliseconds = 1000.0 * $count / $TargetRps
        $remainingMilliseconds = $targetBatchMilliseconds - $watch.Elapsed.TotalMilliseconds
        if ($remainingMilliseconds -gt 0) { Start-Sleep -Milliseconds ([int][Math]::Ceiling($remainingMilliseconds)) }
    }
}
finally {
    $total.Stop()
    $client.Dispose()
}

$ordered = $latencies | Sort-Object
$p95Index = [Math]::Min($ordered.Count - 1, [Math]::Floor($ordered.Count * 0.95))
$p95 = $ordered[$p95Index]
$rps = $Requests / $total.Elapsed.TotalSeconds
$statusSummary = ($statuses.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name):$($_.Value)" }) -join ","
Write-Host ("LOAD_RESULT requests={0} concurrency={1} targetRps={2} failures={3} statuses={4} durationMs={5:N0} rps={6:N1} batchP95Ms={7:N0}" -f $Requests, $Concurrency, $TargetRps, $failures, $statusSummary, $total.Elapsed.TotalMilliseconds, $rps, $p95)
if ($failures -gt 0) { throw "$failures requêtes ont échoué." }
