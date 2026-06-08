# =====================================================================
#  MoonCore - Generation du resource pack BEDROCK (.mcpack) pour Geyser
#  Geyser ne convertit pas automatiquement les sons custom Java -> Bedrock.
#  Ce pack Bedrock declare les memes evenements ("mooncore:*") avec les memes
#  fichiers .ogg ; depose-le dans le dossier "packs/" de Geyser : il sera envoye
#  automatiquement aux joueurs Bedrock.
#
#  Prerequis : avoir lance convert-sounds.ps1 (les .ogg doivent exister).
#  Usage : powershell -ExecutionPolicy Bypass -File build-bedrock-pack.ps1
# =====================================================================
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $root "zip-utils.ps1")
$javaSounds = Join-Path $root "resourcepack\assets\mooncore\sounds"
$out = Join-Path $root "bedrockpack"

if (-not (Test-Path $javaSounds)) {
    Write-Host "Sons introuvables ($javaSounds). Lance d'abord convert-sounds.ps1." -ForegroundColor Red
    exit 1
}

# Repart propre.
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
$destSounds = Join-Path $out "sounds\mooncore"
New-Item -ItemType Directory -Force -Path $destSounds | Out-Null

# Copie l'arborescence des .ogg et construit les definitions de sons.
$defs = [ordered]@{}
Get-ChildItem $javaSounds -Directory | ForEach-Object {
    $id = $_.Name
    $idDest = Join-Path $destSounds $id
    New-Item -ItemType Directory -Force -Path $idDest | Out-Null
    $sounds = @()
    Get-ChildItem $_.FullName -Filter *.ogg | Sort-Object Name | ForEach-Object {
        Copy-Item $_.FullName (Join-Path $idDest $_.Name) -Force
        $n = [IO.Path]::GetFileNameWithoutExtension($_.Name)
        $sounds += "sounds/mooncore/$id/$n"   # chemin relatif, sans extension
    }
    $defs["mooncore:$id"] = [ordered]@{ category = "music"; sounds = $sounds }
}

# sound_definitions.json (sans BOM)
$soundDef = [ordered]@{ format_version = "1.14.0"; sound_definitions = $defs }
Write-Utf8NoBom (Join-Path $out "sounds\sound_definitions.json") ($soundDef | ConvertTo-Json -Depth 6)

# manifest.json (UUID generes)
$manifest = [ordered]@{
    format_version = 2
    header = [ordered]@{
        name = "MoonCore Music"
        description = "Musiques custom MoonCore (zones, events, boss, victoire)"
        uuid = [guid]::NewGuid().ToString()
        version = @(1, 0, 0)
        min_engine_version = @(1, 21, 0)
    }
    modules = @(
        [ordered]@{
            type = "resources"
            description = "Sons MoonCore"
            uuid = [guid]::NewGuid().ToString()
            version = @(1, 0, 0)
        }
    )
}
Write-Utf8NoBom (Join-Path $out "manifest.json") ($manifest | ConvertTo-Json -Depth 6)

# Empaquetage .mcpack (zip avec separateurs "/").
$mcpack = Join-Path $root "MoonCore-Bedrock.mcpack"
New-PackZip -SourceDir $out -OutFile $mcpack

$size = (Get-Item $mcpack).Length / 1MB
Write-Host ("Pack Bedrock genere : {0} ({1:N1} Mo)" -f $mcpack, $size) -ForegroundColor Green
Write-Host "Depose ce .mcpack dans le dossier 'packs/' de Geyser, puis redemarre Geyser." -ForegroundColor Green
