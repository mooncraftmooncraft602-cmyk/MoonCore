package com.mooncore.modules.companion;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont vers le <b>mod client OPTIONNEL</b> "MoonCore Companion" (Fabric, Java only).
 * <p>
 * Communication par message de plugin sur le canal {@value #CHANNEL}. À la connexion, le mod
 * envoie un HELLO ; le serveur répond WELCOME (capacités) et retient le joueur comme « moddé ».
 * Les joueurs SANS le mod (Java vanilla ou Bedrock) ne sont jamais affectés — ils n'ont
 * simplement pas accès aux fonctionnalités avancées côté client (éditeur 2D/3D, modèles, etc.).
 * Les autres modules peuvent appeler {@link #hasCompanion(UUID)} pour réserver une fonctionnalité
 * Java-only aux joueurs équipés du mod.
 */
@ModuleInfo(id = "companion", name = "CompanionBridge")
public final class CompanionModule extends AbstractModule implements Listener, PluginMessageListener {

    /** Canal de message de plugin partagé avec le mod Fabric (Identifier "mooncore:companion"). */
    public static final String CHANNEL = "mooncore:companion";

    // Protocole : 1er octet = opcode, 2e = version de protocole.
    private static final byte OP_HELLO = 0x01;   // client → serveur (présence du mod)
    private static final byte OP_WELCOME = 0x02; // serveur → client (capacités activées)
    private static final byte PROTOCOL = 1;

    // Capacités annoncées au client (bitmask, extensible).
    private static final byte CAP_STUDIO_2D = 0x01; // éditeur de texture/animation 2D natif
    private static final byte CAP_MODELS_3D = 0x02; // rendu de modèles 3D custom
    private static final byte CAP_ENTITY    = 0x04; // modèles/animations d'entités custom
    private static final byte CAPABILITIES  = CAP_STUDIO_2D | CAP_MODELS_3D | CAP_ENTITY;

    private final Set<UUID> companions = ConcurrentHashMap.newKeySet();
    private boolean enabled;

    @Override
    protected void onEnable() {
        this.enabled = moduleConfig().getBoolean("enabled", true);
        if (!enabled) { log().info("[Companion] Désactivé par config."); return; }
        var messenger = plugin().getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin(), CHANNEL);
        messenger.registerIncomingPluginChannel(plugin(), CHANNEL, this);
        registerListener(this);
        log().info("[Companion] Pont mod client prêt (canal " + CHANNEL + "). Mod facultatif (Java only).");
    }

    @Override
    protected void onDisable() {
        if (!enabled) return;
        var messenger = plugin().getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin(), CHANNEL, this);
        messenger.unregisterOutgoingPluginChannel(plugin(), CHANNEL);
        companions.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
    }

    /** True si ce joueur a le mod client MoonCore Companion installé et reconnu. */
    public boolean hasCompanion(UUID uuid) {
        return uuid != null && companions.contains(uuid);
    }

    public boolean hasCompanion(Player p) {
        return p != null && companions.contains(p.getUniqueId());
    }

    public int companionCount() { return companions.size(); }

    // ---- Réception des messages du mod ----

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length < 1) return;
        if (message[0] == OP_HELLO) {
            int proto = message.length > 1 ? (message[1] & 0xFF) : 1;
            boolean isNew = companions.add(player.getUniqueId());
            sendWelcome(player);
            if (isNew) {
                log().info("[Companion] " + player.getName() + " utilise le mod client (protocole " + proto + ").");
                player.sendMessage(Text.mm("<gradient:#8a2be2:#c77dff>MoonCore Companion</gradient> <green>détecté "
                        + "— fonctionnalités Java avancées activées."));
            }
        }
    }

    private void sendWelcome(Player player) {
        byte[] data = {OP_WELCOME, PROTOCOL, CAPABILITIES};
        try {
            player.sendPluginMessage(plugin(), CHANNEL, data);
        } catch (Throwable t) {
            log().warn("[Companion] Envoi WELCOME échoué à " + player.getName() + " : " + t.getMessage());
        }
    }

    /** Envoie une charge utile brute au mod d'un joueur (no-op s'il n'a pas le mod). */
    public void send(Player player, byte[] data) {
        if (player == null || data == null || !hasCompanion(player)) return;
        try { player.sendPluginMessage(plugin(), CHANNEL, data); } catch (Throwable ignored) { }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        companions.remove(e.getPlayer().getUniqueId());
    }
}
