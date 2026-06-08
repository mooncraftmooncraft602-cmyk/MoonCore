package com.mooncore.api.customitem;

import java.util.Locale;

/**
 * Raretés des objets custom, du plus commun au plus rare. La couleur et le poids
 * (probabilité relative dans les loots) sont surchargeables via
 * {@code modules/custom-item.yml > rarities.<id>}.
 */
public enum Rarity {

    COMMON("Commun", "<gray>", 100.0),
    UNCOMMON("Peu commun", "<green>", 60.0),
    RARE("Rare", "<aqua>", 30.0),
    EPIC("Épique", "<light_purple>", 15.0),
    LEGENDARY("Légendaire", "<gold>", 6.0),
    MYTHIC("Mythique", "<red>", 2.0),
    DIVINE("Divin", "<gradient:#ffd700:#fff8b0>", 0.6),
    ANCIENT("Ancien", "<gradient:#00ffd0:#7d5fff>", 0.2);

    private final String defaultLabel;
    private final String defaultColor;
    private final double defaultWeight;

    Rarity(String defaultLabel, String defaultColor, double defaultWeight) {
        this.defaultLabel = defaultLabel;
        this.defaultColor = defaultColor;
        this.defaultWeight = defaultWeight;
    }

    public String defaultLabel() { return defaultLabel; }
    public String defaultColor() { return defaultColor; }
    public double defaultWeight() { return defaultWeight; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** Parse insensible à la casse ; {@code null} si inconnu. */
    public static Rarity fromId(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
