<#
.SYNOPSIS
  Downloads the Zork I-III stepwise walkthroughs used by the in-game "help" hints.

.DESCRIPTION
  Fetches solutions/Sols2.zip from the IF Archive and copies the Zork walkthroughs to
  audioif/src/main/assets/walkthroughs/ as "Zork I.txt", "Zork II.txt" and "Zork III.txt"
  (named after the matching story files in assets/games). That folder is git-ignored:
  the walkthroughs belong to their authors and are fetched for personal builds only,
  never committed. Rebuild the app afterwards so they are packaged into the APK.
#>
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $root 'audioif\src\main\assets\walkthroughs'
$temp = Join-Path ([IO.Path]::GetTempPath()) ("sols2-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $dest, $temp | Out-Null
try {
    $zip = Join-Path $temp 'Sols2.zip'
    Invoke-WebRequest -UseBasicParsing -Uri 'https://www.ifarchive.org/if-archive/solutions/Sols2.zip' -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $temp -Force
    $map = @{ 'Zork1' = 'Zork I.txt'; 'Zork2' = 'Zork II.txt'; 'Zork3' = 'Zork III.txt' }
    foreach ($name in $map.Keys) {
        $src = Get-ChildItem -Path $temp -Recurse -File -Filter $name | Select-Object -First 1
        if (-not $src) { throw "$name not found in Sols2.zip" }
        Copy-Item -Path $src.FullName -Destination (Join-Path $dest $map[$name]) -Force
        Write-Host "Saved $($map[$name])"
    }
} finally {
    Remove-Item -Recurse -Force $temp -ErrorAction SilentlyContinue
}
