# MoonCore — Resource pack (musiques)

Minecraft ne lit pas les `.mp3` et un plugin ne peut pas jouer de fichiers audio directement :
les musiques custom passent **obligatoirement** par un resource pack (`.ogg` + `sounds.json`),
envoyé aux joueurs par le serveur. Ce dossier est ce resource pack.

## 1) Convertir les MP3 en OGG
Prérequis : `ffmpeg` dans le PATH.
```
winget install Gyan.FFmpeg      # ou : choco install ffmpeg
```
Puis, à la racine du projet :
```
powershell -ExecutionPolicy Bypass -File convert-sounds.ps1
```
Le script lit `C:\Users\PC\Downloads\sound`, convertit et range les fichiers dans
`resourcepack/assets/mooncore/sounds/<id>/<n>.ogg` (déjà référencés par `sounds.json`),
et écrit `durations.txt` (pour régler `length-seconds` dans `src/main/resources/tracks.yml`).

## 2) Empaqueter le resource pack
Zippe le **contenu** de ce dossier (le `.zip` doit contenir `pack.mcmeta` et `assets/` à la
racine, PAS le dossier `resourcepack/` lui-même).

## 3) Distribuer aux joueurs
Option A — vanilla (`server.properties`) :
```
resource-pack=https://URL/du/pack.zip
resource-pack-sha1=<sha1 du zip>
require-resource-pack=true
```
Option B — un plugin de resource pack (ForceResourcePack, etc.).

## 4) Mapping son → musique
- `tracks.yml` (dans le plugin) associe un id de piste à une clé `mooncore:*`.
- `modules/audio.yml` mappe events/boss/zones par défaut.
- En jeu : `/moon audio zone settrack <track>`, `/moon loop ...`, etc.

## Bedrock (Geyser/Floodgate) — pack dédié
Geyser ne convertit pas automatiquement les sons custom Java en Bedrock. Un **pack Bedrock**
est donc fourni : `MoonCore-Bedrock.mcpack` (généré par `build-bedrock-pack.ps1`).

Déploiement Geyser :
1. Place `MoonCore-Bedrock.mcpack` dans le dossier `packs/` de Geyser
   (`plugins/Geyser-Spigot/packs/` en général).
2. Redémarre Geyser (ou `/geyser reload`).
Geyser l'enverra automatiquement aux joueurs Bedrock ; les mêmes clés `mooncore:*` y sont
définies (`sounds/sound_definitions.json`), donc les `playsound` transmis par Geyser jouent.

Alternative/repli : définir `bedrock-sound` (un son vanilla) par piste dans `tracks.yml` —
le plugin l'utilise automatiquement pour les joueurs détectés Bedrock (UUID Floodgate).

> Régénérer les deux packs après une nouvelle conversion :
> `build-java-pack.ps1` (Java .zip) et `build-bedrock-pack.ps1` (Bedrock .mcpack).
> Les zips utilisent des séparateurs `/` (obligatoire) et des JSON sans BOM.

## Événements sonores disponibles (clés `mooncore:*`)
place_1, place_2, place_3, boss_demoniaque, boss_final, boss_dragon, boss_principal,
boss_phase, combat, endgame, event_pvp, loot_legendaire, event_start, mining, death,
spawn, exploration, zone_danger.
