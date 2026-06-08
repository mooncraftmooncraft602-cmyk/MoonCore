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

/** Sous-menu des capacités : clic = ajouter/retirer, clic droit = niveau +1 (max 5). */
public final class AbilityEditorMenu implements InventoryHolder {

    private static final int MAX_LEVEL = 5;
    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private final List<Ability> abilities;
    private Inventory inv;

    private AbilityEditorMenu(CustomItemManagerModule module, ChatInput chat, String id) {
        this.module = module;
        this.chat = chat;
        this.id = id;
        this.abilities = new ArrayList<>(module.abilities().all());
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        AbilityEditorMenu m = new AbilityEditorMenu(module, chat, id);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Capacités</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        for (int i = 0; i < abilities.size() && i < 45; i++) {
            Ability ab = abilities.get(i);
            int level = levelOf(d, ab.id());
            boolean on = level > 0;
            String head = (on ? "<green>● " : "<gray>○ ") + ab.displayName()
                    + (on ? " <white>niv " + level : "") + " <dark_gray>(" + (ab.isActive() ? "actif" : "passif") + ")";
            inv.setItem(i, ItemEditorMenu.btn(on ? Material.ENCHANTED_BOOK : Material.BOOK, head,
                    "<gray>" + ab.description(),
                    "<dark_gray>clic = " + (on ? "retirer" : "ajouter") + " · clic droit = niveau +1"));
        }
        inv.setItem(49, ItemEditorMenu.btn(Material.ARROW, "<yellow>← Retour"));
        inv.setItem(53, ItemEditorMenu.btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int rawSlot, boolean right) {
        if (rawSlot == 49) { ItemEditorMenu.open(module, chat, p, id); return; }
        if (rawSlot == 53) { p.closeInventory(); return; }
        if (rawSlot < 0 || rawSlot >= abilities.size() || rawSlot >= 45) return;
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        Ability ab = abilities.get(rawSlot);
        int level = levelOf(d, ab.id());
        if (right) {
            int next = level <= 0 ? 1 : Math.min(MAX_LEVEL, level + 1);
            d.addAbility(ab.id(), next);
        } else {
            if (level > 0) d.removeAbility(ab.id());
            else d.addAbility(ab.id(), 1);
        }
        module.put(d);
        build();
    }

    private static int levelOf(CustomItemDef d, String abilityId) {
        return d.abilities().stream().filter(a -> a.id().equals(abilityId))
                .mapToInt(CustomItemDef.AbilityRef::level).findFirst().orElse(0);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
