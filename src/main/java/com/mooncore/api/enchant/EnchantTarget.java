package com.mooncore.api.enchant;

import org.bukkit.Material;

import java.util.Locale;

/** Catégorie d'objet sur laquelle un enchantement peut s'appliquer. */
public enum EnchantTarget {
    MELEE_WEAPON,   // épées + haches
    AXE,
    PICKAXE,
    MINING_TOOL,    // pioche/pelle/hache
    ARMOR,          // toute pièce d'armure
    BOOTS,
    HELMET,
    BOW;

    public boolean matches(Material m) {
        if (m == null) return false;
        String n = m.name().toUpperCase(Locale.ROOT);
        return switch (this) {
            case MELEE_WEAPON -> n.endsWith("_SWORD") || n.endsWith("_AXE");
            case AXE -> n.endsWith("_AXE");
            case PICKAXE -> n.endsWith("_PICKAXE");
            case MINING_TOOL -> n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_AXE");
            case ARMOR -> n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                    || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
            case BOOTS -> n.endsWith("_BOOTS");
            case HELMET -> n.endsWith("_HELMET");
            case BOW -> m == Material.BOW || m == Material.CROSSBOW;
        };
    }
}
