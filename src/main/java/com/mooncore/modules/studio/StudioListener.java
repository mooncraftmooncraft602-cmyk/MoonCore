package com.mooncore.modules.studio;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Route tous les clics des menus Moon Studio. */
public final class StudioListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Object holder = e.getInventory().getHolder();
        if (holder instanceof RecipeEditorMenu recipe) {
            recipe.click(e);
            return;
        }
        if (!(holder instanceof StudioMenu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        menu.click(p, e.getRawSlot(), e.isRightClick(), e.isShiftClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Object holder = e.getInventory().getHolder();
        if (holder instanceof RecipeEditorMenu recipe) recipe.drag(e);
        else if (holder instanceof StudioMenu) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof RecipeEditorMenu recipe && e.getPlayer() instanceof Player p) {
            recipe.returnIngredients(p);
        }
    }
}
