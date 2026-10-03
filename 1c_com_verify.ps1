# ============================================
# 1C COM Connector Registration Verification
# ============================================

Write-Host "=== 1C COM Connector Registration Verification ===" -ForegroundColor Cyan
Write-Host ""

# --- Шаг 1: Найти подходящую comcntr.dll ---
Write-Host "[1/3] Проверка comcntr.dll..." -ForegroundColor Yellow

$processIs64 = [Environment]::Is64BitProcess

if ($processIs64) {
    $searchPaths = @(
        "C:\Program Files\1cv8\*\bin\comcntr.dll"
    )
} else {
    $searchPaths = @(
        "C:\Program Files (x86)\1cv8\*\bin\comcntr.dll"
    )
}

$dllFiles = @()
foreach ($pattern in $searchPaths) {
    $matches = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue
    if ($matches) { $dllFiles += $matches }
}

$dllFiles = $dllFiles | Sort-Object FullName -Unique

if ($dllFiles.Count -eq 0) {
    Write-Host "[ERROR] comcntr.dll не найден!" -ForegroundColor Red
    exit 1
}

$targetDll = $dllFiles[0].FullName
Write-Host "[OK] Найден: $targetDll" -ForegroundColor Green

# --- Шаг 2: Проверка прав администратора ---
Write-Host ""
Write-Host "[2/3] Проверка прав администратора..." -ForegroundColor Yellow

$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin = $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "[WARNING] Права администратора отсутствуют." -ForegroundColor Yellow
    Write-Host "  Регистрация уже выполнена — переходим к проверке." -ForegroundColor Yellow
} else {
    Write-Host "[OK] Права администратора есть." -ForegroundColor Green
}

# --- Шаг 3: Проверка результата ---
Write-Host ""
Write-Host "[3/3] Проверка COM-объекта..." -ForegroundColor Yellow

try {
    $com = [Activator]::CreateInstance([Type]::GetTypeFromProgID("V83.COMConnector"))
    Write-Host "[OK] V83.COMConnector успешно создан!" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] COM-объект не создаётся: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Проверка завершена успешно ===" -ForegroundColor Green
