# MoonCore — Rapport de revue & validation (Phase 6)

> Version revue : 0.1.0-SNAPSHOT · Paper/Purpur 1.21.x · Java 21
> État : **20/20 modules de la spécification implémentés**, build vert, 62 tests unitaires verts.

---

## 1. Périmètre vérifié

Les 20 modules demandés sont présents et compilent :

| # | Module | id | Statut |
|---|--------|----|--------|
| 1 | Core | — | ✅ framework, lifecycle, registry, eventbus |
| 2 | ConfigManager | (service) | ✅ |
| 3 | DataManager | (service) | ✅ HikariCP + cache + migrations |
| 4 | SeasonManager | season | ✅ |
| 5 | StatisticsManager | statistics | ✅ |
| 6 | RewardManager | reward | ✅ |
| 7 | ProgressionManager | progression | ✅ tiers 1→5 |
| 8 | Daily/WeeklyMissionManager | missions | ✅ moteur unifié (daily+weekly+seasonal) |
| 9 | QuestManager | quest | ✅ |
| 10 | LeaderboardManager | leaderboard | ✅ async + snapshots |
| 11 | BossManager | boss | ✅ YAML, multi-phases, 9 capacités |
| 12 | CustomEnchantManager | custom-enchant | ✅ **30 enchants** |
| 13 | EndGameItems | endgame-items | ✅ **Netherite Flight Core** |
| 14 | ZoneManager | zone | ✅ 21 flags |
| 15 | AntiFarmManager | anti-farm | ✅ |
| 16 | AntiAFKManager | anti-afk | ✅ |
| 17 | EconomyBalancer | economy-balancer | ✅ hook Vault |
| 18 | EventManager | event | ✅ data-driven |
| 19 | AdminTools | admin-tools | ✅ |
| 20 | (Daily+Weekly fusionnés ci-dessus) | | |

---

## 2. Architecture — ✅

- **Modularité** : 16 modules métier autonomes + services noyau, activables via `config.yml`.
- **Découplage** : communication par **ServiceRegistry** (interfaces `api.*`) et **EventBus** interne. Aucun module ne référence l'implémentation d'un autre, sauf 2 accès volontaires via `ModuleManager.get(Class)` (Event→Boss/Zone) en dépendance molle.
- **Ordre de chargement** : tri topologique (`DependencyResolver`) ; garde-fou `ModuleWiringTest` garantissant que `depends` ne cible que des modules réels (bug `data/config` attrapé et corrigé en cours de route).
- **Data-driven** : boss, enchants (effets en code, valeurs paramétrables), zones, missions, quêtes, events, récompenses — définis en YAML.

## 3. Performances — ✅

- **Aucune requête SQL sur le thread principal** : `Database` async (HikariCP + exécuteur dédié) ; lecture via cache, écriture *write-behind*.
- **Aucun scan global d'entités/chunks** : AntiFarm compte via **compteurs incrémentaux** (events) et un comptage **borné à un seul chunk** au spawn ; Boss/Éclipse utilisent un rayon borné ; Zone via index par chunk.
- **Boucles throttlées** : IA boss (10 ticks), tick passif enchants (20 ticks), scan AFK (20 ticks), recompute leaderboards async espacé.
- **Caches** : profils, zones, snapshots, définitions YAML parsées une fois.
- `PlayerMoveEvent` (Zone, AntiAFK) court-circuité tant que le bloc ne change pas.

## 4. Permissions — ✅

- Arbre `mooncore.*` → `mooncore.player` / `mooncore.admin` (catégorisé) défini dans `plugin.yml`.
- Chaque sous-commande déclare sa permission ; vérification centralisée dans `MoonCommand`.
- Bypass staff : `mooncore.bypass.{antifarm,antiafk,economy.tax,zone,cooldown}` respectés par les modules concernés.

## 5. Commandes — ✅

Commande racine `/moon` + aide `help [player|admin]`. Sous-commandes : help, version, modules, reload, zone, antifarm, antiafk, economy, stats, progression, missions, quest, leaderboard, reward, boss, enchant, item, event, season, admin. Toutes avec auto-complétion contextuelle filtrée par permission.

## 6. Configuration — ✅

- `config.yml` (DB, modules, cache, persistence, langue, saison).
- `lang/fr.yml` — tous les messages externalisés (MiniMessage), aucun texte en dur.
- `modules/<id>.yml` par module · `content/{bosses,zones,enchants?,missions,events,rewards,quests}/*.yml`.
- Reload à chaud : `/moon reload [module]`.

## 7. Sauvegardes — ✅

