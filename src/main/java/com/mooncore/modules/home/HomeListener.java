package com.mooncore.modules.home;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Charge/décharge les homes du joueur à la connexion/déconnexion. */
public final class HomeListener implements Listener {

    private final HomeManagerModule module;

    public HomeListener(HomeManagerModule module) {
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
}
