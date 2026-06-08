# MoonCore Companion (mod client Fabric — OPTIONNEL)

Mod **client uniquement** et **facultatif** pour les serveurs MoonCore.
Il débloque, pour les joueurs **Java** qui l'installent, les outils de création avancés
(studio 2D/3D, modèles, animations). **Personne n'est obligé de l'avoir** :
- Java vanilla → jouent normalement, sans les outils avancés ;
- Bedrock (Geyser) → jouent normalement, sans les outils avancés ;
- Java + ce mod → accès aux fonctionnalités avancées.

## Comment ça marche
Le mod et le plugin se parlent via le canal de *plugin message* `mooncore:companion`.
À la connexion, le mod envoie un `HELLO` ; le plugin (module `companion`) répond `WELCOME`
avec un bitmask de capacités et retient le joueur comme « moddé ». Côté plugin, n'importe
quel module peut réserver une fonctionnalité Java-only via
`CompanionModule.hasCompanion(player)`.

Aucun impact sur les autres joueurs : un serveur non-MoonCore ignore simplement le `HELLO`.

## Construire le mod
Prérequis : JDK 21. Le projet utilise **Fabric Loom** (Gradle).

```bash
cd companion-mod
./gradlew build      # (Windows : gradlew.bat build)
```
Le `.jar` final est dans `companion-mod/build/libs/`. À déposer dans le dossier `mods/`
du client (avec **Fabric Loader** + **Fabric API**), Minecraft **1.21.1**.

> Les versions exactes (yarn, loader, fabric-api) sont dans `gradle.properties` ; ajuste-les
> au besoin depuis https://fabricmc.net/develop/ . Si tu n'as pas le wrapper Gradle,
> lance `gradle wrapper` une fois (Gradle 8.10+).

## État (Phase 1)
- ✅ Détection serveur ↔ client (HELLO/WELCOME), capacités annoncées.
- ✅ Touche **M** → écran « Studio » (placeholder).
- ⏳ Phase 2 : éditeur de texture 2D natif (souris, calques, timeline d'animation),
  puis modèles 3D et rendu d'entités custom, pilotés par le serveur via le même canal.

## Protocole (canal `mooncore:companion`)
- Client → serveur : `[0x01, protocole]` (HELLO)
- Serveur → client : `[0x02, protocole, capacités]` (WELCOME)
  - capacités (bitmask) : `0x01` studio 2D, `0x02` modèles 3D, `0x04` entités.
