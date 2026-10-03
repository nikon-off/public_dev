# ============================================
# 1C COM Connector Diagnostics Script
# ============================================

Write-Host "=== 1C COM Connector Diagnostics ===" -ForegroundColor Cyan
Write-Host ""

# --- 1.1 Проверка реестра ---
$registryKeys = @(
    "HKEY_CLASSES_ROOT\V83.COMConnector",
    "HKEY_CLASSES_ROOT\V83.COMConnector.1"
)

$registryFound = $false
foreach ($key in $registryKeys) {
    $value = Get-ItemProperty -Path "Registry::$key" -ErrorAction SilentlyContinue
    if ($value -ne $null) {
        Write-Host "[OK] Found: $key" -ForegroundColor Green
        Write-Host "     Default = $($value.'(Default)')" -ForegroundColor Gray
        $registryFound = $true
    } else {
        Write-Host "[MISSING] Not found: $key" -ForegroundColor Red
    }
}

if (-not $registryFound) {
    Write-Host ""
    Write-Host "*** V83.COMConnector НЕ зарегистрирован в реестре ***" -ForegroundColor Red
}

# --- 1.2 Попытка создания COM-объекта ---
Write-Host ""
Write-Host "--- Проверка создания COM-объекта ---" -ForegroundColor Yellow
try {
    $com = [Activator]::CreateInstance([Type]::GetTypeFromProgID("V83.COMConnector"))
    Write-Host "[OK] V83.COMConnector успешно создан!" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Не удалось создать V83.COMConnector: $_" -ForegroundColor Red
}

# --- 1.3 Поиск comcntr.dll на диске ---
Write-Host ""
Write-Host "--- Поиск comcntr.dll ---" -ForegroundColor Yellow

$possiblePaths = @(
    "C:\Program Files\1cv8\*",
    "C:\Program Files (x86)\1cv8\*"
)

$foundDlls = @()
foreach ($pathPattern in $possiblePaths) {
    $matches = Get-ChildItem -Path $pathPattern -Filter "comcntr.dll" -Recurse -ErrorAction SilentlyContinue
    if ($matches) {
        foreach ($dll in $matches) {
            Write-Host "[FOUND] $($dll.FullName)" -ForegroundColor Green
            $foundDlls += $dll.FullName
        }
    }
}

if ($foundDlls.Count -eq 0) {
    Write-Host "[NOT FOUND] comcntr.dll не найден в типовых путях." -ForegroundColor Red
}

# --- 1.4 Разрядность ---
$bitness = if ([Environment]::Is64BitOperatingSystem) { "64-bit" } else { "32-bit" }
$processBitness = if ([Environment]::Is64BitProcess) { "64-bit" } else { "32-bit" }
Write-Host ""
Write-Host "--- Разрядность ---" -ForegroundColor Yellow
Write-Host "OS: $bitness" -ForegroundColor Gray
Write-Host "PowerShell process: $processBitness" -ForegroundColor Gray
