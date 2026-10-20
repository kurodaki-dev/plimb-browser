<#
.SYNOPSIS
    Construit Plimb Browser pour Windows : une distribution Firefox portable.

.DESCRIPTION
    1. Telecharge Firefox 64 bits (canal stable, francais) depuis Mozilla.
    2. Extrait l archive.
    3. Injecte la configuration Plimb : distribution/policies.json (add-ons
       Mozilla preinstalles, telemetrie coupee), le profil (user.js) et nos
       propres extensions WebExtensions compilees en .xpi.
    4. Produit dist-electron/Plimb-Browser-Windows-Portable.zip

.NOTES
    Lancer depuis la racine du depot :  pwsh -File windows\build.ps1
    Aucune dependance npm : ce build ne demande que PowerShell 5.1+.
#>

$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$outputDir = Join-Path $root 'build-output'
$packageName = 'Plimb Browser'
$packageDir = Join-Path $outputDir $packageName
$distDir = Join-Path $root 'dist-electron'
$archivePath = Join-Path $distDir 'Plimb-Browser-Windows-Portable.zip'

$firefoxUrl = 'https://download.mozilla.org/?product=firefox-latest-ssl&os=win64&lang=fr'
$tempRoot = if ($env:RUNNER_TEMP) { $env:RUNNER_TEMP } else { [System.IO.Path]::GetTempPath() }
$firefoxZip = Join-Path $tempRoot 'firefox-win64.zip'

Write-Host 'Plimb Browser (Windows) - distribution Firefox portable'
Write-Host 'Telechargement de Firefox...'
$previousProgress = $ProgressPreference
$ProgressPreference = 'SilentlyContinue'
try {
    Invoke-WebRequest -Uri $firefoxUrl -OutFile $firefoxZip -UserAgent 'PlimbBrowser/1.0'
}
finally {
    $ProgressPreference = $previousProgress
}

if (Test-Path $outputDir) {
    Remove-Item $outputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

Write-Host 'Extraction de Firefox...'
Expand-Archive -Path $firefoxZip -DestinationPath $packageDir -Force

if (-not (Test-Path (Join-Path $packageDir 'firefox.exe'))) {
    throw "firefox.exe introuvable apres extraction."
}

# ---- configuration d entreprise (add-ons Mozilla, telemetrie) -------------
Copy-Item -Path (Join-Path $root 'distribution') `
    -Destination (Join-Path $packageDir 'distribution') -Recurse -Force

# ---- profil portable ------------------------------------------------------
$profileDir = Join-Path $packageDir 'profile'
New-Item -ItemType Directory -Path $profileDir -Force | Out-Null
Copy-Item -Path (Join-Path $root 'profile' 'user.js') `
    -Destination (Join-Path $profileDir 'user.js') -Force

# ---- nos extensions WebExtensions, compilees en .xpi ----------------------
$extensionsSource = Join-Path $root 'extensions'
$extensionsTarget = Join-Path $profileDir 'extensions'
New-Item -ItemType Directory -Path $extensionsTarget -Force | Out-Null

$registry = @()
$installDate = [int64][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

foreach ($folder in Get-ChildItem -Path $extensionsSource -Directory) {
    $manifestPath = Join-Path $folder.FullName 'manifest.json'
    if (-not (Test-Path $manifestPath)) { continue }

    $manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json
    $geckoSettings = $manifest.browser_specific_settings.gecko
    $extensionId = 'plimb-extension@plimb.browser'
    if ($geckoSettings -and $geckoSettings.id) {
        $extensionId = [string]$geckoSettings.id
    }

    $files = Get-ChildItem -Path $folder.FullName -File | ForEach-Object { $_.FullName }
    if (-not $files -or $files.Count -eq 0) { continue }

    $xpiPath = Join-Path $extensionsTarget ($extensionId + '.xpi')
    if (Test-Path $xpiPath) { Remove-Item $xpiPath -Force }
    Compress-Archive -Path $files -DestinationPath $xpiPath -Force

    $registry += [ordered]@{
        id                         = $extensionId
        syncGUID                    = '{' + [guid]::NewGuid().ToString() + '}'
        version                     = [string]$manifest.version
        type                        = 'extension'
        loader                      = $null
        location                    = 'app-profile'
        path                        = 'extensions/' + $extensionId + '.xpi'
        active                      = $true
        visible                     = $true
        userDisabled                = $false
        appDisabled                 = $false
        installTelemetryDiscarded   = $false
        developer                   = $false
        foreignInstall              = $false
        installDate                 = $installDate
    }
}

if ($registry.Count -gt 0) {
    $json = $registry | ConvertTo-Json -Depth 6
    # Sans BOM : Firefox refuse un extensions.json qui commence par un BOM.
    [System.IO.File]::WriteAllText(
        (Join-Path $profileDir 'extensions.json'),
        $json,
        (New-Object System.Text.UTF8Encoding($false))
    )
    Write-Host "$($registry.Count) extension(s) Plimb integree(s) au profil."
}

# ---- lanceur + lisez-moi --------------------------------------------------
Copy-Item -Path (Join-Path $root 'Plimb Browser.cmd') `
    -Destination (Join-Path $packageDir 'Plimb Browser.cmd') -Force
Copy-Item -Path (Join-Path $root 'README.md') `
    -Destination (Join-Path $packageDir 'LISEZMOI.txt') -Force

# ---- archive finale -------------------------------------------------------
New-Item -ItemType Directory -Path $distDir -Force | Out-Null
if (Test-Path $archivePath) { Remove-Item $archivePath -Force }
Compress-Archive -Path $packageDir -DestinationPath $archivePath -Force

Write-Host "Archive creee : $archivePath"
Get-ChildItem -Path $distDir | Select-Object FullName, Length | Format-Table -AutoSize
