package com.mooncore.modules.customitem;

import org.bukkit.Material;

import java.util.Locale;

/** Famille d'outil vanilla que doit imiter un item custom. */
public enum ToolKind {
    NONE("aucun"),
    SWORD("epee"),
    PICKAXE("pioche"),
    AXE("hache"),
    SHOVEL("pelle"),
    HOE("houe");

    private final String label;

    ToolKind(String label) {
        this.label = label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }

    public static ToolKind fromId(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        String s = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (s) {
            case "SWORD", "EPEE", "EPE", "WEAPON" -> SWORD;
            case "PICKAXE", "PICK", "PIOCHE" -> PICKAXE;
            case "AXE", "HACHE" -> AXE;
            case "SHOVEL", "SPADE", "PELLE" -> SHOVEL;
            case "HOE", "HOUE" -> HOE;
            case "NONE", "ANY", "HAND", "AUCUN" -> NONE;
            default -> NONE;
        };
    }

    public static ToolKind fromMaterial(Material material) {
        if (material == null) return NONE;
        String n = material.name();
        if (n.endsWith("_SWORD")) return SWORD;
        if (n.endsWith("_PICKAXE")) return PICKAXE;
        if (n.endsWith("_AXE")) return AXE;
        if (n.endsWith("_SHOVEL")) return SHOVEL;
        if (n.endsWith("_HOE")) return HOE;
        return NONE;
    }

    public static ToolKind fromText(String text) {
        if (text == null) return NONE;
        String s = text.toLowerCase(Locale.ROOT);
        if (s.contains("hache") || s.contains("axe")) return AXE;
        if (s.contains("pelle") || s.contains("shovel")) return SHOVEL;
        if (s.contains("pioche") || s.contains("pickaxe")) return PICKAXE;
        if (s.contains("houe") || s.contains("hoe")) return HOE;
        if (s.contains("epee") || s.contains("epée") || s.contains("sword") || s.contains("lame")) return SWORD;
        return NONE;
    }
}
