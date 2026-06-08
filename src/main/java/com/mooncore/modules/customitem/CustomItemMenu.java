package com.mooncore.modules.customitem;

import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu GUI (coffre) listant tous les objets custom — l'équivalent d'un « onglet
 * créatif » : clic = recevoir l'objet. Compatible Bedrock (coffre traduit par Geyser).
 */
public final class CustomItemMenu implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final CustomItemManagerModule module;
    private final int page;
    private final List<String> ids;
    private Inventory inventory;

    private CustomItemMenu(CustomItemManagerModule module, int page) {
        this.module = module;
        this.page = page;
        this.ids = new ArrayList<>(module.rawDefs().keySet());
    }

    public static void open(CustomItemManagerModule module, Player p, int page) {
        new CustomItemMenu(module, Math.max(0, page)).build().show(p);
    }

    private CustomItemMenu build() {
        int pages = Math.max(1, (int) Math.ceil(ids.size() / (double) PER_PAGE));
        int pg = Math.min(page, pages - 1);
        this.inventory = Bukkit.createInventory(this, SIZE,
                Text.mm("<gradient:#8a2be2:#c77dff>Menu créatif — objets custom</gradient> <dark_gray>(" + (pg + 1) + "/" + pages + ")"));

        int start = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < ids.size(); i++) {
            CustomItemDef def = module.rawDef(ids.get(start + i));
            if (def != null) inventory.setItem(i, module.buildItem(def, 1));
        }
        if (pg > 0) inventory.setItem(PREV_SLOT, nav(Material.ARROW, "<yellow>← Page précédente"));
        inventory.setItem(INFO_SLOT, nav(Material.BOOK,
                "<gray>Clique un objet pour le recevoir"));
        if (start + PER_PAGE < ids.size()) inventory.setItem(NEXT_SLOT, nav(Material.ARROW, "<yellow>Page suivante →"));
        return this;
    }

    private void show(Player p) {
        p.openInventory(inventory);
    }

    private static ItemStack nav(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(m);
        }
        return it;
    }

    /** Gère un clic dans ce menu (appelé par CustomItemListener). */
    public void onClick(Player p, int slot) {
        if (slot == PREV_SLOT && page > 0) { open(module, p, page - 1); return; }
        if (slot == NEXT_SLOT) { open(module, p, page + 1); return; }
        if (slot < 0 || slot >= PER_PAGE) return;
        int index = page * PER_PAGE + slot;
        if (index >= ids.size()) return;
        CustomItemDef def = module.rawDef(ids.get(index));
        if (def != null) {
            module.give(p, def.id(), 1);
            p.sendMessage(Text.mm("<green>Reçu : <reset>" + def.displayName()));
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
