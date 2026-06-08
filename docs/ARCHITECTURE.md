# MoonCore — Architecture

> Plugin principal de saison pour Paper/Purpur 1.21.x — Java 21.
> Objectif gameplay : allonger fortement la durée de vie d'une saison (progression, events, endgame, économie durable, anti-abus).

---

## 1. Principes directeurs

1. **Modularité stricte** — chaque système est un `MoonModule` autonome, activable/désactivable via `config.yml`. Aucun module ne référence un autre en dur : la communication passe par le **ServiceRegistry** (interfaces) et un **EventBus** interne.
2. **Tout asynchrone côté I/O** — aucune requête SQL sur le thread principal. Lecture via cache, écriture en *write-behind*.
3. **Cache d'abord** — l'état joueur vivant est en mémoire (Caffeine). La base est la source de vérité persistée, pas le chemin chaud.
4. **Configurable à 100 %** — aucune valeur de gameplay codée en dur. Tout en YAML, rechargeable à chaud.
5. **Data-driven** — boss, enchants, zones, missions, récompenses, events : définis en YAML, pas en code. Un admin crée du contenu sans recompiler.
6. **Sûr par défaut** — chaque action sensible est derrière une permission ; chaque gain économique passe par des garde-fous (anti-farm, anti-afk, balancer).

---

## 2. Vue d'ensemble en couches

```
┌──────────────────────────────────────────────────────────────┐
│  COUCHE PRÉSENTATION                                          │
│  Commandes (/moon …)  •  GUIs (compat Bedrock)  •  Placeholders│
├──────────────────────────────────────────────────────────────┤
│  COUCHE MODULES MÉTIER                                         │
│  Progression • Season • Quest • Daily/WeeklyMission           │
│  Event • Boss • Zone • CustomEnchant • EndgameItems           │
│  EconomyBalancer • AntiFarm • AntiAFK • Reward • Leaderboard  │
│  Statistics • AdminTools                                       │
├──────────────────────────────────────────────────────────────┤
│  COUCHE SERVICES NOYAU (Core)                                 │
│  ModuleManager • ServiceRegistry • EventBus                   │
│  ConfigManager • DataManager (Database+Cache) • RewardEngine  │
│  Schedulers • Text/MiniMessage • MoonLogger                   │
├──────────────────────────────────────────────────────────────┤
│  COUCHE INFRASTRUCTURE                                         │
│  HikariCP → MariaDB/MySQL  •  Caffeine  •  Vault  •  Paper API │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Cycle de vie & framework de modules

### Contrat
```java
@ModuleInfo(id = "anti-farm", name = "AntiFarmManager",
            depends = {"data", "config"}, softDepends = {"statistics"})
