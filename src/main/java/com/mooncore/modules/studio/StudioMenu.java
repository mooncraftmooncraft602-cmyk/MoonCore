package com.mooncore.modules.studio;

import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

interface StudioMenu extends InventoryHolder {
    void click(Player p, int slot, boolean rightClick, boolean shiftClick);
}
