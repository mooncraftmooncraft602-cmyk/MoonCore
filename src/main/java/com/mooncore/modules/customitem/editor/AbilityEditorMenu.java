package com.mooncore.modules.customitem.editor;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.ability.Ability;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;

/** Sous-menu des capacités (paginé) : clic = ajouter/retirer, clic droit = niveau +1 (max 5). */
public final class AbilityEditorMenu implements InventoryHolder {

    private static final int MAX_LEVEL = 5;
    private static final int PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private final int page;
    private final List<Ability> abilities;
    private Inventory inv;

    private AbilityEditorMenu(CustomItemManagerModule module, ChatInput chat, String id, int page) {
        this.module = module;
        this.chat = chat;
        this.id = id;
        this.page = Math.max(0, page);
        this.abilities = new ArrayList<>(module.abilities().all());
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        open(module, chat, p, id, 0);
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id, int page) {
        AbilityEditorMenu m = new AbilityEditorMenu(module, chat, id, page);
        int pages = Math.max(1, (int) Math.ceil(m.abilities.size() / (double) PER_PAGE));
        int pg = Math.min(m.page, pages - 1);
        m.inv = Bukkit.createInventory(m, 54,
                Text.mm("<gradient:#8a2be2:#c77dff>Capacités</gradient> <dark_gray>» <white>" + id
                        + " <dark_gray>(" + (pg + 1) + "/" + pages + ")"));
        m.build(pg);
        p.openInventory(m.inv);
    }

    private void build(int pg) {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        int start = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < abilities.size(); i++) {
            Ability ab = abilities.get(start + i);
            int level = levelOf(d, ab.id());
            boolean on = level > 0;
            String head = (on ? "<green>● " : "<gray>○ ") + ab.displayName()
                    + (on ? " <white>niv " + level : "")
                    + " <dark_gray>(" + (ab.isActive() ? "actif" : "passif") + (ab.special() ? "·spécial" : "") + ")";
            inv.setItem(i, ItemEditorMenu.btn(on ? Material.ENCHANTED_BOOK : Material.BOOK, head,
                    "<gray>" + ab.description(),
                    "<dark_gray>clic = " + (on ? "retirer" : "ajouter") + " · clic droit = niveau +1"));
        }
        if (pg > 0) inv.setItem(PREV_SLOT, ItemEditorMenu.btn(Material.ARROW, "<yellow>← Page précédente"));
        inv.setItem(BACK_SLOT, ItemEditorMenu.btn(Material.OAK_DOOR, "<yellow>← Retour à l'objet"));
        if (start + PER_PAGE < abilities.size()) inv.setItem(NEXT_SLOT, ItemEditorMenu.btn(Material.ARROW, "<yellow>Page suivante →"));
    }

    public void click(Player p, int rawSlot, boolean right) {
        if (rawSlot == PREV_SLOT && page > 0) { open(module, chat, p, id, page - 1); return; }
        if (rawSlot == NEXT_SLOT) { open(module, chat, p, id, page + 1); return; }
        if (rawSlot == BACK_SLOT) { ItemEditorMenu.open(module, chat, p, id); return; }
        if (rawSlot < 0 || rawSlot >= PER_PAGE) return;
        int index = page * PER_PAGE + rawSlot;
        if (index >= abilities.size()) return;
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        Ability ab = abilities.get(index);
        int level = levelOf(d, ab.id());
        if (right) {
            int next = level <= 0 ? 1 : Math.min(MAX_LEVEL, level + 1);
            d.addAbility(ab.id(), next);
        } else {
            if (level > 0) d.removeAbility(ab.id());
            else d.addAbility(ab.id(), 1);
        }
        module.put(d);
        open(module, chat, p, id, page); // rafraîchit la page courante
    }

    private static int levelOf(CustomItemDef d, String abilityId) {
        return d.abilities().stream().filter(a -> a.id().equals(abilityId))
                .mapToInt(CustomItemDef.AbilityRef::level).findFirst().orElse(0);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
