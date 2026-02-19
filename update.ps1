param(
    [string]$Version,
    [string]$Desc
)

$ErrorActionPreference = "Stop"
$projectRoot = "C:\Users\Steve\Documents\SonicPulse"
$backupDir = "$projectRoot\_backups"
$versionFile = "$projectRoot\version.txt"
$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$backupName = "${timestamp}_v${Version}"

# 1. Build Project
Write-Host "[:] Building Project..." -ForegroundColor Yellow
cmd /c "gradlew.bat clean build"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Build Failed! Aborting run." -ForegroundColor Red
    exit 1
}

# 2. Run Client (Blocks until MC closes)
Write-Host "[:] Starting Client... (Script will pause until you exit Minecraft)" -ForegroundColor Cyan
cmd /c "gradlew.bat runClient"

# 3. Prompt for Backup
Write-Host "`n"
$response = Read-Host "[?] Minecraft closed. Did it work? Backup this version? (y/n)"
if ($response -ne "y") {
    Write-Host "[:] Backup skipped. No changes saved." -ForegroundColor Gray
    exit 0
}

# 4. Perform Backup
Write-Host "[:] Backing up verified code..." -ForegroundColor Magenta

# A. Update Version Log (Prepend)
$logEntry = "v$Version | $(Get-Date -Format 'yyyy-MM-dd HH:mm') | $Desc"
$currentLog = Get-Content -Path $versionFile -ErrorAction SilentlyContinue
$newLog = @($logEntry) + $currentLog
Set-Content -Path $versionFile -Value $newLog
Write-Host "    [+] Updated version.txt" -ForegroundColor Green

# B. Backup Source Code
$srcBackup = "$backupDir\$backupName-src.zip"
Compress-Archive -Path "$projectRoot\src" -DestinationPath $srcBackup -Force
Write-Host "    [+] Source backed up to $srcBackup" -ForegroundColor Green

# C. Archive JAR
if (Test-Path "$projectRoot\build\libs\*.jar") {
    Copy-Item "$projectRoot\build\libs\*.jar" -Destination "$backupDir\$backupName.jar"
    Write-Host "    [+] JAR archived to $backupDir\$backupName.jar" -ForegroundColor Green
}

Write-Host "[:] SUCCESS: Version v$Version secured." -ForegroundColor Cyan
