package com.mooncore.modules.audio;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Réapplique l'audio aux moments clés (connexion, respawn, téléport) pour une réactivité
 * immédiate ; le rafraîchissement des zones et la relance des boucles sont assurés par la
 * tâche légère du module. Event-driven : aucun travail par tick de déplacement.
 */
public final class AudioListener implements Listener {

    private final AudioManagerModule module;

    public AudioListener(AudioManagerModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        module.applyPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        module.audioState().remove(e.getPlayer().getUniqueId());
        module.zones().purge(e.getPlayer().getUniqueId()); // évite l'accumulation des sélections/éditions admin
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        // Persiste après la mort.
        module.applyPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // Position mise à jour au tick suivant.
        module.applyPlayerSoon(e.getPlayer());
    }
}
