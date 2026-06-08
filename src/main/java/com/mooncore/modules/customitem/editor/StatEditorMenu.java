package com.mooncore.modules.customitem.editor;

import com.mooncore.api.customitem.ItemStats;
import com.mooncore.api.customitem.StatBudget;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sous-menu d'édition des stats avec budget (caps par stat). Clic = ±, borné. */
public final class StatEditorMenu implements InventoryHolder {

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private final List<String> stats = new ArrayList<>(ItemStats.known().keySet());
    private final Map<String, Double> caps = StatBudget.defaults();
    private Inventory inv;

    private StatEditorMenu(CustomItemManagerModule module, ChatInput chat, String id) {
        this.module = module;
        this.chat = chat;
        this.id = id;
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        StatEditorMenu m = new StatEditorMenu(module, chat, id);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Stats</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        for (int i = 0; i < stats.size() && i < 45; i++) {
            String stat = stats.get(i);
            double v = d.stats().getOrDefault(stat, 0.0);
            double cap = StatBudget.cap(caps, stat);
            boolean nearCap = Math.abs(v) >= cap;
            String color = v == 0 ? "<gray>" : (nearCap ? "<red>" : "<green>");
            inv.setItem(i, ItemEditorMenu.btn(Material.PAPER, "<yellow>" + stat,
                    color + fmt(v) + " <dark_gray>/ " + fmt(cap),
                    "<dark_gray>clic G +1 · clic D -1 · shift ±5"));
        }
        inv.setItem(49, ItemEditorMenu.btn(Material.ARROW, "<yellow>← Retour"));
        inv.setItem(53, ItemEditorMenu.btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int rawSlot, boolean right, boolean shift) {
        if (rawSlot == 49) { ItemEditorMenu.open(module, chat, p, id); return; }
        if (rawSlot == 53) { p.closeInventory(); return; }
        if (rawSlot < 0 || rawSlot >= stats.size() || rawSlot >= 45) return;
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        String stat = stats.get(rawSlot);
        double step = (shift ? 5 : 1) * (right ? -1 : 1);
        double v = StatBudget.clamp(caps, stat, d.stats().getOrDefault(stat, 0.0) + step);
        if (Math.abs(v) < 0.0001) d.removeStat(stat); else d.setStat(stat, v);
        module.put(d);
        build(); // rafraîchit
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
