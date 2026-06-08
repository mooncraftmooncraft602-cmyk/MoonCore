package com.mooncore.api.customitem;

import java.util.Locale;

/**
 * Catégories d'objets custom. Détermine le comportement par défaut (équipement,
 * application des stats d'attaque/défense, slot d'armure, etc.).
 */
public enum ItemType {

    WEAPON(true, false),
    TOOL(true, false),
    ARMOR(false, true),
    ACCESSORY(false, false),
    RELIC(false, false),
    ARTIFACT(false, false),
    BOSS_ITEM(true, false),
    CONSUMABLE(false, false),
    EVENT_ITEM(false, false);

    /** Stats offensives appliquées quand l'objet est tenu en main. */
    private final boolean heldStats;
    /** Stats défensives appliquées quand l'objet est porté (armure). */
    private final boolean wornStats;

    ItemType(boolean heldStats, boolean wornStats) {
        this.heldStats = heldStats;
        this.wornStats = wornStats;
    }

    public boolean appliesHeld() { return heldStats; }
    public boolean appliesWorn() { return wornStats; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static ItemType fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
