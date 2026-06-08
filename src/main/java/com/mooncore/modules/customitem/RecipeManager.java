package com.mooncore.modules.customitem;

import com.mooncore.MoonCore;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enregistre/retire les recettes d'artisanat des objets custom auprès du serveur.
 * Une recette utilise une clé {@code ci_recipe_<id>} pour pouvoir être retirée
 * proprement au reload/disable.
 */
public final class RecipeManager {

    private final MoonCore plugin;
    private final CustomItemManagerModule module;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public RecipeManager(MoonCore plugin, CustomItemManagerModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void registerAll() {
        for (CustomItemDef def : module.rawDefs().values()) {
            register(def);
        }
    }

    public boolean register(CustomItemDef def) {
        CustomItemDef.Recipe r = def.recipe();
        if (r == null || r.isEmpty()) return false;
        ItemStack result = module.buildItem(def, Math.max(1, r.amount));
        NamespacedKey key = new NamespacedKey(plugin, "ci_recipe_" + def.id());
        try {
            if (r.shaped) {
                ShapedRecipe recipe = new ShapedRecipe(key, result);
                List<String> shape = normalizeShape(r.shape);
                recipe.shape(shape.toArray(new String[0]));
                for (Map.Entry<Character, org.bukkit.Material> e : r.ingredients.entrySet()) {
                    recipe.setIngredient(e.getKey(), new RecipeChoice.MaterialChoice(e.getValue()));
                }
                plugin.getServer().addRecipe(recipe);
            } else {
                ShapelessRecipe recipe = new ShapelessRecipe(key, result);
                for (org.bukkit.Material m : r.ingredients.values()) {
                    recipe.addIngredient(new RecipeChoice.MaterialChoice(m));
                }
                plugin.getServer().addRecipe(recipe);
            }
            registered.add(key);
            return true;
        } catch (Exception e) {
            plugin.logger().warn("Recette invalide pour l'objet custom " + def.id() + " : " + e.getMessage());
            return false;
        }
    }

    /** Garantit exactement 3 lignes de 3 caractères (les espaces = slots vides). */
    private static List<String> normalizeShape(List<String> shape) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String row = i < shape.size() ? shape.get(i) : "   ";
            if (row.length() > 3) row = row.substring(0, 3);
            while (row.length() < 3) row += " ";
            out.add(row);
        }
        return out;
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            plugin.getServer().removeRecipe(key);
        }
        registered.clear();
    }
}
