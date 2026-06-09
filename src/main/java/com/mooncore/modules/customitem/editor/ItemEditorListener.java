package com.mooncore.modules.customitem.editor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/** Route les clics des menus de l'éditeur d'item (principal, stats, capacités). */
public final class ItemEditorListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Object holder = e.getInventory().getHolder();
        if (!(holder instanceof ItemEditorMenu || holder instanceof StatEditorMenu
                || holder instanceof AbilityEditorMenu || holder instanceof EnchantEditorMenu
                || holder instanceof ConsumableEditorMenu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        int slot = e.getRawSlot();
        if (holder instanceof ItemEditorMenu m) m.click(p, slot, e.isRightClick());
        else if (holder instanceof StatEditorMenu m) m.click(p, slot, e.isRightClick(), e.isShiftClick());
        else if (holder instanceof AbilityEditorMenu m) m.click(p, slot, e.isRightClick());
        else if (holder instanceof EnchantEditorMenu m) m.click(p, slot, e.isRightClick());
        else if (holder instanceof ConsumableEditorMenu m) m.click(p, slot, e.isRightClick(), e.isShiftClick());
    }
}
