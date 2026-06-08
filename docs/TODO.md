# MoonCore — TODO exhaustive & priorités

Légende priorité : **P0** = fondation bloquante · **P1** = cœur de saison · **P2** = contenu/profondeur · **P3** = confort/polish.
État : ☐ à faire · ◐ en cours · ☑ fait.

> **STATUT GLOBAL (0.1.0)** : ✅ Les 20 modules de la spécification sont implémentés, compilés et testés (62 tests verts). Revue Phase 6 effectuée → voir [REVIEW.md](REVIEW.md). Backlog restant : module Teams/Homes/Spawn natifs (remplacement complet), PlaceholderAPI, GUIs, support Folia, tests d'intégration DB — détaillé dans REVIEW.md §12.

---

## PHASE 0 — Fondation (P0) — *en cours*

- ☑ Projet Maven, wrapper, shade+relocate (HikariCP/Caffeine/MariaDB)
- ☑ `plugin.yml`, arbre de permissions, `config.yml`
- ☑ Docs : architecture, modèles de données, risques
- ◐ Core : `MoonModule`, `AbstractModule`, `@ModuleInfo`, `ModuleManager` (tri topologique, lifecycle)
- ◐ `ServiceRegistry` + `EventBus` interne
- ◐ `ConfigManager` (chargement/reload, validation, `lang/fr.yml`)
- ◐ `DataManager` : `Database` (HikariCP), exécuteur async, `MigrationRunner`, cache Caffeine
- ◐ Utilitaires : `Text`/MiniMessage, `Schedulers` (Folia-aware), `MoonLogger`
- ◐ Commande `/moon` + système d'aide (`help`, `help player`, `help admin`) + dispatcher de sous-commandes
- ☐ Tests : harnais de test (`DependencyResolver`, `MigrationRunner`, parsing config) sans serveur

## PHASE 1 — Données & saison (P0/P1)

- ☐ `SeasonManager` : saison active, calendrier, reset/archivage (déplacement vers tables `*_archive`), commande `/moon season …`
- ☐ Repositories : `PlayerProfileRepository`, `StatisticsRepository`
- ☐ Préchargement profil au join (async) + flush au quit + auto-save périodique
- ☐ `StatisticsManager` : compteurs EAV, historique, API d'incrément, hooks EventBus

## PHASE 2 — Anti-abus & économie (P1) — *priorité produit n°1*

- ☐ `ZoneManager` : régions (sélection, création), index par chunk, **flags** : nohome, notpa, nobed, noelytra, noenderpearl, noclaim, noblockbreak, noblockplace, nopvp, forcepvp, noflight, nogrief, nospawner, nomobspawn, nocommand, nodamage, noitemdrop, noitempickup, noenter/noleave, nointeract. Commandes `/moon zone …`.
- ☐ `AntiFarmManager` :
  - registre spawners indexé par chunk/équipe (events de pose/casse)
  - limites configurables : par chunk, par équipe, globale par joueur
  - détection densité d'entités par chunk via compteurs incrémentaux (pas de scan)
  - réduction progressive de rendement au-delà de seuils (courbe configurable)
  - liste blanche/normale pour ne pas casser les farms raisonnables
  - alertes admin + logs
- ☐ `AntiAFKManager` : détection (mouvement, interaction, rotation), états, réduction/annulation des gains (mobs, missions, drops) en zone AFK, logs, bypass perms.
- ☐ `EconomyBalancer` (hook Vault) : taxes (transaction, vente), frais (tp, réparation, maintenance/upkeep), taxes progressives par richesse, money sinks, `economy_ledger`, détection de gains anormaux (`AbnormalGainEvent`), tableau de bord `/moon economy …`.

## PHASE 3 — Progression & objectifs (P1/P2)

- ☐ `ProgressionManager` : tiers 1→5, XP/critères de montée, `unlocked_flags`, gating (un contenu requiert un tier), events `PlayerTierUpEvent`, GUI/commande de progression.
- ☐ `RewardManager` / `RewardEngine` : récompenses génériques data-driven (items, money via Vault, commandes, enchants, perms temporaires), anti double-claim (`reward_claim`).
- ☐ `DailyMissionManager` + `WeeklyMissionManager` : pool de missions YAML, rotation/reset horodaté, suivi de progression via EventBus, claim de récompenses.
- ☐ `QuestManager` : quêtes saisonnières multi-étapes scénarisées, prérequis de tier.
- ☐ `LeaderboardManager` : boards configurables (richesse, kills boss, missions, playtime…), calcul async + snapshot, affichage, placeholders.

## PHASE 4 — Contenu endgame (P2)

- ☐ `CustomEnchantManager` : moteur d'enchants sur PDC + les **30 enchants** (voir liste), livres, niveaux, application, conflits, commandes `/moon enchant …`, gating par progression.
- ☐ `EndGameItems` : framework d'items custom (PDC + recettes/obtention) + **Netherite Flight Core** (vol type Elytra en conservant les stats du plastron netherite) + autres items pertinents.
- ☐ `BossManager` : boss YAML multi-phases, stats (HP/dégâts/armure/vitesse), capacités (dash, téléport, explosion, invocation, poison, feu, régén, bouclier, AoE…), barre de boss, table de loot, events de spawn.
- ☐ `EventManager` : framework d'events data-driven (boss event, PvP event, chasse au trésor, temporaire, saisonnier), planification, annonces, récompenses, intégration Zone.

## PHASE 5 — Administration & finition (P2/P3)

- ☐ `AdminTools` : inspection joueur, debug modules, dump cache, simulate, reload ciblé, give enchant/book/item, force tier, trigger event/boss, gestion zones, audit économie.
- ☐ Placeholders PlaceholderAPI (progression, stats, économie, missions, classements).
- ☐ Compat Bedrock : fallbacks chat/commande pour tout flux GUI.
- ☐ `lang/fr.yml` complété, tous messages externalisés.
- ☐ Documentation admin (création de boss/zone/enchant/mission via YAML).

## PHASE 6 — Revue & validation (P0 transverse, avant release)

- ☐ Revue architecture & cohérence inter-modules
- ☐ Profilage perf (Spark) : events anti-farm, boss tick, leaderboards
- ☐ Revue permissions (toutes les sous-commandes couvertes)
- ☐ Revue exploits : duplication, contournement zones/anti-farm, double-claim, races éco
- ☐ Tests cas limites (cache miss, DB down, reload à chaud, reset de saison)
- ☐ Compat Paper/Purpur 1.21.x + Geyser/Floodgate
- ☐ Rapport de vérification final

---

## Liste des 30 enchants (Phase 4)
Vampirisme · Exécution · Berserker · Brise-Armure · Saignement · Poison · Coup Critique · Chasseur de Boss · Phoenix · Résilience · Épines Avancées · Régénération · Endurance · Absorption · Anti-Knockback · Aura de Protection · Excavation · Prospection · Bûcheron · Vein Miner · Trésor Caché · Super Fortune · Auto Smelt · Double Saut · Dash · Agilité · Anti-Chute · Dragon Slayer · Titan · Éclipse.

## Ordre de livraison recommandé
`Fondation → Données/Saison → ZoneManager → AntiFarm → AntiAFK → EconomyBalancer → Statistics → Progression → Reward → Missions → Quest → Leaderboard → Boss → Event → CustomEnchant → EndgameItems → AdminTools → Revue`.

> Rationale : les systèmes anti-abus (Phase 2) répondent directement au problème n°1 (joueurs riches trop vite qui partent) ; ils doivent être prêts avant d'ajouter du contenu qui amplifierait les gains.
