package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Liste des items avec accès direct à leur recette. */
public final class StudioRecipeMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private StudioRecipeMenu(MoonCore plugin, ChatInput chat, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.page = Math.max(0, page);
        CustomItemManagerModule module = plugin.moduleManager().get(CustomItemManagerModule.class);
        this.ids = module == null ? List.of() : new ArrayList<>(module.rawDefs().keySet());
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, int page) {
        StudioRecipeMenu menu = new StudioRecipeMenu(plugin, chat, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>» Recettes"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        inv.setItem(0, StudioItems.btn(Material.CRAFTING_TABLE, "<green>Éditeur 3x3",
                "<gray>clique un item pour modifier sa recette"));
        inv.setItem(1, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Recette IA",
                "<gray>shift sur un item: demander une recette IA"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));

        CustomItemManagerModule module = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (module == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Module custom-item inactif"));
            return;
        }
        int start = page * StudioItems.CONTENT_SLOTS.length;
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length && start + i < ids.size(); i++) {
            CustomItemDef def = module.rawDef(ids.get(start + i));
            if (def == null) continue;
            boolean has = def.recipe() != null && !def.recipe().isEmpty();
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.btn(has ? Material.CRAFTING_TABLE : Material.PAPER,
                    (has ? "<green>" : "<yellow>") + def.id(),
                    "<gray>clic: éditer la grille",
                    "<gray>clic droit: donner le résultat",
                    "<gray>shift: générer avec IA",
                    has ? "<dark_gray>recette enregistrée" : "<dark_gray>aucune recette"));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page précédente"));
        if (start + StudioItems.CONTENT_SLOTS.length < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
        inv.setItem(49, StudioItems.btn(Material.BOOK, "<gray>" + ids.size() + " item(s)"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 8) { StudioHubMenu.open(plugin, chat, p); return; }
        if (slot == 45 && page > 0) { open(plugin, chat, p, page - 1); return; }
        if (slot == 53) { open(plugin, chat, p, page + 1); return; }
        CustomItemManagerModule module = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (module == null) return;
        int idx = indexFor(slot);
        if (idx < 0) return;
        int itemIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (itemIndex >= ids.size()) return;
        String id = ids.get(itemIndex);
        if (shiftClick) {
            p.closeInventory();
            p.performCommand("moon ai createrecipe " + id + " recette simple et equilibree avec ingredients vanilla");
        } else if (rightClick) {
            module.give(p, id, 1);
            p.sendMessage(Text.mm("<green>Résultat reçu : <white>" + id));
        } else {
            RecipeEditorMenu.open(plugin, chat, p, id);
        }
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    @Override
    public Inventory getInventory() { return inv; }
}
