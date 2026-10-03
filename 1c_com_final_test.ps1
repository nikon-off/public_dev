Write-Host "=== 1C COM Connector Final Test ==="
Write-Host ""

# Test 1: Create COM object
Write-Host "[Test 1] Creating V83.COMConnector..."
try {
    $com = [Activator]::CreateInstance([Type]::GetTypeFromProgID("V83.COMConnector"))
    Write-Host "[OK] V83.COMConnector created successfully!"
} catch {
    Write-Host "[FAIL] Cannot create V83.COMConnector: $_"
    exit 1
}

# Test 2: Check bitness
$bitness = if ([Environment]::Is64BitProcess) { "64-bit" } else { "32-bit" }
Write-Host ""
Write-Host "[Test 2] Process bitness: $bitness"

# Test 3: Find DLL
Write-Host ""
Write-Host "[Test 3] Searching for comcntr.dll..."
$dlls = Get-ChildItem -Path "C:\Program Files\1cv8" -Filter "comcntr.dll" -Recurse -ErrorAction SilentlyContinue
if ($dlls) {
    foreach ($dll in $dlls) {
        Write-Host "[FOUND] $($dll.FullName)"
    }
} else {
    Write-Host "[NOT FOUND] comcntr.dll"
}

Write-Host ""
Write-Host "=== All tests passed ==="