public final class AntiFarmModule extends AbstractModule { … }
```

- `@ModuleInfo` déclare l'id, les dépendances dures (`depends`) et molles (`softDepends`).
- `ModuleManager` :
  1. instancie tous les modules activés dans `config.yml > modules`,
  2. **trie topologiquement** selon `depends` (échec si cycle ou dépendance manquante activée),
  3. appelle `enable()` dans l'ordre, `disable()` en ordre inverse,
  4. `reload()` à chaud (sans `disable/enable`) quand possible.

### États
`REGISTERED → ENABLING → ENABLED → DISABLING → DISABLED` (+ `FAILED`).
Un module qui échoue à `enable()` n'empêche pas les autres de charger (sauf si d'autres en dépendent : ils sont alors marqués `FAILED` en cascade et désactivés proprement).

### Communication inter-modules
- **ServiceRegistry** : un module *publie* une interface (`register(EconomyService.class, impl)`), les autres la *consomment* (`get(EconomyService.class)`), jamais la classe concrète.
- **EventBus interne** : événements typés MoonCore (`PlayerTierUpEvent`, `BossDefeatedEvent`, `MissionCompletedEvent`, `AbnormalGainEvent`…) en plus des `Bukkit Event` standards. Permet à Statistics/Quest/Reward de réagir sans coupler les producteurs.

---

## 4. Modules — rôle & dépendances internes

| Module | Rôle | depends | softDepends |
|---|---|---|---|
| **core** | bootstrap, registry, eventbus, scheduler, lang | — | — |
| **config** | chargement/reload des YAML, validation | core | — |
| **data** | Database (HikariCP), cache, migrations, repos | core, config | — |
| **season** | saison active, reset, archivage, calendrier | data | — |
| **economy-balancer** | taxes, sinks, frais, anti-inflation, hook Vault | data, config | statistics |
| **progression** | tiers 1→5, déblocages, gating de contenu | data, season | reward |
| **zone** | régions + flags (nohome, notpa, nopvp…) | data, config | — |
| **anti-farm** | limites spawners, densité entités, rendements | data, config | statistics, zone |
| **anti-afk** | détection AFK, réduction de gains | config | statistics |
| **statistics** | compteurs joueurs, historique, agrégats | data | — |
| **daily-mission** | missions journalières (rotation) | data, reward | progression |
| **weekly-mission** | missions hebdo | data, reward | progression |
| **quest** | quêtes saisonnières scénarisées | data, reward | progression |
| **reward** | moteur de récompenses générique (RewardEngine) | data, config | economy-balancer |
| **leaderboard** | classements (top X) calculés en async | data, statistics | — |
| **event** | events temporaires/saisonniers data-driven | data, config | boss, reward, zone |
| **boss** | boss YAML multi-phases, IA, capacités | data, config | reward, zone |
| **custom-enchant** | 30 enchants, livres, application | data, config | progression |
| **endgame-items** | objets endgame (Netherite Flight Core…) | data, config | custom-enchant, progression |
| **admin-tools** | outils in-game transverses, debug, reload | core | * (toutes molles) |

> Règle : une flèche `depends` signifie « j'ai besoin que l'autre soit ENABLED avant moi ». Les `softDepends` n'imposent que l'ordre *si présent*.

---

## 5. Système de sauvegarde (DataManager)

### Pile
`Caffeine (mémoire) → Repository (mapping objet↔SQL) → HikariCP → MariaDB/MySQL`

### Stratégie
- **Lecture** : `getCached(uuid)` ; *miss* → chargement async → mise en cache. À la connexion d'un joueur, préchargement (`PlayerJoinEvent`, async load).
- **Écriture** : *write-behind*. Les mutations marquent l'entité `dirty` ; un flush périodique (`persistence.auto-save-interval-seconds`) et le `PlayerQuitEvent` écrivent par lots (`batch-size`).
- **Cohérence** : chaque write passe par une file mono-consommateur par table pour éviter les races ; transactions pour les opérations multi-tables (ex. achat = débit + log + stat).
- **Migrations** : table `mooncore_schema_version` ; `MigrationRunner` applique en ordre les `Migration(version, sql/apply)` manquantes au démarrage (idempotent).
- **Crash-safety** : flush au `onDisable`. Le write-behind borne la perte au pire à `auto-save-interval` ; les transactions économiques critiques (transferts) sont écrites *immédiatement*, pas en write-behind.

### Convention de tables
Préfixe `mooncore_`. Clés joueur en `BINARY(16)` (UUID compact) indexées. Données saisonnières portent une colonne `season_id` pour archivage/reset.

---

## 6. Modèles de données (logiques)

> Détaillés et versionnés via migrations. Vue logique ci-dessous.

- **player_profile** `(uuid PK, name, first_join, last_seen, playtime_seconds, season_id)`
- **progression** `(uuid, season_id, tier, xp, unlocked_flags JSON, PK(uuid,season_id))`
- **statistics** `(uuid, season_id, stat_key, value, PK(uuid,season_id,stat_key))` — modèle EAV pour extensibilité.
- **stat_history** `(id PK, uuid, stat_key, delta, reason, ts)` — historique/audit.
- **team** `(team_id PK, name, owner_uuid, created_at, season_id)` + **team_member** `(team_id, uuid, role)`.
- **home** `(uuid, name, world, x,y,z,yaw,pitch, PK(uuid,name))`.
- **mission_state** `(uuid, mission_id, scope[daily|weekly|season], progress, completed, claimed, reset_at)`.
- **quest_state** `(uuid, quest_id, step, completed JSON)`.
- **spawner_registry** `(id PK, chunk_key, owner_uuid, team_id, world, x,y,z, type, created_at)` — pour AntiFarm (compte par chunk/équipe).
- **economy_ledger** `(id PK, uuid, amount, balance_after, type[tax|sink|fee|gain|transfer], reason, ts)` — audit anti-inflation.
- **reward_claim** `(uuid, reward_id, source, claimed_at)` — anti double-claim.
- **leaderboard_snapshot** `(board_id, season_id, rank, uuid, value, computed_at)` — précalcul async.
- **enchant_state** : porté par l'item (PDC), pas en base ; la base ne stocke que les *livres en stock admin* si besoin.

---

## 7. Performance — choix structurants

- **Zéro scan global** : pas de `getNearbyEntities` ni de balayage de chunks périodique. AntiFarm s'appuie sur des **événements** (`CreatureSpawnEvent`, `SpawnerSpawnEvent`, pose de spawner) + un registre indexé par chunk, pas sur des comptages live.
- **Tick budget** : les tâches lourdes (leaderboards, agrégats stats) tournent en async ; seuls les effets monde reviennent sur le main thread (Folia-aware si possible : `getRegionScheduler`/`getGlobalRegionScheduler` quand dispo, fallback Bukkit scheduler).
- **Cache partout** : profils, zones (R-tree/grille par chunk), définitions YAML parsées une fois.
- **Boss/Event** : boucles d'IA throttlées (ex. tick toutes les N ticks), capacités limitées par cooldown, despawn de sécurité.
- **Indexation** : index SQL sur toutes les clés d'accès (uuid, chunk_key, season_id, stat_key).
- **Batch I/O** : écritures groupées via `addBatch`.

---

## 8. Configuration

- `config.yml` — noyau (DB, modules, langue, cache, persistence).
- `lang/fr.yml` — tous les messages (MiniMessage), aucun texte en dur.
- `modules/<id>.yml` — config par module.
- `content/bosses/*.yml`, `content/zones/*.yml`, `content/enchants/*.yml`, `content/missions/*.yml`, `content/events/*.yml`, `content/rewards/*.yml`, `content/quests/*.yml` — contenu data-driven.
- Reload à chaud : `/moon admin reload [module]`.

---

## 9. Sécurité & permissions

Arbre `mooncore.*` → `mooncore.player` / `mooncore.admin` (voir `plugin.yml`).
- Permissions par catégorie admin (events, bosses, zones, economy, …).
- Permissions `mooncore.bypass.*` pour le staff (anti-farm, anti-afk, taxes, zones, cooldowns).
- Chaque sous-commande vérifie sa permission ; les actions destructrices demandent confirmation.

---

## 10. Compatibilité

- **Bedrock (Geyser/Floodgate)** : éviter les GUIs reposant sur des items custom non rendus côté Bedrock ; prévoir des fallbacks chat/commandes. Les enchants/objets utilisent le `PersistentDataContainer` (portable).
- **Vault** : EconomyBalancer lit/modifie via l'API Vault ; aucune réécriture de l'économie existante.
- **Paper/Purpur 1.21.x** : API `api-version: '1.21'`. Détection optionnelle de Folia pour le scheduling régionalisé.
```