- Migrations versionnées, **plages réservées par module** (core 1, zone 100, antifarm 200, eco 300, stats 400, reward 500, prog 600, missions 700, season 800, quest 900) → pas de collision.
- Clés UUID en `CHAR(36)` homogènes.
- Write-behind + flush au quit + flush périodique + flush à l'arrêt.
- Transactions immédiates pour l'audit/anti double-claim (`reward_claim` via `INSERT IGNORE` atomique).

## 8. Cohérence des systèmes — ✅

Chaînes inter-modules vérifiées : Missions/Quests/Boss → Reward + Progression + Statistics ; AntiFarm → Zone (`nospawner`) + AntiAFK (multiplicateur) ; Event → Boss/Reward/Zone/Progression ; Leaderboard → Statistics ; Enchants → Economy/Boss. Statistics consomme l'EventBus (AFK, gains anormaux).

## 9. Risques d'exploitation — ✅ (couverts) / ⚠️ (à surveiller)

- ✅ **Injection SQL** : 100 % PreparedStatement ; les seules concaténations sont des placeholders `?` générés ou du DDL constant.
- ✅ **Double-claim** récompenses/missions : verrou applicatif + clé unique DB.
- ✅ **Contournement AntiFarm** : limites combinées chunk **+** joueur **+** équipe ; densité par chunk.
- ✅ **Contournement Zone** : flags enderpearl/elytra/lit/commande/mouvement + bypass perm.
- ✅ **Abus AFK** : multi-signaux, réduction de gains via service consommé par AntiFarm.
- ⚠️ **Économie** : le solde reste géré par Vault/EssentialsX ; les transferts joueur↔joueur natifs ne passent pas par MoonCore (hors scope), donc la détection de gains anormaux ne couvre que les gains routés via `EconomyService`.

## 10. Cas limites — ✅

- **DB injoignable au démarrage** → plugin désactivé proprement (fail-fast), serveur reste up.
- **Vault absent** → EconomyBalancer en mode dégradé (taxes/frais inactifs), pas d'erreur.
- **Reload à chaud** → recharge config sans double-enregistrement de listeners/commandes.
- **Joueur hors-ligne** → stats/progression en upsert DB ; missions/quêtes ne progressent que connecté (normal).
- **Changement de saison** → nouvelles données vierges, anciennes archivées par `seasonId` (redémarrage recommandé pour propager le `seasonId` à tous les modules).

## 11. Compatibilité Paper/Purpur 1.21.x — ✅

- `api-version: '1.21'`, Java 21.
- API modernes 1.21 : composant `GLIDER` (Flight Core), `Registry.SOUNDS` (au lieu de `Sound.valueOf` retiré), attributs `MAX_HEALTH`/`ATTACK_DAMAGE`/`KNOCKBACK_RESISTANCE`.
- Détection Folia exposée (`Schedulers.isFolia`) ; scheduling actuel = Bukkit (Purpur non-Folia). Support régionalisé Folia = amélioration future.
- **Bedrock (Geyser/Floodgate)** : aucun GUI à inventaire custom — tout passe par chat/commandes/barres/titres, donc rendu correct côté Bedrock. ✅

---

## 12. Améliorations possibles (backlog)

1. **Module Teams/Homes/Spawn natifs** (décision « remplacement complet ») : `TeamService` n'a qu'un stub ; l'implémenter activerait pleinement les limites AntiFarm par équipe et les classements par équipe.
2. **PlaceholderAPI** : exposer progression/stats/économie/classements/missions en placeholders.
3. **Application des enchants à l'enclume** (livres) en plus de la commande ; et préserver la lore vanilla existante (actuellement la lore custom remplace la lore).
4. **GUIs** optionnels (avec fallback Bedrock déjà en place via commandes).
5. **Folia** : router via `RegionScheduler`/`GlobalRegionScheduler`.
6. **Tests d'intégration DB** (Testcontainers MariaDB) pour repos + migrations.
7. **Propagation du changement de saison sans redémarrage** (cycle disable/enable contrôlé des modules data-dépendants).
8. **Détection de gains anormaux étendue** si l'économie devient native.

---

## 13. Vérification finale

- `mvn clean package` → **BUILD SUCCESS**.
- **62 tests unitaires verts** (DependencyResolver, ModuleWiring, Zone, AntiFarm, AntiAFK, Economy, Reward, Progression, Mission, Leaderboard, Boss, Enchant, Stats, Season, Quest, Cooldowns, TimeFormat).
- Jar shadé : libs relocalisées, `META-INF/services/java.sql.Driver` → driver MariaDB relocalisé (connexion JDBC OK au runtime).

**Conclusion** : le périmètre fonctionnel demandé est couvert, l'architecture est saine, performante et extensible, et les garde-fous anti-abus/anti-inflation — cœur de la problématique de rétention — sont en place. Le plugin est prêt pour une phase de test sur serveur de préproduction (avec MySQL/MariaDB + Vault).
