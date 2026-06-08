package com.mooncore.modules.customitem;

import com.mooncore.api.customitem.CustomItemManagerService.CustomItemView;
import com.mooncore.api.customitem.ItemType;
import com.mooncore.api.customitem.Rarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Définition mutable d'un objet custom. Persistée en YAML dans {@code items/<id>.yml}.
 * Éditable en jeu via {@code /moon item ...}. Le rendu visuel (modèle/texture) est
 * optionnel : les stats et capacités vivent dans la PDC et le lore, donc l'objet
 * reste pleinement fonctionnel sur Bedrock même sans resource pack.
 */
public final class CustomItemDef implements CustomItemView {

    /** Référence d'une capacité portée par l'objet. */
    public record AbilityRef(String id, int level) {}

    /** Règle de drop : source = "boss:<id>" | "mob:<ENTITY_TYPE>" | "event:<id>". */
    public record DropRule(String source, double chance, int min, int max) {}

    /** Ingredient de recette : material vanilla ou item custom exact. */
    public record RecipeIngredient(Material material, String customItemId) {
        public RecipeIngredient {
            if (customItemId != null) {
                customItemId = customItemId.toLowerCase(Locale.ROOT).trim();
                if (customItemId.isBlank()) customItemId = null;
            }
        }

        public static RecipeIngredient material(Material material) {
            return new RecipeIngredient(material, null);
        }

        public static RecipeIngredient custom(String customItemId) {
            return new RecipeIngredient(null, customItemId);
        }

        public static RecipeIngredient parse(String raw) {
            if (raw == null || raw.isBlank()) return null;
            String value = raw.trim();
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.startsWith("custom:")) return custom(value.substring("custom:".length()));
            if (lower.startsWith("item:")) return custom(value.substring("item:".length()));
            Material mat = matchMaterial(value);
            return mat == null || !mat.isItem() ? null : material(mat);
        }

        public boolean isCustom() {
            return customItemId != null;
        }

        public String storageKey() {
            return isCustom() ? "custom:" + customItemId : material.name();
        }

