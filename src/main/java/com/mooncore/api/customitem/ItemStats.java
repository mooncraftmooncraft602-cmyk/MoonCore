package com.mooncore.api.customitem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catalogue des statistiques d'objet. Le système est <b>extensible</b> : une stat
 * est simplement une clé {@code String} stockée dans la définition de l'objet, donc
 * un admin peut introduire une stat custom sans toucher au code. Cette classe ne
 * fait que recenser les stats connues (libellé, unité, application vanilla éventuelle).
 */
public final class ItemStats {

    public static final String DAMAGE = "damage";
    public static final String HEALTH = "health";
    public static final String ARMOR = "armor";
    public static final String ARMOR_TOUGHNESS = "armor_toughness";
    public static final String MOVEMENT_SPEED = "movement_speed";
    public static final String ATTACK_SPEED = "attack_speed";
    public static final String CRIT_CHANCE = "crit_chance";       // %
    public static final String CRIT_DAMAGE = "crit_damage";       // multiplicateur additionnel (%)
    public static final String LIFE_STEAL = "life_steal";         // % des dégâts rendus en PV
    public static final String MANA = "mana";
    public static final String MANA_REGEN = "mana_regen";
    public static final String COOLDOWN_REDUCTION = "cooldown_reduction"; // %
    public static final String LUCK = "luck";
    public static final String MINING_SPEED = "mining_speed";
    public static final String HARVEST_BONUS = "harvest_bonus";   // % drop bonus récoltes
    public static final String BOSS_DAMAGE = "boss_damage";       // % dégâts vs boss
    public static final String PVP_DAMAGE = "pvp_damage";         // % dégâts vs joueurs
    public static final String PVE_DAMAGE = "pve_damage";         // % dégâts vs créatures

    /** Métadonnées d'affichage/d'application d'une stat connue. */
    public record StatMeta(String key, String label, boolean percent, String vanillaAttribute) {}

    private static final Map<String, StatMeta> KNOWN = new LinkedHashMap<>();

    private static void def(String key, String label, boolean percent, String vanillaAttribute) {
        KNOWN.put(key, new StatMeta(key, label, percent, vanillaAttribute));
    }

    static {
        // Clés courtes (registry moderne) ; Attrs.byKey gère le repli legacy generic.*
        def(DAMAGE, "Dégâts", false, "attack_damage");
        def(HEALTH, "Vie", false, "max_health");
        def(ARMOR, "Armure", false, "armor");
        def(ARMOR_TOUGHNESS, "Résistance d'armure", false, "armor_toughness");
        def(MOVEMENT_SPEED, "Vitesse", true, "movement_speed");
        def(ATTACK_SPEED, "Vitesse d'attaque", false, "attack_speed");
        def(CRIT_CHANCE, "Chance de critique", true, null);
        def(CRIT_DAMAGE, "Dégâts critiques", true, null);
        def(LIFE_STEAL, "Vol de vie", true, null);
        def(MANA, "Mana", false, null);
        def(MANA_REGEN, "Régén. mana", false, null);
        def(COOLDOWN_REDUCTION, "Réduction de cooldown", true, null);
        def(LUCK, "Chance", false, "luck");
        def(MINING_SPEED, "Vitesse de minage", true, null);
        def(HARVEST_BONUS, "Bonus de récolte", true, null);
        def(BOSS_DAMAGE, "Dégâts vs boss", true, null);
        def(PVP_DAMAGE, "Dégâts PvP", true, null);
        def(PVE_DAMAGE, "Dégâts PvE", true, null);
    }

    private ItemStats() {}

    /** Métadonnées d'une stat connue, ou {@code null} si custom/inconnue. */
    public static StatMeta meta(String key) {
        return key == null ? null : KNOWN.get(key.toLowerCase(java.util.Locale.ROOT));
    }

    /** Libellé lisible (fallback : la clé telle quelle). */
    public static String label(String key) {
        StatMeta m = meta(key);
        return m != null ? m.label() : key;
    }

    public static boolean isPercent(String key) {
        StatMeta m = meta(key);
        return m != null && m.percent();
    }

    public static Map<String, StatMeta> known() {
        return java.util.Collections.unmodifiableMap(KNOWN);
    }
}
