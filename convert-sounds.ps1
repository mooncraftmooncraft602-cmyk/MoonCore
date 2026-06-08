# =====================================================================
#  MoonCore - Conversion des musiques MP3 -> OGG pour le resource pack
#  Minecraft ne lit que le .ogg (Vorbis) et via un resource pack.
#
#  Prerequis : ffmpeg dans le PATH (winget install Gyan.FFmpeg
#              ou choco install ffmpeg).
#
#  Usage :
#    powershell -ExecutionPolicy Bypass -File convert-sounds.ps1
#    powershell -ExecutionPolicy Bypass -File convert-sounds.ps1 -Source "C:\chemin\vers\mp3"
#
#  Les noms de fichiers sont normalises (accents retires) avant correspondance,
#  pour eviter tout probleme d'encodage.
# =====================================================================
param(
    [string]$Source = "C:\Users\PC\Downloads\sound"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dest = Join-Path $root "resourcepack\assets\mooncore\sounds"

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Host "ffmpeg introuvable dans le PATH." -ForegroundColor Red
    Write-Host "  winget install Gyan.FFmpeg   (ou)   choco install ffmpeg" -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path $Source)) {
    Write-Host "Dossier source introuvable : $Source" -ForegroundColor Red
    exit 1
}

function Remove-Diacritics([string]$s) {
    $n = $s.Normalize([Text.NormalizationForm]::FormD)
    $sb = New-Object System.Text.StringBuilder
    foreach ($c in $n.ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($c) -ne [Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$sb.Append($c)
        }
    }
    return $sb.ToString().Normalize([Text.NormalizationForm]::FormC).ToLowerInvariant().Trim()
}

# Correspondance nom de base NORMALISE (sans accents, minuscules) -> id.
$map = @{
    "1ere place"                       = "place_1"
    "2eme place"                       = "place_2"
    "3eme place"                       = "place_3"
    "boss demoniaque"                  = "boss_demoniaque"
    "boss final (tres dangereux)"      = "boss_final"
    "boss mystique _ dragon"           = "boss_dragon"
    "boss principal (epique)"          = "boss_principal"
    "boss phase change"                = "boss_phase"
    "combat normal"                    = "combat"
    "endgame _ final area"             = "endgame"
    "event pvp"                        = "event_pvp"
    "event rare _ loot legendaire"     = "loot_legendaire"
    "event start"                      = "event_start"
    "mining _ farming"                 = "mining"
    "mort _ death screen"              = "death"
    "musique de hub _ spawn"           = "spawn"
    "musique exploration _ survie"     = "exploration"
    "zone dangereuse _ pvp zone"       = "zone_danger"
}

$groups = @{}
Get-ChildItem -Path $Source -Filter *.mp3 | ForEach-Object {
    $name = $_.BaseName
    $variant = 0
    $base = $name
    if ($name -match '^(.*) \((\d+)\)$') {
        $base = $Matches[1]
        $variant = [int]$Matches[2]
    }
    $key = Remove-Diacritics $base
    if ($map.ContainsKey($key)) {
        $id = $map[$key]
        if (-not $groups.ContainsKey($id)) { $groups[$id] = @() }
        $groups[$id] += [pscustomobject]@{ Variant = $variant; Path = $_.FullName }
    } else {
        Write-Host ("Ignore (non mappe) : {0}  [cle: {1}]" -f $name, $key) -ForegroundColor DarkYellow
    }
}

$durations = @()
foreach ($id in $groups.Keys) {
    $files = $groups[$id] | Sort-Object Variant
    $outDir = Join-Path $dest $id
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $index = 0
    foreach ($f in $files) {
        $out = Join-Path $outDir "$index.ogg"
        Write-Host ("-> {0}/{1}.ogg" -f $id, $index) -ForegroundColor Cyan
        & ffmpeg -y -loglevel error -i $f.Path -vn -c:a libvorbis -q:a 5 $out
        if (Get-Command ffprobe -ErrorAction SilentlyContinue) {
            $dur = & ffprobe -v error -show_entries format=duration -of csv=p=0 $out
            $durations += ("{0}/{1} = {2} s" -f $id, $index, [math]::Round([double]$dur))
        }
        $index++
    }
}

if ($durations.Count -gt 0) {
    $durFile = Join-Path $root "resourcepack\durations.txt"
    $durations | Sort-Object | Set-Content -Encoding utf8 $durFile
    Write-Host "Durees ecrites dans resourcepack\durations.txt." -ForegroundColor Green
}

Write-Host "Conversion terminee. Resource pack pret dans resourcepack\." -ForegroundColor Green
