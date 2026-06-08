package com.mooncore.modules.zone;

import com.mooncore.MoonCore;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Baguette de sélection (houe en or) : clic gauche = coin 1, clic droit = coin 2.
 * Réservée à {@code mooncore.admin.zones}.
 */
public final class SelectionListener implements Listener {

    private static final Material WAND = Material.GOLDEN_HOE;

    private final MoonCore plugin;
    private final ZoneModule zone;

    public SelectionListener(MoonCore plugin, ZoneModule zone) {
        this.plugin = plugin;
        this.zone = zone;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("mooncore.admin.zones")) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != WAND) return;
        if (event.getClickedBlock() == null) return;

        Action action = event.getAction();
        Location loc = event.getClickedBlock().getLocation();
        RegionSelection sel = zone.selection(player);
        var cm = plugin.configManager();

        if (action == Action.LEFT_CLICK_BLOCK) {
            sel.setPos1(loc);
            event.setCancelled(true);
            player.sendMessage(cm.prefixed("zone-pos1",
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ())));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            sel.setPos2(loc);
            event.setCancelled(true);
            player.sendMessage(cm.prefixed("zone-pos2",
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ())));
        }
    }
}
