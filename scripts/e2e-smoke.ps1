[CmdletBinding()]
param([string]$BaseUrl = "http://localhost:8088")

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd('/')

function Login-SchoolUser([string]$email, [string]$password) {
    $body = @{ tenantSlug = "ecole-demo"; email = $email; password = $password } | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri "$base/api/v1/auth/login" -ContentType "application/json" -Body $body
}

function Invoke-Protected([string]$path, [string]$token) {
    return Invoke-RestMethod -Method Get -Uri "$base$path" -Headers @{ Authorization = "Bearer $token" }
}

$health = Invoke-RestMethod -Method Get -Uri "$base/api/v1/system/status"
if ($health.status -notin @("UP", "DEGRADED")) { throw "Le backend n’est pas prêt." }

$admin = Login-SchoolUser "admin@schoolz.ma" "SchoolZDemo2026!"
$users = Invoke-Protected "/api/v1/admin/users" $admin.accessToken
$privacy = Invoke-Protected "/api/v1/privacy" $admin.accessToken
if ($users.Count -lt 1 -or $privacy.purposes.Count -lt 3) { throw "Le parcours administrateur est incomplet." }

$teacher = Login-SchoolUser "prof.math@schoolz.ma" "SchoolZTeacher2026!"
$teacherDay = Invoke-Protected "/api/v1/teacher-portal/today?date=2026-09-01" $teacher.accessToken
if (-not $teacherDay.teacher -or $teacherDay.lessons.Count -lt 1) { throw "Le parcours professeur est incomplet." }

$student = Login-SchoolUser "yasmine.eleve@schoolz.ma" "SchoolZStudent2026!"
$studentDay = Invoke-Protected "/api/v1/student-portal/dashboard?date=2026-09-01" $student.accessToken
if (-not $studentDay.student -or $studentDay.grades.Count -lt 1) { throw "Le parcours élève est incomplet." }

$familyLogin = @{ tenantSlug = "ecole-demo"; email = "parent.demo@schoolz.ma"; password = "SchoolZParent2026!" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/api/v1/public/admissions/sessions" -ContentType "application/json" -Body $familyLogin -SessionVariable familySession | Out-Null
$family = Invoke-RestMethod -Method Get -Uri "$base/api/v1/public/family-portal?date=2026-09-01" -WebSession $familySession
if ($family.children.Count -lt 2 -or -not $family.selectedStudentId) { throw "Le parcours famille est incomplet." }

foreach ($route in @("/app/dashboard", "/app/privacy", "/app/teacher", "/student", "/family")) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$base$route"
    if ($response.StatusCode -ne 200 -or $response.Content -notmatch '<app-root') {
        throw "La route SPA $route n’est pas servie correctement."
    }
}

Write-Host "E2E_SMOKE_OK admin=1 teacherLessons=$($teacherDay.lessons.Count) studentGrades=$($studentDay.grades.Count) familyChildren=$($family.children.Count)"
