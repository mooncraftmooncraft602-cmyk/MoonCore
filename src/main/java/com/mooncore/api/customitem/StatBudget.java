package com.mooncore.api.customitem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plafonds par stat (anti-cheat / équilibrage), partagés par l'IA, la GUI d'édition et
 * la CLI. Valeurs = maximum raisonnable d'un objet LEGENDARY/ANCIENT bien équilibré.
 */
public final class StatBudget {

    private StatBudget() {}

    /** Plafond par défaut (par stat) ; valeur de repli pour une stat inconnue. */
    public static final double DEFAULT_CAP = 100.0;

    /** Copie modifiable des plafonds par défaut. */
    public static Map<String, Double> defaults() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("damage", 15.0);
        m.put("health", 20.0);
        m.put("armor", 20.0);
        m.put("armor_toughness", 12.0);
        m.put("movement_speed", 30.0);
        m.put("attack_speed", 4.0);
        m.put("crit_chance", 50.0);
        m.put("crit_damage", 150.0);
        m.put("life_steal", 20.0);
        m.put("mana", 100.0);
        m.put("mana_regen", 50.0);
        m.put("cooldown_reduction", 40.0);
        m.put("luck", 10.0);
        m.put("mining_speed", 100.0);
        m.put("harvest_bonus", 100.0);
        m.put("boss_damage", 75.0);
        m.put("pvp_damage", 50.0);
        m.put("pve_damage", 50.0);
        return m;
    }

    /** Plafond d'une stat selon une table (repli {@link #DEFAULT_CAP}). */
    public static double cap(Map<String, Double> caps, String stat) {
        return caps.getOrDefault(stat.toLowerCase(java.util.Locale.ROOT), DEFAULT_CAP);
    }

    /** Borne une valeur dans [-cap, cap]. */
    public static double clamp(Map<String, Double> caps, String stat, double value) {
        double c = cap(caps, stat);
        return Math.max(-c, Math.min(c, value));
    }
}
