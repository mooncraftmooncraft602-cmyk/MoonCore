package com.mooncore.modules.ai;

import com.mooncore.api.customitem.ItemStats;
import com.mooncore.api.customitem.ItemType;
import com.mooncore.api.customitem.Rarity;
import com.mooncore.modules.customitem.ability.AbilityRegistry;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Construction des prompts système. Le prompt impose un format de sortie strict
 * (JSON only) et contraint l'IA aux valeurs réellement supportées par MoonCore, ce qui
 * réduit drastiquement les hallucinations et facilite la validation.
 */
public final class AiPrompts {

    private final AbilityRegistry abilities;

    public AiPrompts(AbilityRegistry abilities) {
        this.abilities = abilities;
    }

    private String abilitiesList() {
        return abilities.all().stream()
                .map(a -> a.id() + (a.special() ? "*" : "") + " (" + (a.isActive() ? "actif" : "passif") + ")")
                .collect(Collectors.joining(", "));
    }

    /** Doc des capacités « spéciales » (opt-in) : id → description, pour mapper le langage naturel. */
    private String specialAbilitiesDoc() {
        return abilities.all().stream()
                .filter(com.mooncore.modules.customitem.ability.Ability::special)
                .map(a -> "  - " + a.id() + " : " + a.description())
                .collect(Collectors.joining("\n"));
    }

    /** Règle commune : capacités opt-in (jamais ajoutées sans demande explicite). */
    private String abilityPolicy() {
        return """
                CAPACITÉS — RÈGLE STRICTE :
                - N'ajoute AUCUNE capacité par défaut. Laisse "abilities": [] SAUF si l'admin
                  demande EXPLICITEMENT des pouvoirs/effets/magie/capacités spéciales (ex.
                  « épée de magie noire qui draine la vie », « pioche qui mine en 3x3 »,
                  « armure qui renvoie les dégâts »).
                - Les capacités marquées d'une * sont puissantes (magie noire innée aux armes,
                  pouvoirs d'outils) : utilise-les UNIQUEMENT sur demande, en choisissant celles
                  qui collent à la description. Mets le bon "level" (1-5, plus haut = plus fort).
                - Capacités d'outils (pioche/hache/pelle/houe) : tunnel_3x3, vein_miner, auto_smelt,
                  timber, magnet_pickup, telekinesis, auto_replant, explosive_mine, fortune_surge,
                  haste_on_mine — seulement si l'outil doit avoir ce comportement.
                - Si l'admin en demande « plein » / « le plus possible », tu peux en combiner
                  plusieurs (3-5) cohérentes ensemble.
                Catalogue des capacités spéciales :
                %SPECIAL_ABILITIES%
                """.replace("%SPECIAL_ABILITIES%", specialAbilitiesDoc());
    }

    private static String statsList() {
        return String.join(", ", ItemStats.known().keySet());
    }

    private static String typesList() {
        return Arrays.stream(ItemType.values()).map(ItemType::id).collect(Collectors.joining(", "));
    }

    private static String raritiesList() {
        return Arrays.stream(Rarity.values()).map(Rarity::id).collect(Collectors.joining(", "));
    }

