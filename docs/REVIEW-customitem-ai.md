# MoonCore — CustomItemManager & AIAdminAssistant — Rapport final

Modules ajoutés à l'architecture existante (Paper 1.21.1, Java 21) sans modification
des modules tiers. Build vert (77 tests), jar shadé 20,4 Mo.

---

## 1. Architecture

### 1.1 CustomItemManager (`custom-item`)
Système d'objets custom **data-driven** : chaque objet est une définition YAML
(`plugins/MoonCore/items/<id>.yml`) éditable en jeu.

| Élément | Rôle |
|---|---|
| `api.customitem.CustomItemManagerService` | Service public consommé par les autres modules (boss/event/reward/progression). |
| `api.customitem.Rarity / ItemType / ItemStats / AbilityKind` | Catalogues (8 raretés, 9 types, ~18 stats, 18 capacités). |
| `modules.customitem.CustomItemDef` | Définition mutable (id, nom, type, rareté, matériau, modèle, stats, capacités, drops, recette). (Dé)sérialisation YAML. |
| `CustomItemDefStore` | Persistance fichier (lisible, versionnable, export/import trivial). |
| `CustomItemFactory` | Construit l'`ItemStack` : nom coloré par rareté, **lore complet**, PDC d'identité, attributs vanilla, modèle optionnel. |
| `ability.Ability / AbilityRegistry` | Capacités actives (handler au clic droit + cooldown) et passives (appliquées en continu). Extensible via `register(...)`. |
| `CustomItemListener` | Gameplay : crit, vol de vie, multiplicateurs PvE/PvP/Boss, résistance, fortune/harvest, loot/xp bonus, drops boss/mob, tick des passifs. |
| `RecipeManager` | Recettes shaped/shapeless enregistrées/retirées proprement. |
| `ResourcePackBuilder` | Génère un pack vanilla (overrides `custom_model_data`) à partir de PNG existants ; **ne fabrique aucune texture**, warning si manquante. |
| `command.CustomItemSubCommand` | `/moon item ...` complet (création, édition, stats, capacités, rareté, modèle, recette, drop, reward, pack, import). |

Le stockage des définitions est **fichier** (et non SQL) par choix : éditabilité,
revue et import/export. Les données runtime (cooldowns) sont en mémoire.

### 1.2 AIAdminAssistant (`ai-assistant`)
Assistant IA admin. **Pipeline de sécurité strict** :

```
Admin → /moon ai … → AiClient (async) → réponse IA (JSON) → AiActionValidator
      → CustomItemDef sûr (clampé/whitelisté) → CustomItemManager applique → audit
```

| Élément | Rôle |
|---|---|
| `AiConfig` | provider, model, clé API (server-side only), température, timeout, rate-limit, bornes. |
| `provider.AiProvider` (+ `AnthropicProvider`, `OpenAiProvider`) | Abstraction multi-fournisseur. |
| `AiClient` | HTTP `java.net.http` **asynchrone**, fenêtre de débit glissante, gestion timeout/erreur réseau/HTTP≥400. |
| `AiPrompts` | Prompts système imposant un **schéma JSON strict** borné aux valeurs réellement supportées. |
| `AiActionValidator` | Parse + valide + borne + whiteliste. **Jamais d'exécution de commande.** |
| `AiAuditStore` | Audit SQL (migration **1300**, table `mooncore_ai_audit`) : admin, date, requête, résultat, statut. |
| `command.AiSubCommand` | `/moon ai createitem/modifyitem/createbossdrop/createreward/createrecipe/balanceitem/generatelore/describeitem/model/reload/history`. |

---

## 2. Sécurité (IA)
- **Clé API jamais exposée** : lue côté serveur, jamais envoyée au client, jamais
  loggée, aucune commande ne la révèle.