        @Override
        public String toString() {
            return storageKey();
        }
    }

    public static final class Recipe {
        public boolean shaped = true;
        public List<String> shape = new ArrayList<>(List.of("   ", "   ", "   "));
        public Map<Character, RecipeIngredient> ingredients = new LinkedHashMap<>();
        public int amount = 1;

        public boolean isEmpty() {
            return ingredients.isEmpty();
        }
    }

    private final String id;
    private String displayName;
    private ItemType type = ItemType.WEAPON;
    private Rarity rarity = Rarity.COMMON;
    private Material material = Material.IRON_SWORD;
    private ToolKind toolKind = ToolKind.NONE;
    private ToolTier toolTier = ToolTier.HAND;
    private int customModelData = 0;             // 0 = aucun (rendu vanilla)
    private String modelKey = null;              // clé de texture (resource pack)
    private boolean glowing = false;
    private boolean unbreakable = false;
    private final List<String> lore = new ArrayList<>();
    private final Map<String, Double> stats = new LinkedHashMap<>();
    private final List<AbilityRef> abilities = new ArrayList<>();
    private final List<DropRule> drops = new ArrayList<>();
    private Recipe recipe = null;

    public CustomItemDef(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.displayName = "<white>" + id + "</white>";
    }

    // ---- CustomItemView ----
    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public ItemType type() { return type; }
    @Override public Rarity rarity() { return rarity; }

    // ---- Accesseurs / mutateurs ----
    public void setDisplayName(String name) { this.displayName = name; }
    public void setType(ItemType type) { this.type = type; }
    public void setRarity(Rarity rarity) { this.rarity = rarity; }
    public Material material() { return material; }
    public void setMaterial(Material material) {
        this.material = material;
        inferToolFromMaterial(material);
    }
    public ToolKind toolKind() { return toolKind; }
    public ToolTier toolTier() { return toolTier; }
    public void setToolKind(ToolKind kind) { setTool(kind, toolTier == ToolTier.HAND ? ToolTier.IRON : toolTier); }
    public void setToolTier(ToolTier tier) { setTool(toolKind, tier); }
    public void setTool(ToolKind kind, ToolTier tier) {
        this.toolKind = kind == null ? ToolKind.NONE : kind;
        this.toolTier = tier == null ? ToolTier.HAND : tier;
        if (this.toolKind == ToolKind.NONE) {
            this.toolTier = ToolTier.HAND;
            return;
        }
        if (this.toolTier == ToolTier.HAND) this.toolTier = ToolTier.IRON;
        Material toolMaterial = this.toolTier.materialFor(this.toolKind);
        if (toolMaterial != null) this.material = toolMaterial;
        this.type = this.toolKind == ToolKind.SWORD
                ? com.mooncore.api.customitem.ItemType.WEAPON
                : com.mooncore.api.customitem.ItemType.TOOL;
    }
    public int customModelData() { return customModelData; }
    public void setCustomModelData(int cmd) { this.customModelData = cmd; }
    public String modelKey() { return modelKey; }
    public void setModelKey(String key) { this.modelKey = key; }
    public boolean glowing() { return glowing; }
    public void setGlowing(boolean glowing) { this.glowing = glowing; }
    public boolean unbreakable() { return unbreakable; }
    public void setUnbreakable(boolean unbreakable) { this.unbreakable = unbreakable; }
    public List<String> lore() { return lore; }
    public Map<String, Double> stats() { return stats; }
    public List<AbilityRef> abilities() { return abilities; }
    public List<DropRule> drops() { return drops; }
    public Recipe recipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }

    public void setStat(String key, double value) {
        stats.put(key.toLowerCase(Locale.ROOT), value);
    }
    public void removeStat(String key) { stats.remove(key.toLowerCase(Locale.ROOT)); }

    public void addAbility(String abilityId, int level) {
        String norm = abilityId.toLowerCase(Locale.ROOT);
        abilities.removeIf(a -> a.id().equals(norm));
        abilities.add(new AbilityRef(norm, Math.max(1, level)));
    }
    public boolean removeAbility(String abilityId) {
        String norm = abilityId.toLowerCase(Locale.ROOT);
        return abilities.removeIf(a -> a.id().equals(norm));
    }

    private void inferToolFromMaterial(Material material) {
        ToolKind kind = ToolKind.fromMaterial(material);
        if (kind == ToolKind.NONE) {
            this.toolKind = ToolKind.NONE;
            this.toolTier = ToolTier.HAND;
            return;
        }
        this.toolKind = kind;
        this.toolTier = ToolTier.fromMaterial(material);
        this.type = kind == ToolKind.SWORD ? ItemType.WEAPON : ItemType.TOOL;
    }

    /** Copie profonde sous un nouvel id (pour /moon item clone). */
    public CustomItemDef cloneAs(String newId) {
        CustomItemDef c = new CustomItemDef(newId);
        c.displayName = this.displayName;
        c.type = this.type;
        c.rarity = this.rarity;
        c.material = this.material;
        c.toolKind = this.toolKind;
        c.toolTier = this.toolTier;
        c.customModelData = this.customModelData;
        c.modelKey = this.modelKey;
        c.glowing = this.glowing;
        c.unbreakable = this.unbreakable;
        c.lore.addAll(this.lore);
        c.stats.putAll(this.stats);
        c.abilities.addAll(this.abilities);
        c.drops.addAll(this.drops);
        if (this.recipe != null) {
            Recipe r = new Recipe();
            r.shaped = this.recipe.shaped;
            r.shape = new ArrayList<>(this.recipe.shape);
            r.ingredients = new LinkedHashMap<>(this.recipe.ingredients);
            r.amount = this.recipe.amount;
            c.recipe = r;
        }
        return c;
    }

    // ---- Sérialisation YAML ----

    public void save(ConfigurationSection s) {
        s.set("display-name", displayName);
        s.set("type", type.id());
        s.set("rarity", rarity.id());
        s.set("material", material.name());
        s.set("tool-kind", toolKind.id());
        s.set("tool-tier", toolTier.id());
        s.set("custom-model-data", customModelData);
        s.set("model-key", modelKey);
        s.set("glowing", glowing);
        s.set("unbreakable", unbreakable);
        s.set("lore", new ArrayList<>(lore));

        ConfigurationSection statsSec = s.createSection("stats");
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            statsSec.set(e.getKey(), e.getValue());
        }

        List<Map<String, Object>> abilityList = new ArrayList<>();
        for (AbilityRef a : abilities) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id());
            m.put("level", a.level());
            abilityList.add(m);
        }
        s.set("abilities", abilityList);

        List<Map<String, Object>> dropList = new ArrayList<>();
        for (DropRule d : drops) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", d.source());
            m.put("chance", d.chance());
            m.put("min", d.min());
            m.put("max", d.max());
            dropList.add(m);
        }
        s.set("drops", dropList);

        if (recipe != null && !recipe.isEmpty()) {
            ConfigurationSection rs = s.createSection("recipe");
            rs.set("shaped", recipe.shaped);
            rs.set("shape", recipe.shape);
            rs.set("amount", recipe.amount);
            ConfigurationSection ing = rs.createSection("ingredients");
            for (Map.Entry<Character, RecipeIngredient> e : recipe.ingredients.entrySet()) {
                ing.set(String.valueOf(e.getKey()), e.getValue().storageKey());
            }
        } else {
            s.set("recipe", null);
        }
    }

    public static CustomItemDef load(String id, ConfigurationSection s) {
        CustomItemDef d = new CustomItemDef(id);
        d.displayName = s.getString("display-name", d.displayName);
        ItemType t = ItemType.fromId(s.getString("type", "weapon"));
        if (t != null) d.type = t;
        Rarity r = Rarity.fromId(s.getString("rarity", "common"));
        if (r != null) d.rarity = r;
        Material mat = matchMaterial(s.getString("material", "IRON_SWORD"));
        if (mat != null) d.setMaterial(mat);
        ToolKind savedKind = ToolKind.fromId(s.getString("tool-kind", d.toolKind.id()));
        ToolTier savedTier = ToolTier.fromId(s.getString("tool-tier", d.toolTier.id()));
        if (savedKind != ToolKind.NONE) d.setTool(savedKind, savedTier == ToolTier.HAND ? ToolTier.IRON : savedTier);
        else if (s.contains("tool-kind")) d.setTool(ToolKind.NONE, ToolTier.HAND);
        d.customModelData = s.getInt("custom-model-data", 0);
        d.modelKey = s.getString("model-key", null);
        d.glowing = s.getBoolean("glowing", false);
        d.unbreakable = s.getBoolean("unbreakable", false);
        d.lore.addAll(s.getStringList("lore"));

        ConfigurationSection statsSec = s.getConfigurationSection("stats");
        if (statsSec != null) {
            for (String key : statsSec.getKeys(false)) {
                d.stats.put(key.toLowerCase(Locale.ROOT), statsSec.getDouble(key));
            }
        }

        for (Map<?, ?> m : s.getMapList("abilities")) {
            Object aid = m.get("id");
            if (aid == null) continue;
            int lvl = m.get("level") instanceof Number n ? n.intValue() : 1;
            d.abilities.add(new AbilityRef(String.valueOf(aid).toLowerCase(Locale.ROOT), Math.max(1, lvl)));
        }

        for (Map<?, ?> m : s.getMapList("drops")) {
            Object src = m.get("source");
            if (src == null) continue;
            double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 0.0;
            int min = m.get("min") instanceof Number n ? n.intValue() : 1;
            int max = m.get("max") instanceof Number n ? n.intValue() : 1;
            d.drops.add(new DropRule(String.valueOf(src), chance, min, max));
        }

        ConfigurationSection rs = s.getConfigurationSection("recipe");
        if (rs != null) {
            Recipe rec = new Recipe();
            rec.shaped = rs.getBoolean("shaped", true);
            List<String> shape = rs.getStringList("shape");
            if (!shape.isEmpty()) rec.shape = shape;
            rec.amount = rs.getInt("amount", 1);
            ConfigurationSection ing = rs.getConfigurationSection("ingredients");
            if (ing != null) {
                for (String k : ing.getKeys(false)) {
                    RecipeIngredient ingredient = RecipeIngredient.parse(ing.getString(k));
                    if (ingredient != null && !k.isEmpty()) rec.ingredients.put(k.charAt(0), ingredient);
                }
            }
            d.recipe = rec;
        }
        return d;
    }

    private static Material matchMaterial(String name) {
        if (name == null) return null;
        return Material.matchMaterial(name.toUpperCase(Locale.ROOT));
    }
}
