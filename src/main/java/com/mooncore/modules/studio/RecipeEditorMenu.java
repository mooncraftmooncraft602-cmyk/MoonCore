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

/** Editeur de recette shaped 3x3 avec ingredients copies depuis l'inventaire. */
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
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Recette</gradient> <dark_gray>> <white>" + itemId));
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
            loadRecipe(module, def);
        }
        if (initialGrid != null) {
            for (int i = 0; i < GRID.length && i < initialGrid.length; i++) {
                inv.setItem(GRID[i], initialGrid[i] == null ? null : initialGrid[i].clone());
            }
        }
        inv.setItem(14, StudioItems.btn(Material.ARROW, "<gray>Resultat"));
        inv.setItem(SAVE, StudioItems.btn(Material.EMERALD_BLOCK, "<green>Sauvegarder",
                "<gray>clic inventaire puis clic grille = copier",
                "<gray>shift-clic inventaire = ajoute direct",
                "<gray>clic droit grille = vider"));
        inv.setItem(BACK, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour recettes"));
        inv.setItem(CLEAR, StudioItems.btn(Material.TNT, "<red>Vider recette"));
        inv.setItem(AI, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Generer IA"));
    }

    private void loadRecipe(CustomItemManagerModule module, CustomItemDef def) {
        if (def.recipe() == null || def.recipe().isEmpty()) return;
        Map<Character, CustomItemDef.RecipeIngredient> ing = def.recipe().ingredients;
        for (int row = 0; row < Math.min(3, def.recipe().shape.size()); row++) {
            String line = def.recipe().shape.get(row);
            for (int col = 0; col < Math.min(3, line.length()); col++) {
                char c = line.charAt(col);
                ItemStack preview = preview(module, ing.get(c));
                if (preview != null) inv.setItem(GRID[row * 3 + col], preview);
            }
        }
    }

    void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        boolean top = e.getClickedInventory() != null && e.getClickedInventory().equals(e.getView().getTopInventory());
        int slot = e.getRawSlot();

        if (!top) {
            // Anti-duplication : un double-clic / collect-to-cursor aspire les "ghosts" (items réels)
            // de la grille du haut depuis l'inventaire du bas → on bloque ces actions.
            if (e.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
                    || e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) {
                e.setCancelled(true);
                return;
            }
            if (e.isShiftClick() && usable(e.getCurrentItem())) {
                e.setCancelled(true);
                placeFirstEmpty(p, e.getCurrentItem());
            }
            return;
        }

        e.setCancelled(true);
        if (isGrid(slot)) {
            editGridSlot(p, slot, e);
            return;
        }
        switch (slot) {
            case SAVE -> save(p);
            case CLEAR -> clearRecipe(p);
            case AI -> {
                p.closeInventory();
                p.performCommand("moon ai createrecipe " + itemId + " recette simple et equilibree avec ingredients vanilla");
            }
            case BACK -> StudioRecipeMenu.open(plugin, chat, p, 0);
            default -> { }
        }
    }

    void drag(InventoryDragEvent e) {
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = e.getRawSlots().stream().anyMatch(raw -> raw < topSize);
        if (!touchesTop) return;

        e.setCancelled(true);
        if (!usable(e.getOldCursor())) return;
        for (int raw : e.getRawSlots()) {
            if (isGrid(raw)) setGhost(raw, e.getOldCursor());
        }
    }

    private void editGridSlot(Player p, int slot, InventoryClickEvent e) {
        if (e.isRightClick()) {
            inv.setItem(slot, null);
            return;
        }
        if (usable(e.getCursor())) {
            setGhost(slot, e.getCursor());
            return;
        }
        if (e.isShiftClick()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (usable(hand)) setGhost(slot, hand);
            else p.sendMessage(Text.mm("<red>Tiens un item en main pour remplir cette case."));
            return;
        }
        askMaterial(p, slot);
    }

    private void askMaterial(Player p, int slot) {
        p.closeInventory();
        ItemStack[] current = snapshotGrid();
        chat.request(p, "<yellow>Materiau pour cette case (ex <white>DIAMOND</white>) :", in -> {
            Material mat = Material.matchMaterial(in.toUpperCase(java.util.Locale.ROOT));
            if (mat == null || !mat.isItem()) {
                p.sendMessage(Text.mm("<red>Materiau invalide : " + in));
                open(plugin, chat, p, itemId, current);
                return;
            }
            int idx = gridIndex(slot);
            if (idx >= 0) current[idx] = new ItemStack(mat);
            open(plugin, chat, p, itemId, current);
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

    private void placeFirstEmpty(Player p, ItemStack source) {
        for (int slot : GRID) {
            ItemStack current = inv.getItem(slot);
            if (current == null || current.getType().isAir()) {
                setGhost(slot, source);
                return;
            }
        }
        p.sendMessage(Text.mm("<yellow>La grille est pleine. Clic droit sur une case pour la vider."));
    }

    private void setGhost(int slot, ItemStack source) {
        if (!usable(source)) return;
        ItemStack ghost = source.clone();
        ghost.setAmount(1);
        inv.setItem(slot, ghost);
    }

    private ItemStack preview(CustomItemManagerModule module, CustomItemDef.RecipeIngredient ingredient) {
        if (module == null || ingredient == null) return null;
        if (ingredient.isCustom()) {
            CustomItemDef custom = module.rawDef(ingredient.customItemId());
            if (custom != null) return module.buildItem(custom, 1);
            return StudioItems.btn(Material.BARRIER, "<red>Item custom manquant", "<gray>" + ingredient.customItemId());
        }
        Material mat = ingredient.material();
        return mat == null || !mat.isItem() ? null : new ItemStack(mat);
    }

    private CustomItemDef.RecipeIngredient ingredientFrom(CustomItemManagerModule module, ItemStack item) {
        if (!usable(item)) return null;
        String customId = module.factory().idOf(item);
        if (customId != null && module.rawDef(customId) != null) {
            return CustomItemDef.RecipeIngredient.custom(customId);
        }
        return CustomItemDef.RecipeIngredient.material(item.getType());
    }

    private void save(Player p) {
        CustomItemManagerModule module = items();
        if (module == null) return;
        CustomItemDef def = module.rawDef(itemId);
        if (def == null) return;
        CustomItemDef.Recipe recipe = new CustomItemDef.Recipe();
        Map<String, Character> chars = new LinkedHashMap<>();
        char next = 'A';
        for (int row = 0; row < 3; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                ItemStack it = inv.getItem(GRID[row * 3 + col]);
                if (!usable(it)) {
                    line.append(' ');
                    continue;
                }
                CustomItemDef.RecipeIngredient ingredient = ingredientFrom(module, it);
                if (ingredient == null) {
                    line.append(' ');
                    continue;
                }
                String key = ingredient.storageKey();
                Character c = chars.get(key);
                if (c == null) {
                    c = next++;
                    chars.put(key, c);
                    recipe.ingredients.put(c, ingredient);
                }
                line.append(c);
            }
            recipe.shape.set(row, line.toString());
        }
        def.setRecipe(recipe.isEmpty() ? null : recipe);
        module.put(def);
        module.recipeManager().unregisterAll();
        module.recipeManager().registerAll();
        p.sendMessage(Text.mm("<green>Recette sauvegardee pour <white>" + itemId));
        StudioItems.rebuild(plugin, p);
    }

    private void clearRecipe(Player p) {
        CustomItemManagerModule module = items();
        if (module == null) return;
        CustomItemDef def = module.rawDef(itemId);
        if (def == null) return;
        def.setRecipe(null);
        module.put(def);
        module.recipeManager().unregisterAll();
        module.recipeManager().registerAll();
        for (int slot : GRID) inv.setItem(slot, null);
        p.sendMessage(Text.mm("<green>Recette videe pour <white>" + itemId));
    }

    void returnIngredients(Player p) {
        // Grille virtuelle : aucun item reel n'est stocke dans le menu.
    }

    private static boolean isGrid(int slot) {
        return gridIndex(slot) >= 0;
    }

    private static int gridIndex(int slot) {
        for (int i = 0; i < GRID.length; i++) if (GRID[i] == slot) return i;
        return -1;
    }

    private static boolean usable(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType().isItem();
    }

    private CustomItemManagerModule items() {
        return plugin.moduleManager().get(CustomItemManagerModule.class);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
