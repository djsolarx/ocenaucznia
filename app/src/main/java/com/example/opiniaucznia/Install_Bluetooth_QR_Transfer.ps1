$ErrorActionPreference = "Stop"
$project = "C:\apki\opiniaucznia"
$javaDir = Join-Path $project "app\src\main\java\com\example\opiniaucznia"
$mainTarget = Join-Path $javaDir "MainActivity.kt"
$activityTarget = Join-Path $javaDir "BluetoothOpinionTransferActivity.kt"
$manifestTarget = Join-Path $project "app\src\main\AndroidManifest.xml"
$sourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path

foreach ($p in @($mainTarget, $manifestTarget)) {
    if (-not (Test-Path $p)) { throw "Nie znaleziono pliku: $p" }
}
if (-not (Test-Path (Join-Path $project "app\build.gradle.kts"))) { throw "Nie znaleziono app\build.gradle.kts" }

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item $mainTarget "$mainTarget.backup_$stamp"
Copy-Item $manifestTarget "$manifestTarget.backup_$stamp"
if (Test-Path $activityTarget) { Copy-Item $activityTarget "$activityTarget.backup_$stamp" }

Copy-Item (Join-Path $sourceDir "MainActivity.kt") $mainTarget -Force
Copy-Item (Join-Path $sourceDir "BluetoothOpinionTransferActivity.kt") $activityTarget -Force

$manifest = Get-Content $manifestTarget -Raw
if ($manifest -notmatch 'android.permission.BLUETOOTH_CONNECT') {
    $permissions = @'
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
'@
    $manifest = [regex]::Replace($manifest, '(?s)(<manifest\b[^>]*>)', ('$1' + "`r`n" + $permissions), 1)
}
if ($manifest -notmatch 'BluetoothOpinionTransferActivity') {
    $activityLine = '        <activity android:name=".BluetoothOpinionTransferActivity" android:exported="false" />'
    $manifest = [regex]::Replace($manifest, '(?s)(<application\b[^>]*>)', ('$1' + "`r`n" + $activityLine), 1)
}
Set-Content -Path $manifestTarget -Value $manifest -Encoding UTF8

Write-Host "Pliki transferu skopiowane. Kopie zapasowe utworzono z sufiksem: $stamp" -ForegroundColor Green
Write-Host "Teraz uruchamiam kompilację debug..." -ForegroundColor Cyan
Push-Location $project
try { .\gradlew.bat assembleDebug } finally { Pop-Location }
