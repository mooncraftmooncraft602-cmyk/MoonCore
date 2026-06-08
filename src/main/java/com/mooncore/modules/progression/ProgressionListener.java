package com.mooncore.modules.progression;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Charge/sauvegarde la progression à la connexion/déconnexion et accorde de l'XP de
 * progression sur les kills (sources configurables). D'autres modules (boss, missions,
 * quêtes) accordent de l'XP via {@code ProgressionService.addXp}.
 */
public final class ProgressionListener implements Listener {

    private final ProgressionModule module;

    public ProgressionListener(ProgressionModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        module.load(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        module.unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (e.getEntity() instanceof Player) {
            if (module.xpPerPlayerKill() > 0) {
                module.addXp(killer.getUniqueId(), module.xpPerPlayerKill(), "player-kill");
            }
        } else if (module.xpPerMobKill() > 0) {
            module.addXp(killer.getUniqueId(), module.xpPerMobKill(), "mob-kill");
        }
    }
}