    /** Prompt système pour produire/équilibrer un item (sortie JSON stricte). */
    public String itemSchemaSystem() {
        return """
                Tu es un game designer expert pour un serveur Minecraft RPG (plugin MoonCore).
                Tu DOIS répondre UNIQUEMENT par un objet JSON valide, sans texte autour, sans
                bloc de code markdown. Aucune commande, aucune instruction système : seulement
                des données.

                Schéma attendu :
                {
                  "id": "string (a-z 0-9 _ -, court)",
                  "display_name": "nom MiniMessage, ex: <gold>Lame du Dragon</gold>",
                  "type": un de [%TYPES%],
                  "rarity": un de [%RARITIES%],
                  "material": un Material Bukkit valide en MAJUSCULES (ex: NETHERITE_SWORD),
                  "tool_kind": optionnel, un de axe|pickaxe|shovel|hoe|sword pour les vrais outils,
                  "tool_tier": optionnel, un de wood|stone|iron|gold|diamond|netherite,
                  "lore": ["lignes MiniMessage de description/lore"],
                  "stats": { "<stat>": nombre, ... },
                  "abilities": [ { "id": "<capacité>", "level": entier } ],
                  "enchants": { "<enchantement vanilla>": niveau, ... } (optionnel ; clés
                            comme sharpness, protection, efficiency, unbreaking, fortune…),
                  "consume_effects": [ { "effect": "<effet vanilla>", "duration": secondes,
                            "amplifier": entier } ] (optionnel ; UNIQUEMENT pour type=consumable,
                            ex potion/nourriture ; effets comme speed, regeneration, strength…),
                  "glowing": booléen,
                  "unbreakable": booléen
                }

                Stats autorisées : %STATS%.
                Capacités autorisées (utilise UNIQUEMENT celles-ci ; * = spéciale/opt-in) : %ABILITIES%.

                %ABILITY_POLICY%

                ÉQUILIBRAGE STRICT (un objet ne doit JAMAIS être cheaté) — référence :
                une épée netherite vanilla fait 8 de dégâts. Plafonds ABSOLUS (même pour
                ancient) : damage ≤ 15, health ≤ 20, armor ≤ 20, crit_chance ≤ 50,
                crit_damage ≤ 150, life_steal ≤ 20, boss_damage ≤ 75, pvp/pve_damage ≤ 50,
                movement_speed ≤ 30, cooldown_reduction ≤ 40.
                Budget par rareté (reste BIEN en dessous des plafonds pour les basses
                raretés) :
                - common : 1 petite stat, 0 capacité.
                - uncommon : 1-2 petites stats, 0 capacité.
                - rare : 2 stats modérées, 0-1 capacité passive.
                - epic : 2-3 stats, 1 capacité.
                - legendary : 3 stats, 1-2 capacités.
                - mythic/divine/ancient : 3-4 stats fortes (sans dépasser les plafonds),
                  2 capacités max.
                Maximum 2 capacités, 4 stats. Pas de valeurs absurdes. Privilégie un
                gameplay intéressant plutôt que des chiffres énormes.
                Réponds en français pour les textes (display_name, lore).
                """
                .replace("%TYPES%", typesList())
                .replace("%RARITIES%", raritiesList())
                .replace("%STATS%", statsList())
                .replace("%ABILITY_POLICY%", abilityPolicy())
                .replace("%ABILITIES%", abilitiesList());
    }

    private static String bossAbilities() {
        return java.util.Arrays.stream(com.mooncore.modules.boss.BossAbilityType.values())
                .map(Enum::name).collect(Collectors.joining(", "));
    }

