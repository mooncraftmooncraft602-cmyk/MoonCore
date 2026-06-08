package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Éditeur de recette shaped 3x3 avec placement direct d'items dans la grille. */
public final class RecipeEditorMenu implements InventoryHolder {

    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT = 24;
    private static final int SAVE = 48;
    private static final int CLEAR = 50;
    private static final int AI = 51;
    private static final int BACK = 49;

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String itemId;
    private final ItemStack[] initialGrid;
    private Inventory inv;

    private RecipeEditorMenu(MoonCore plugin, ChatInput chat, String itemId, ItemStack[] initialGrid) {
        this.plugin = plugin;
        this.chat = chat;
        this.itemId = itemId;
        this.initialGrid = initialGrid;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String itemId) {
        open(plugin, chat, p, itemId, null);
    }

    private static void open(MoonCore plugin, ChatInput chat, Player p, String itemId, ItemStack[] initialGrid) {
        RecipeEditorMenu menu = new RecipeEditorMenu(plugin, chat, itemId, initialGrid);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Recette</gradient> <dark_gray>» <white>" + itemId));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        for (int slot : GRID) inv.setItem(slot, null);
        CustomItemManagerModule module = items();
        CustomItemDef def = module == null ? null : module.rawDef(itemId);
        if (module != null && def != null) {
            inv.setItem(RESULT, module.buildItem(def, Math.max(1, def.recipe() == null ? 1 : def.recipe().amount)));
            loadRecipe(def);
        }
        if (initialGrid != null) {
            for (int i = 0; i < GRID.length && i < initialGrid.length; i++) {
                inv.setItem(GRID[i], initialGrid[i] == null ? null : initialGrid[i].clone());
            }
        }
        inv.setItem(14, StudioItems.btn(Material.ARROW, "<gray>Résultat"));
        inv.setItem(SAVE, StudioItems.btn(Material.EMERALD_BLOCK, "<green>Sauvegarder",
                "<gray>les ingrédients sont lus comme matériaux vanilla"));
        inv.setItem(BACK, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour recettes"));
        inv.setItem(CLEAR, StudioItems.btn(Material.TNT, "<red>Vider recette"));
        inv.setItem(AI, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Générer IA"));
    }

    private void loadRecipe(CustomItemDef def) {
        if (def.recipe() == null || def.recipe().isEmpty()) return;
        Map<Character, Material> ing = def.recipe().ingredients;
        for (int row = 0; row < Math.min(3, def.recipe().shape.size()); row++) {
            String line = def.recipe().shape.get(row);
            for (int col = 0; col < Math.min(3, line.length()); col++) {
                char c = line.charAt(col);
                Material mat = ing.get(c);
                if (mat != null && mat.isItem()) inv.setItem(GRID[row * 3 + col], new ItemStack(mat));
            }
        }
    }

    void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean top = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());
        int slot = e.getRawSlot();
        e.setCancelled(true);
        if (!top) return;
        if (isGrid(slot)) {
            editGridSlot(p, slot, e.isRightClick(), e.isShiftClick());
            return;
        }
        switch (slot) {
            case SAVE -> save(p);
            case CLEAR -> clearRecipe(p);
            case AI -> { p.closeInventory(); p.performCommand("moon ai createrecipe " + itemId + " recette simple et equilibree avec ingredients vanilla"); }
            case BACK -> StudioRecipeMenu.open(plugin, chat, p, 0);
            default -> { }
        }
    }

    void drag(InventoryDragEvent e) {
        e.setCancelled(true);
    }

    private void editGridSlot(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (rightClick) {
            inv.setItem(slot, null);
            return;
        }
        if (shiftClick) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && !hand.getType().isAir()) inv.setItem(slot, new ItemStack(hand.getType()));
            else p.sendMessage(Text.mm("<red>Tiens un item en main pour remplir cette case."));
            return;
        }
        p.closeInventory();
        ItemStack[] current = snapshotGrid();
        chat.request(p, "<yellow>Matériau pour cette case (ex <white>DIAMOND</white>) :", in -> {
            Material mat = Material.matchMaterial(in.toUpperCase(java.util.Locale.ROOT));
            if (mat == null || !mat.isItem()) {
                p.sendMessage(Text.mm("<red>Materiau invalide : " + in));
                open(plugin, chat, p, itemId, current);
                return;
            }
            if (mat == null || !mat.isItem()) p.sendMessage(Text.mm("<red>Matériau invalide : " + in));
            else {
                int idx = gridIndex(slot);
                if (idx >= 0) current[idx] = new ItemStack(mat);
                open(plugin, chat, p, itemId, current);
            }
        });
    }

    private ItemStack[] snapshotGrid() {
        ItemStack[] out = new ItemStack[GRID.length];
        for (int i = 0; i < GRID.length; i++) {
            ItemStack it = inv.getItem(GRID[i]);
            out[i] = it == null ? null : it.clone();
        }
        return out;
    }

    private void save(Player p) {
        CustomItemManagerModule module = items();
        CustomItemDef def = module == null ? null : module.rawDef(itemId);
        if (def == null) return;
        CustomItemDef.Recipe recipe = new CustomItemDef.Recipe();
        Map<Material, Character> chars = new LinkedHashMap<>();
        char next = 'A';
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                ItemStack it = inv.getItem(GRID[row * 3 + col]);
                if (it == null || it.getType().isAir()) {
                    line.append(' ');
                    continue;
                }
                Material mat = it.getType();
                Character c = chars.get(mat);
                if (c == null) {
                    c = next++;
                    chars.put(mat, c);
                    recipe.ingredients.put(c, mat);
                }
                line.append(c);
            }
            recipe.shape.set(row, line.toString());
        }
        def.setRecipe(recipe.isEmpty() ? null : recipe);
        module.put(def);
        module.recipeManager().unregisterAll();
        module.recipeManager().registerAll();
        p.sendMessage(Text.mm("<green>Recette sauvegardée pour <white>" + itemId));
        StudioItems.rebuild(plugin, p);
    }

    private void clearRecipe(Player p) {
        CustomItemManagerModule module = items();
        CustomItemDef def = module == null ? null : module.rawDef(itemId);
        if (def == null) return;
        def.setRecipe(null);
        module.put(def);
        module.recipeManager().unregisterAll();
        module.recipeManager().registerAll();
        for (int slot : GRID) inv.setItem(slot, null);
        p.sendMessage(Text.mm("<green>Recette vidée pour <white>" + itemId));
    }

    void returnIngredients(Player p) {
        // Grille virtuelle : aucun item réel n'est stocké dans le menu.
    }

    private static boolean isGrid(int slot) {
        return gridIndex(slot) >= 0;
    }

    private static int gridIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) return i;
        return -1;
    }

    private CustomItemManagerModule items() {
        return plugin.moduleManager().get(CustomItemManagerModule.class);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