- **Aucune exécution libre** : l'IA ne renvoie que des données ; seules des actions
  whitelistées (création/édition d'objets via les systèmes internes) sont possibles.
- **Bornage** : stats clampées (`max-stat-value`), niveaux de capacité clampés,
  capacités inconnues rejetées, matériaux/types/raretés vérifiés.
- **Rate-limit** anti-abus (req/min) + **audit** complet et consultable.
- Permissions : `mooncore.admin.items`, `mooncore.admin.ai` (default op).

---

## 3. Compatibilité Geyser / Floodgate (Java + Bedrock)

Détection Bedrock centralisée : `util.Compat.isBedrock()` (API Floodgate par
réflexion, repli heuristique UUID).

| Fonctionnalité | Java | Bedrock | Adaptation |
|---|---|---|---|
| Création d'items custom (admin) | ✅ | ✅ | Commandes texte — relayées par Geyser. |
| Armes custom (stats) | ✅ | ✅ | Attributs **serveur-side** → identiques. |
| Capacités **actives** | ✅ | ✅ | Déclencheur = **clic droit** (`PlayerInteractEvent`), pas de swap offhand (absent sur Bedrock). |
| Capacités **passives** | ✅ | ✅ | Effets serveur-side (potions, combat, tick). |
| Drops de boss / mob | ✅ | ✅ | Logique serveur, indépendante du client. |
| Récompenses d'événement | ✅ | ✅ | `event:<id>` appliqué serveur-side. |
| Stats/capacités visibles | ✅ | ✅ | **Toujours écrites dans le lore** (fallback visuel). |
| Modèle custom (`custom_model_data`) | ✅ (pack Java) | ⚠️ | **Non rendu nativement par Bedrock** → repli texture vanilla + lore. Pack Geyser séparé requis. Warning loggé par `ResourcePackBuilder`. |
| Commandes admin (item / ai) | ✅ | ✅ | 100% texte. |
| Audio (module existant) | ✅ | ⚠️ | Sons par clé/OGG ; certains sons custom peuvent ne pas exister côté Bedrock → fallback silencieux (déjà géré par `AudioStateManager`). |

**Conclusion Bedrock** : tout le **gameplay** fonctionne à l'identique sur Bedrock.
Seul le **rendu de modèle custom** diffère (limite Bedrock connue) ; le lore garantit
que l'information reste accessible. Aucune GUI Java-only n'a été introduite.

---

## 4. Limitations connues
- **Modèles Bedrock** : pas de génération automatique du pack Geyser (`.mcpack`).
  Le pack Java est généré ; le Bedrock reste manuel.
- **Accessoires / reliques / artefacts** : sans slot d'équipement vanilla, leurs
  stats passives s'appliquent quand l'objet est **tenu en main** (documenté).
- **Recettes** : shaped/shapeless avec choix par matériau (pas d'ingrédient = objet
  custom précis, par simplicité/robustesse).
- **IA** : qualité dépend du fournisseur/modèle/clé ; la validation borne mais ne
  juge pas le « fun ». Pas de streaming (réponse en un bloc).
- **Drops `event:<id>`** : l'attribution effective dépend de l'appel par EventManager
  au service (point d'intégration prêt côté objet).

---

## 5. Risques restants
- **Coût/quota IA** : appels payants ; rate-limit configurable mais à surveiller.
- **Renommages d'API Mojang/Paper** : capacités utilisent une API version-safe
  (clé de potion, attributs `GENERIC_*`) — à revérifier lors d'un upgrade majeur.
- **Charge `EntityDeathEvent`** : parcours des définitions à chaque mort ; négligeable
  pour quelques dizaines d'objets, à indexer par source si le catalogue explose.

---

## 6. Améliorations futures
- Générateur de pack **Bedrock/Geyser** automatique à partir des mêmes définitions.
- Index `source → defs` pour les drops (perf à grande échelle).
- GUI coffre (compatible Geyser) pour l'édition d'items en complément des commandes.
- Slots d'accessoires custom (inventaire virtuel) pour reliques/artefacts.
- Cache de réponses IA (prompts identiques) et mode « dry-run » (aperçu sans persister).
- Tests d'intégration sur un serveur headless pour les capacités actives.