    /**
     * Prompt unifié : l'admin décrit ce qu'il veut, l'IA CHOISIT elle-même le(s) type(s)
     * (objet / bloc / minerai / boss) et peut en créer PLUSIEURS d'un coup.
     */
    public String unifiedCreateSystem() {
        return """
                Tu es le cerveau de MoonCore (serveur Minecraft RPG). L'admin te parle ; TU
                choisis quoi faire : RÉPONDRE à une question, CRÉER un ou plusieurs éléments
                (objet/bloc/boss, combinables), ou CODER une fonctionnalité que les données ne
                permettent pas. Réponds UNIQUEMENT par un JSON :
                { "creations": [ {"kind":"item|block|boss|answer|code", ...champs...}, ... ] }
                (1 à 8 éléments max). Aucun texte hors JSON.

                kind="answer" → simple réponse/explication. Champs : text (string, français).
                  Utilise-le quand l'admin pose une QUESTION et ne demande pas de création.

                kind="code" → l'admin demande une action que item/block/boss ne couvrent PAS
                  (ex. « fais pleuvoir des poulets », « téléporte tous les joueurs au spawn »,
                  « donne 64 diamants à tout le monde »). Champs : task (string : décris
                  précisément, en français, ce que le code Java doit faire). Le serveur
                  générera puis exécutera ce code (mode développeur requis).

                kind="item" → objet/arme/armure/outil/lingot/accessoire. Champs :
                  id, display_name (MiniMessage), type (un de [%TYPES%]),
                  rarity (un de [%RARITIES%]), material (Material Bukkit MAJ),
                  lore (array), stats {<stat>:nombre}, abilities [{id,level}],
                  glowing, unbreakable.
                  Stats autorisées : %STATS%. Capacités (* = spéciale/opt-in) : %ABILITIES%.
                  %ABILITY_POLICY%
                  ÉQUILIBRAGE strict : damage≤15, crit_damage≤150, boss_damage≤75,
                  life_steal≤20, 4 stats max. Jamais cheaté.

                kind="block" → bloc ou minerai. Champs :
                  id, display-name (MiniMessage), drop-xp (entier), requires-pickaxe (bool),
                  worldgen { generate(bool), replace("STONE"/"DEEPSLATE"...), min-y, max-y,
                  veins-per-chunk(1-6), vein-size(2-8) }.

                kind="boss" → mob boss. Champs :
                  display-name, entity (EntityType vivant MAJ), max-health(10-5000),
                  damage(1-40), speed(0.1-0.6), armor(0-30),
                  bar-color [PURPLE,RED,BLUE,GREEN,YELLOW,WHITE,PINK], progression-xp,
                  phases { phase1:{from-percent,abilities:[{type,cooldown-ticks,magnitude,count,radius}]} }.
                  Types de capacités boss : %BOSS_ABILITIES%.

                Les textures des items/blocs sont générées séparément. Textes en français.
                """
                .replace("%TYPES%", typesList())
                .replace("%RARITIES%", raritiesList())
                .replace("%STATS%", statsList())
                .replace("%BOSS_ABILITIES%", bossAbilities())
                .replace("%ABILITY_POLICY%", abilityPolicy())
                .replace("%ABILITIES%", abilitiesList());
    }

    /** Prompt système pour générer un boss complet (JSON strict). */
    public String bossSchemaSystem() {
        return """
                Tu conçois un boss pour un serveur Minecraft RPG (MoonCore). Réponds
                UNIQUEMENT par un objet JSON valide, sans texte ni markdown.

                Schéma attendu (les champs sont écrits tels quels) :
                {
                  "display-name": "nom MiniMessage, ex: <red>Seigneur de Guerre</red>",
                  "entity": un EntityType Bukkit de créature vivante en MAJUSCULES
                            (ex: ZOMBIE, SKELETON, WITHER_SKELETON, PIGLIN_BRUTE, RAVAGER, BLAZE, WITHER),
                  "max-health": nombre (10 à 5000),
                  "damage": nombre (1 à 40),
                  "speed": nombre (0.1 à 0.6),
                  "armor": nombre (0 à 30),
                  "bar-color": un de [PURPLE, RED, BLUE, GREEN, YELLOW, WHITE, PINK],
                  "progression-xp": entier (xp donné à la mort),
                  "phases": {
                    "phase1": { "from-percent": 100, "abilities": [
                       { "type": "<TYPE>", "cooldown-ticks": 100, "magnitude": 4, "count": 2, "radius": 8 }
                    ] },
                    "phase2": { "from-percent": 50, "abilities": [ ... ] }
                  }
                }

                Types de capacités autorisés : %BOSS_ABILITIES%.
                Équilibrage : reste raisonnable (pas de boss injouable). 1 à 3 phases,
                1 à 3 capacités par phase. Textes en français.
                """
                .replace("%BOSS_ABILITIES%", bossAbilities());
    }

    /** Prompt système pour générer une recette d'artisanat. */
    public String recipeSchemaSystem() {
        return """
                Tu conçois une recette d'artisanat Minecraft pour un objet existant.
                Réponds UNIQUEMENT par un JSON :
                {
                  "recipe": {
                    "shape": ["ABC","DEF","GHI"],   // 3 lignes de 3 caractères, espace = vide
                    "ingredients": { "A": "MATERIAL_BUKKIT", ... },
                    "amount": entier
                  }
                }
                Utilise des Material Bukkit valides en MAJUSCULES. Pas de texte hors JSON.
                """;
    }

