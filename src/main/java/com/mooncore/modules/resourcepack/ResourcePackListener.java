package com.mooncore.modules.resourcepack;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Envoie le pack forcé à la connexion (joueurs Java) et applique l'éventuel kick
 * si le pack est refusé/échoue alors qu'il est obligatoire.
 */
public final class ResourcePackListener implements Listener {

    private final ResourcePackModule module;

    public ResourcePackListener(ResourcePackModule module) {
        this.module = module;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Léger délai non nécessaire : l'envoi peut se faire dès le join.
        module.send(e.getPlayer());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!module.force() || !module.kickOnDecline()) return;
        var status = e.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            e.getPlayer().kick(module.promptComponent());
        }
    }
}
