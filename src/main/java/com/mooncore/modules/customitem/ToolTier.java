package com.mooncore.modules.customitem;

import org.bukkit.Material;

import java.util.Locale;

/** Tier vanilla d'un outil custom. */
public enum ToolTier {
    HAND(0, "hand", ""),
    WOOD(1, "bois", "WOODEN"),
    GOLD(1, "or", "GOLDEN"),
    STONE(2, "pierre", "STONE"),
    IRON(3, "fer", "IRON"),
    DIAMOND(4, "diamant", "DIAMOND"),
    NETHERITE(5, "netherite", "NETHERITE");

    private final int level;
    private final String label;
    private final String materialPrefix;

    ToolTier(int level, String label, String materialPrefix) {
        this.level = level;
        this.label = label;
        this.materialPrefix = materialPrefix;
    }

    public int level() {
        return level;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    public Material materialFor(ToolKind kind) {
        if (kind == null || kind == ToolKind.NONE || this == HAND) return null;
        return Material.matchMaterial(materialPrefix + "_" + switch (kind) {
            case SWORD -> "SWORD";
            case PICKAXE -> "PICKAXE";
            case AXE -> "AXE";
            case SHOVEL -> "SHOVEL";
            case HOE -> "HOE";
            case NONE -> "";
        });
    }

    public static ToolTier fromId(String raw) {
        if (raw == null || raw.isBlank()) return HAND;
        String s = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (s) {
            case "WOOD", "WOODEN", "BOIS" -> WOOD;
            case "GOLD", "GOLDEN", "OR" -> GOLD;
            case "STONE", "PIERRE" -> STONE;
            case "IRON", "FER" -> IRON;
            case "DIAMOND", "DIAMANT" -> DIAMOND;
            case "NETHERITE" -> NETHERITE;
            default -> HAND;
        };
    }

    public static ToolTier fromMaterial(Material material) {
        if (material == null) return HAND;
        String n = material.name();
        if (n.startsWith("WOODEN_")) return WOOD;
        if (n.startsWith("GOLDEN_")) return GOLD;
        if (n.startsWith("STONE_")) return STONE;
        if (n.startsWith("IRON_")) return IRON;
        if (n.startsWith("DIAMOND_")) return DIAMOND;
        if (n.startsWith("NETHERITE_")) return NETHERITE;
        return HAND;
    }
}