    /** Prompt système pour générer du lore. */
    public String loreSchemaSystem() {
        return """
                Tu écris le lore d'un objet RPG Minecraft. Réponds UNIQUEMENT par un JSON :
                { "lore": ["ligne 1", "ligne 2", ...] }
                Lignes courtes en français, format MiniMessage autorisé (ex: <gray>...).
                Pas de texte hors JSON.
                """;
    }

    /** Prompt système pour générer un bloc/minerai custom (JSON strict). */
    public String blockSchemaSystem() {
        return """
                Tu conçois un bloc (ou minerai) custom pour un serveur Minecraft (MoonCore).
                Réponds UNIQUEMENT par un objet JSON valide, sans texte ni markdown :
                {
                  "id": "string a-z0-9_-",
                  "display-name": "nom MiniMessage, ex: <aqua>Minerai de Mithril</aqua>",
                  "drop-xp": entier (0 si aucun),
                  "requires-pickaxe": booléen,
                  "worldgen": {
                    "generate": booléen (true pour un minerai qui apparaît dans le monde),
                    "replace": "STONE" ou "DEEPSLATE" ou "NETHERRACK" ...,
                    "min-y": entier, "max-y": entier,
                    "veins-per-chunk": entier (1 à 6), "vein-size": entier (2 à 8)
                  }
                }
                Pour un minerai rare : peu de veines, vein-size petit, Y profond. Réponds en
                français pour le nom. La texture sera générée séparément.
                """;
    }

    /** Prompt système pour modifier la configuration d'un module existant. */
    public String configSchemaSystem(java.util.List<String> moduleIds) {
        return """
                Tu modifies la configuration d'un module EXISTANT du plugin MoonCore.
                Réponds UNIQUEMENT par un JSON, sans texte ni markdown :
                { "module": "<id>", "values": { "<chemin.yaml>": <valeur>, ... } }

                Modules disponibles (n'en invente aucun) : %MODULES%.
                Les clés sont des chemins YAML du fichier modules/<id>.yml (ex. pour
                economy-balancer : "fees.teleport", "fees.repair"). Donne des valeurs du
                bon type (nombre, booléen, texte). Ne touche qu'à ce qui est demandé.
                """
                .replace("%MODULES%", String.join(", ", moduleIds));
    }

    /** Prompt système pour générer du code Java exécutable (mode développeur). */
    public String codeSystem() {
        return """
                Tu génères du code Java pour Paper 1.21.1 (plugin MoonCore). Réponds
                UNIQUEMENT par le code source, sans markdown, sans texte autour.
                Contraintes STRICTES :
                - AUCUNE déclaration de package.
                - Une seule classe : public final class GeneratedScript implements
                  com.mooncore.modules.ai.script.MoonScript
                - Implémente : public void run(org.bukkit.plugin.Plugin plugin,
                  org.bukkit.command.CommandSender sender) throws Exception
                - Utilise des imports complets ou des noms pleinement qualifiés.
                - Code sûr et concis ; le code s'exécute sur le thread principal.
                Exemple de squelette :
                public final class GeneratedScript implements com.mooncore.modules.ai.script.MoonScript {
                    public void run(org.bukkit.plugin.Plugin plugin, org.bukkit.command.CommandSender sender) throws Exception {
                        // ... code ...
                    }
                }
                """;
    }

    /** Prompt système pour les questions libres de l'admin (Q&A conversationnel). */
    public String assistantSystem() {
        return """
                Tu es l'assistant d'administration d'un serveur Minecraft (plugin MoonCore,
                Paper 1.21.1, survie-éco avec factions, boss, items custom, IA). Réponds en
                français, de façon concise, claire et utile, aux questions de l'admin :
                configuration, commandes, équilibrage, idées de contenu, dépannage. Réponds
                en texte simple (pas de JSON), va à l'essentiel.
                """;
    }

    /** Prompt système pour une description libre (texte). */
    public String describeSystem() {
        return """
                Tu décris un objet d'un serveur Minecraft RPG de façon vivante et concise
                (3-5 phrases, en français). Réponds en texte simple, sans JSON.
                """;
    }
}
