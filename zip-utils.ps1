# Utilitaires d'empaquetage : zip avec separateurs "/" (obligatoire pour les packs
# Minecraft) et ecriture JSON sans BOM. A dot-sourcer : . .\zip-utils.ps1

function Write-Utf8NoBom {
    param([string]$Path, [string]$Text)
    [IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function New-PackZip {
    # Zippe le CONTENU de $SourceDir (chemins relatifs, separateur "/").
    param([string]$SourceDir, [string]$OutFile)
    Add-Type -AssemblyName System.IO.Compression | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null
    if (Test-Path $OutFile) { Remove-Item $OutFile -Force }
    $base = (Resolve-Path $SourceDir).Path.TrimEnd('\')
    $fs = [IO.File]::Open($OutFile, [IO.FileMode]::Create)
    $zip = New-Object System.IO.Compression.ZipArchive($fs, [IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem $SourceDir -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($base.Length + 1).Replace('\', '/')
            $entry = $zip.CreateEntry($rel, [IO.Compression.CompressionLevel]::Optimal)
            $es = $entry.Open()
            $in = [IO.File]::OpenRead($_.FullName)
            try { $in.CopyTo($es) } finally { $in.Close(); $es.Close() }
        }
    } finally {
        $zip.Dispose(); $fs.Close()
    }
}
