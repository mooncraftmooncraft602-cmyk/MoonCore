# MoonCore — Risques & stratégie de tests

## 1. Risques de performance (plusieurs centaines de joueurs)

| Risque | Cause | Mitigation |
|---|---|---|
| Lag main-thread sur I/O | requêtes SQL synchrones | **Tout async** (HikariCP + executor dédié) ; cache lecture ; write-behind. |
| Scan d'entités coûteux | AntiFarm comptant les mobs par `getNearbyEntities`/scan de chunks | **Compteurs incrémentaux** via events `(Spawner)CreatureSpawnEvent` + registre indexé par chunk. Aucun scan périodique. |
| Boss/Event tick lourd | IA exécutée chaque tick pour de nombreuses entités | Tick throttlé (toutes N ticks), cooldowns de capacités, despawn de sécurité, limite d'instances simultanées. |
| Leaderboards | tri global fréquent | Calcul **async** + snapshot mis en cache, recalcul espacé (config). |
| Explosion mémoire cache | profils jamais évincés | Caffeine `expireAfterAccess` + `maximumSize` ; éviction des joueurs hors-ligne. |
| Thrash DB au reset de saison | reset massif synchrone | Archivage par lots async, fenêtre de maintenance, transactions bornées. |
| Folia/regionisation | scheduler global bloquant | `Schedulers` détecte Folia → region/global scheduler ; fallback Bukkit. |
| Reload à chaud | états incohérents | Reload limité à la config ; lifecycle module propre pour le reste. |

## 2. Risques d'exploitation (abus joueurs)

| Exploit | Vecteur | Mitigation |
|---|---|---|
| Duplication d'objets endgame/enchants | crafts, clics shift, mort/respawn | Marqueurs PDC + ID unique, validations serveur, tests dédiés. |
| Contournement AntiFarm | déplacer la farm sur plusieurs chunks/équipes/comptes | Limites combinées chunk **et** équipe **et** joueur ; détection densité ; logs d'alerte. |
| Contournement de zone | enderpearl, elytra, /home, lit, déconnexion-reco | Flags dédiés (noenderpearl, noelytra, nohome, nobed) + check à l'entrée/téléport + check au login (position). |
| Abus AFK | machines à clic, anti-AFK auto | Détection multi-signaux (déplacement réel + rotation + interaction variée), réduction de gains, logs. |
| Double-claim de récompenses | spam de commande, lag | Table `reward_claim` (clé unique), verrou applicatif, idempotence. |
| Race économique (solde négatif/dup money) | transactions concurrentes | Transferts en **transaction immédiate** (pas write-behind), check-and-set atomique, `economy_ledger`. |
| Inflation | farms + gains AFK + events répétés | EconomyBalancer : sinks, taxes progressives, détection `AbnormalGainEvent`. |
| Élévation de privilèges | sous-commande sans check | Chaque sous-commande vérifie sa permission ; revue Phase 6. |
| Injection SQL | concat de chaînes | **PreparedStatement** systématique, jamais de concaténation. |

## 3. Stratégie de tests

**Tests unitaires (sans serveur)** — exécutables via Maven :
- `DependencyResolver` : ordre topologique, détection de cycle, dépendance manquante.
- `MigrationRunner` : application idempotente, saut des migrations déjà appliquées.
- Parsing/validation de config (valeurs manquantes, types, bornes).
- Courbes de rendement AntiFarm, calculs de taxes EconomyBalancer (fonctions pures).

**Tests d'intégration** :
- DB : MariaDB éphémère (Testcontainers) pour repos + migrations.
- Cache : cohérence write-behind (dirty → flush → relecture).

**Tests manuels in-game (checklist Phase 6)** :
- Reload à chaud par module.
- DB indisponible au démarrage → dégradation propre (modules data-dépendants `FAILED`, serveur reste up).
- Reset de saison sur jeu de données réaliste.
- Parcours Bedrock (Geyser) sur chaque flux UI.
- Cas limites zones (login en zone, tp forcé, pearl à travers bord de zone).

**Profilage** : Spark sur les events anti-farm, le tick boss et le recalcul des leaderboards sous charge simulée.
