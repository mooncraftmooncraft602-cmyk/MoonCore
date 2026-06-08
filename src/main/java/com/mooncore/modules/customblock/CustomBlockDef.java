package com.mooncore.modules.customblock;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Définition d'un bloc custom (rendu via un état de note_block + resource pack).
 * Optionnellement gén' comme un minerai dans les chunks nouvellement générés.
 */
public final class CustomBlockDef {

    private final String id;
    private String displayName;
    private String modelKey;            // clé de texture (blocks-textures/<key>.png) — toutes faces par défaut
    private String textureTop;          // faces séparées optionnelles (clés de texture)
    private String textureSide;
    private String textureBottom;
    private int stateIndex = -1;        // état note-block assigné (stable, persistant)
    private String dropItemId;          // id d'item custom à drop (null = se drop lui-même)
    private int dropXp = 0;
    private boolean requiresPickaxe = true;

    // Worldgen (génération de minerai dans les nouveaux chunks).
    private boolean generate = false;
    private Material replace = Material.STONE;
    private int minY = -16, maxY = 64;
    private int veinsPerChunk = 2;
    private int veinSize = 4;

    public CustomBlockDef(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = "<white>" + id + "</white>";
        this.modelKey = this.id;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public void setDisplayName(String n) { this.displayName = n; }
    public String modelKey() { return modelKey; }
    public void setModelKey(String k) { this.modelKey = k; }

    /** True si des faces distinctes (top/side/bottom) sont définies. */
    public boolean hasFaces() { return textureTop != null || textureSide != null || textureBottom != null; }
    public String textureTop() { return textureTop != null ? textureTop : modelKey; }
    public String textureSide() { return textureSide != null ? textureSide : modelKey; }
    public String textureBottom() { return textureBottom != null ? textureBottom : (textureSide != null ? textureSide : modelKey); }
    public void setTextureTop(String k) { this.textureTop = k; }
    public void setTextureSide(String k) { this.textureSide = k; }
    public void setTextureBottom(String k) { this.textureBottom = k; }
    public int stateIndex() { return stateIndex; }
    public void setStateIndex(int i) { this.stateIndex = i; }
    public String dropItemId() { return dropItemId; }
    public void setDropItemId(String d) { this.dropItemId = d; }
    public int dropXp() { return dropXp; }
    public void setDropXp(int x) { this.dropXp = x; }
    public boolean requiresPickaxe() { return requiresPickaxe; }
    public void setRequiresPickaxe(boolean b) { this.requiresPickaxe = b; }

    public boolean generate() { return generate; }
    public void setGenerate(boolean b) { this.generate = b; }
    public Material replace() { return replace; }
    public void setReplace(Material m) { this.replace = m; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public void setYRange(int min, int max) { this.minY = min; this.maxY = max; }
    public int veinsPerChunk() { return veinsPerChunk; }
    public void setVeinsPerChunk(int v) { this.veinsPerChunk = v; }
    public int veinSize() { return veinSize; }
    public void setVeinSize(int v) { this.veinSize = v; }

    public void save(ConfigurationSection s) {
        s.set("display-name", displayName);
        s.set("model-key", modelKey);
        s.set("texture-top", textureTop);
        s.set("texture-side", textureSide);
        s.set("texture-bottom", textureBottom);
        s.set("state-index", stateIndex);
        s.set("drop-item", dropItemId);
        s.set("drop-xp", dropXp);
        s.set("requires-pickaxe", requiresPickaxe);
        ConfigurationSection g = s.createSection("worldgen");
        g.set("generate", generate);
        g.set("replace", replace.name());
        g.set("min-y", minY);
        g.set("max-y", maxY);
        g.set("veins-per-chunk", veinsPerChunk);
        g.set("vein-size", veinSize);
    }

    public static CustomBlockDef load(String id, ConfigurationSection s) {
        CustomBlockDef d = new CustomBlockDef(id);
        d.displayName = s.getString("display-name", d.displayName);
        d.modelKey = s.getString("model-key", d.id);
        d.textureTop = s.getString("texture-top", null);
        d.textureSide = s.getString("texture-side", null);
        d.textureBottom = s.getString("texture-bottom", null);
        d.stateIndex = s.getInt("state-index", -1);
        d.dropItemId = s.getString("drop-item", null);
        d.dropXp = s.getInt("drop-xp", 0);
        d.requiresPickaxe = s.getBoolean("requires-pickaxe", true);
        ConfigurationSection g = s.getConfigurationSection("worldgen");
        if (g != null) {
            d.generate = g.getBoolean("generate", false);
            Material m = Material.matchMaterial(g.getString("replace", "STONE").toUpperCase(Locale.ROOT));
            if (m != null) d.replace = m;
            d.minY = g.getInt("min-y", -16);
            d.maxY = g.getInt("max-y", 64);
            d.veinsPerChunk = g.getInt("veins-per-chunk", 2);
            d.veinSize = g.getInt("vein-size", 4);
        }
        return d;
    }
}
