# =====================================================================
#  MoonCore - Empaquetage du resource pack JAVA (.zip)
#  Zippe le contenu de resourcepack\ avec des separateurs "/" (obligatoire).
#  Usage : powershell -ExecutionPolicy Bypass -File build-java-pack.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $root "zip-utils.ps1")

$src = Join-Path $root "resourcepack"
$zip = Join-Path $root "MoonCore-ResourcePack.zip"
if (-not (Test-Path (Join-Path $src "assets"))) {
    Write-Host "Sons manquants : lance d'abord convert-sounds.ps1." -ForegroundColor Red
    exit 1
}
New-PackZip -SourceDir $src -OutFile $zip
$sha = (Get-FileHash $zip -Algorithm SHA1).Hash.ToLower()
$size = (Get-Item $zip).Length / 1MB
Write-Host ("Pack Java : {0} ({1:N1} Mo)" -f $zip, $size) -ForegroundColor Green
Write-Host ("SHA1 : {0}" -f $sha) -ForegroundColor Green
Write-Host "server.properties -> resource-pack-sha1=$sha" -ForegroundColor Yellow
