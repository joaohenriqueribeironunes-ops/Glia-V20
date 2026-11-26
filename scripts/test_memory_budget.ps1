$ErrorActionPreference = 'Stop'

# Runs the host-only C++ test for the KV memory budget shared header.
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $root 'app\src\test\native\memory_budget_test.cpp'
$output = Join-Path $env:TEMP 'glia_memory_budget_test.exe'

$gpp = Get-ChildItem 'C:\Users\conta\AppData\Local\Microsoft\WinGet\Packages' -Filter 'g++.exe' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $gpp) { throw 'g++.exe not found' }

Write-Host "Compiling native memory budget test: $source"
& $gpp -std=c++17 -O2 -Wall -Wextra $source -o $output
if ($LASTEXITCODE -ne 0) { throw "native compile failed: $LASTEXITCODE" }

Write-Host 'Running native memory budget test'
& $output
if ($LASTEXITCODE -ne 0) { throw "native test failed: $LASTEXITCODE" }

Write-Host 'NATIVE_TEST_OK'
