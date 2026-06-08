# MoonCore — AudioManager : rapport

Module `audio` ajouté à MoonCore sans modifier la logique des autres systèmes (intégration
via classes publiques `Zone`/`RegionSelection`/`ZoneIndex` et via l'EventBus). Build vert,
tests verts (dont `AudioPriorityResolverTest`).

## Structure livrée
`Track` · `TrackManager` · `AudioSource` · `ResolvedAudio` · `AudioPriorityResolver` (pur) ·
`AudioStateManager` · `AudioData` · `LoopManager` · `ZoneAudioManager` · `EventAudioManager` ·
`AudioManagerModule` · `AudioListener` · commandes `/moon audio` et `/moon loop`.
Ressources : `tracks.yml`, `modules/audio.yml`, `audio-data.yml` (runtime).

## Fonctionnalités
- **Tracks** configurables (sound, volume, pitch, loop, fadeIn/Out, length, repli Bedrock).
- **Zones audio** (cuboïdes) avec piste associée : `/moon audio zone create|setpos1|setpos2|settrack|delete|list`.
- **Déclencheurs auto** via EventBus : event start → musique event ; boss spawn → musique boss ;
  changement de phase → intensification ; boss vaincu → musique victoire (temporisée) ;
  (jingle reward : brancher `RewardAction.SOUND`/un event reward — voir améliorations).
- **Loops forcés** persistants : `/moon loop all|player|stop|info`, sauvegardés dans
  `audio-data.yml` → survivent mort / téléport / reconnexion ; le loop global s'applique
  automatiquement aux nouveaux joueurs (à la connexion).
- **Priorité** : GLOBAL_LOOP > PLAYER_LOOP > EVENT > ZONE > DEFAULT (`AudioPriorityResolver`).
- **Anti-superposition** : on coupe la piste précédente avant d'en jouer une nouvelle ; on ne
  rejoue que si la piste résolue change (anti-spam de paquets).
- **Admin** : `/moon audio play|stop|volume|reload|debug`.

## Compatibilité Bedrock (Geyser/Floodgate)
- Sons joués via l'API Adventure `Sound`/`Key` (Geyser traduit les sons vanilla).
- Détection Bedrock heuristique (UUID Floodgate à bits de poids fort nuls) → repli
  `bedrock-sound` par piste si défini.
- Aucun paquet non standard : uniquement `playSound`/`stopSound` (supportés Bedrock).

## Performance
- **Event-driven** + **une seule tâche légère** (1 s) qui ne fait que de la résolution
  (quelques lookups de map + un lookup de chunk) et ne rejoue que si nécessaire.
- Aucun travail par tick de déplacement ; réactivité immédiate via les events join/respawn/tp.
- État joueur en cache (`AudioStateManager`).

## Cas limites vérifiés
- **Relog** : loop global/joueur rechargés depuis `audio-data.yml` et réappliqués au join.
- **Mort** : réappliqué au respawn.
- **Téléport** : réappliqué au tick suivant (position à jour) → bonne zone.
- **Reload plugin** : `/moon audio reload` recharge tracks + data + config et réapplique ;
  `onDisable` coupe proprement l'audio de tous les joueurs.
- **Piste inconnue** : commandes refusent ; lecture ignore proprement.
- **Quit** : état nettoyé.

## Risques restants / limitations techniques
1. **Pas de vrai fondu (fade) sur un son en cours** : Minecraft ne permet pas d'ajuster le
   volume d'un son déjà émis. La transition est une coupe nette + relance. `fade-in/out` sont
   donc indicatifs. Un crossfade « réel » nécessiterait un resource-pack avec des variantes,
   hors périmètre.
2. **Boucle simulée** : pas de boucle native — relance à `length-seconds`. Si la valeur ne
   correspond pas à la durée réelle du son, on entend un léger chevauchement/silence. À régler
   par piste.
3. **Slot EVENT mono-piste** : events et boss partagent le slot EVENT (dernier déclencheur
   gagnant). Deux events simultanés ne se superposent pas (par design anti-spam).
4. **Détection Bedrock heuristique** : fiable avec Floodgate (UUID), mais non garantie sans
   Floodgate. Brancher l'API Floodgate améliorerait la précision.
5. **Sons custom resource-pack** : ne jouent que si le pack est chargé côté client ; sur
   Bedrock, prévoir un `bedrock-sound` de repli (vanilla) sinon silence.
6. **`/moon audio play` (aperçu)** : lecture one-shot non suivie ; peut être recouverte au
   prochain tick si une piste résolue existe. C'est volontaire (outil d'aperçu admin).

## Améliorations futures
- Brancher un **jingle « loot rare »** sur un futur `RareLootEvent` (RewardManager) — le hook
  EventBus est prêt, il manque l'événement côté Reward.
- **Floodgate API** pour la détection Bedrock + table de correspondance sons Java→Bedrock.
- **Fade volumétrique approximatif** via plusieurs `playSound` à volumes décroissants
  (au prix de paquets supplémentaires).
- **Zones audio reliées aux régions du ZoneManager** (option : lire la musique d'une région
  ZoneManager existante par nom, en plus des zones audio autonomes).
- **Priorité fine par event** (file de priorités EVENT plutôt que dernier-gagnant).
