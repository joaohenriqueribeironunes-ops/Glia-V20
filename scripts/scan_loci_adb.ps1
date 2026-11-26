param(
    [string] $Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string] $OutDir = "$env:TEMP\opencode\loci-scan",
    [string] $PackagePattern = "loci"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Adb)) {
    throw "adb nao encontrado: $Adb"
}

$deviceLines = & $Adb devices -l
$deviceIds = @()
foreach ($line in $deviceLines) {
    if ($line -match '^(\S+)\s+device\b') { $deviceIds += $Matches[1] }
}
if ($deviceIds.Count -lt 1) {
    throw "Nenhum device ADB autorizado. Conecte o celular via USB e aceite a autorizacao de depuracao."
}

$packages = @()
$pmLines = & $Adb shell pm list packages
foreach ($line in $pmLines) {
    if ($line -match '^package:(\S+)$') { $packages += $Matches[1] }
}

$matchesPkgs = @($packages | Where-Object { $_ -match $PackagePattern })
if ($matchesPkgs.Count -lt 1) {
    Write-Host "Nenhum pacote com '$PackagePattern'. Pacotes candidatos:"
    $packages | Where-Object { $_ -match 'assistant|voice|glia|iai|speech' } | ForEach-Object { Write-Host "  $_" }
    exit 2
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
foreach ($pkg in $matchesPkgs) {
    Write-Host "Pacote: $pkg"
    $safe = $pkg -replace '[^\w.-]', '_'
    $pkgDir = Join-Path $OutDir $safe
    New-Item -ItemType Directory -Force -Path $pkgDir | Out-Null

    $pathLines = & $Adb shell pm path $pkg
    foreach ($line in $pathLines) {
        if ($line -match '^package:(\S+)$') {
            $remote = $Matches[1]
            $name = Split-Path -Leaf $remote
            $local = Join-Path $pkgDir $name
            Write-Host "  pull $remote -> $local"
            & $Adb pull $remote $local | Out-Null
        }
    }

    $dump = Join-Path $pkgDir "dumpsys-package.txt"
    & $Adb shell dumpsys package $pkg > $dump
    Write-Host "  dump: $dump"
}

Write-Host "SCAN_OK=$OutDir"
