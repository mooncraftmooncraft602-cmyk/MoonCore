package com.mooncore.util;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Capture la PROCHAINE ligne de chat d'un joueur et la renvoie à un callback (sur le
 * thread principal). Réutilisable pour saisir un nom, un matériau, une couleur hex…
 * Le joueur peut taper « annuler » pour abandonner. Expire après 60 s.
 */
public final class ChatInput implements Listener {

    private record Pending(Consumer<String> callback, long expiresAt) {}

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInput(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Demande une saisie ; {@code prompt} est envoyé au joueur. */
    public void request(Player p, String prompt, Consumer<String> callback) {
        pending.put(p.getUniqueId(), new Pending(callback, System.currentTimeMillis() + 60_000));
        p.sendMessage(Text.mm(prompt + " <dark_gray>(tape ta réponse dans le chat, ou « annuler »)"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending pd = pending.get(p.getUniqueId());
            if (pd != null && System.currentTimeMillis() >= pd.expiresAt()) {
                pending.remove(p.getUniqueId());
                if (p.isOnline()) p.sendMessage(Text.mm("<gray>Saisie expirée."));
            }
        }, 60 * 20L + 20L);
    }

    public boolean isWaiting(UUID uuid) { return pending.containsKey(uuid); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.remove(e.getPlayer().getUniqueId());
        if (pd == null) return;
        e.setCancelled(true); // ne pas diffuser la réponse
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("annuler") || text.equalsIgnoreCase("cancel")) {
                p.sendMessage(Text.mm("<gray>Annulé."));
                return;
            }
            pd.callback().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
    }
}
