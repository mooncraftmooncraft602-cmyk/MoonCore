package com.mooncore.modules.economy;

import com.mooncore.MoonCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Applique les frais (money sinks) : téléportation par commande/plugin et réparation à
 * l'enclume. Si le joueur ne peut pas payer, l'action est annulée.
 */
public final class FeeListener implements Listener {

    private final MoonCore plugin;
    private final EconomyBalancerModule module;

    public FeeListener(MoonCore plugin, EconomyBalancerModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent e) {
        double fee = module.teleportFee();
        if (fee <= 0 || !module.isAvailable()) return;
        Player p = e.getPlayer();
        if (p.hasPermission("mooncore.bypass.economy.tax")) return;
        if (!module.feeCauses().contains(e.getCause().name())) return;

        var cm = plugin.configManager();
        if (!module.has(p.getUniqueId(), fee)) {
            e.setCancelled(true);
            p.sendMessage(cm.prefixed("economy-fee-insufficient",
                    "fee", format(fee), "type", "téléportation"));
            return;
        }
        module.withdraw(p.getUniqueId(), fee, "teleport-fee");
        p.sendMessage(cm.prefixed("economy-fee-paid", "fee", format(fee), "type", "téléportation"));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAnvil(InventoryClickEvent e) {
        double fee = module.repairFee();
        if (fee <= 0 || !module.isAvailable()) return;
        if (e.getInventory().getType() != InventoryType.ANVIL) return;
        if (!(e.getInventory() instanceof AnvilInventory)) return;
        // Slot résultat de l'enclume = index 2.
        if (e.getRawSlot() != 2) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (p.hasPermission("mooncore.bypass.economy.tax")) return;

        ItemStack result = e.getInventory().getItem(2);
        if (result == null || result.getType().isAir()) return;

        var cm = plugin.configManager();
        if (!module.has(p.getUniqueId(), fee)) {
            e.setCancelled(true);
            p.sendMessage(cm.prefixed("economy-fee-insufficient",
                    "fee", format(fee), "type", "réparation"));
            return;
        }
        module.withdraw(p.getUniqueId(), fee, "repair-fee");
        p.sendMessage(cm.prefixed("economy-fee-paid", "fee", format(fee), "type", "réparation"));
    }

    private String format(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
