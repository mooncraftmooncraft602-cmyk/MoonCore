# MoonCore

Plugin principal de saison pour **Paper 1.21.x** (Java 21) — progression, events, boss, zones, anti-farm, économie durable, enchants et endgame.

## Build

```bash
./mvnw clean package        # Linux/macOS
mvnw.cmd clean package      # Windows
```

Le jar final est généré dans `target/MoonCore-<version>.jar` (dépendances HikariCP/Caffeine/MariaDB embarquées et relocalisées).

> Maven n'est pas requis sur la machine : le wrapper (`mvnw`) télécharge Maven 3.9.9 au premier lancement. Java 21 est requis.

## Installation

1. Déposer le jar dans `plugins/`.
2. Démarrer une fois : `config.yml`, `lang/fr.yml` sont créés.
3. Renseigner la section `database` (MySQL/MariaDB) dans `config.yml`.
4. (Optionnel) Installer **Vault** et **PlaceholderAPI** — détectés automatiquement.
5. Redémarrer.

## Commandes

- `/moon help [player|admin]` — aide
- `/moon version` — version, saison, modules actifs
- `/moon modules` — état de chargement des modules *(admin)*
- `/moon reload [module]` — recharge la config *(admin)*

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — architecture, modules, modèles de données, perf
- [docs/TODO.md](docs/TODO.md) — feuille de route exhaustive et priorités
- [docs/RISKS.md](docs/RISKS.md) — risques perf/exploit et stratégie de tests

## État

**0.1.0 — les 20 modules de la spécification sont implémentés, compilés et testés (62 tests verts).**

Noyau (framework de modules, DataManager MySQL/HikariCP + cache + migrations, ConfigManager, ServiceRegistry, EventBus, `/moon`) + modules métier : Season, Statistics, Reward, Progression, Missions (daily/weekly/seasonal), Quest, Leaderboard, Boss (YAML multi-phases), CustomEnchant (**30 enchants**), EndgameItems (**Netherite Flight Core**), Zone (21 flags), AntiFarm, AntiAFK, EconomyBalancer (Vault), Event, AdminTools.

Rapport de validation : [docs/REVIEW.md](docs/REVIEW.md). Backlog : Teams/Homes/Spawn natifs, PlaceholderAPI, GUIs, Folia.
