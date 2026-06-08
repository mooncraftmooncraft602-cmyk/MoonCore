package com.mooncore.modules.audio;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère les boucles forcées : globale (priorité max) et par joueur. Persistées dans
 * {@code audio-data.yml} → survivent à la mort, au teleport et à la reconnexion ; la boucle
 * globale s'applique automatiquement aux nouveaux joueurs.
 */
public final class LoopManager {

    private final AudioData data;

    private volatile boolean globalActive;
    private volatile String globalTrack;
    private final Map<UUID, String> playerLoops = new ConcurrentHashMap<>();

    public LoopManager(AudioData data) {
        this.data = data;
    }

    public void load() {
        playerLoops.clear();
        ConfigurationSection root = data.yml().getConfigurationSection("loop");
        if (root == null) return;
        this.globalActive = root.getBoolean("global.active", false);
        this.globalTrack = root.getString("global.track", null);
        ConfigurationSection players = root.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                if (players.getBoolean(key + ".active", false)) {
                    String track = players.getString(key + ".track", null);
                    if (track != null) {
                        try { playerLoops.put(UUID.fromString(key), track); } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }
    }

    // ---- Global ----

    public String global() {
        return globalActive ? globalTrack : null;
    }

    public void setGlobal(String track) {
        this.globalActive = true;
        this.globalTrack = track;
        persist();
    }

    public void clearGlobal() {
        this.globalActive = false;
        this.globalTrack = null;
        persist();
    }

    // ---- Par joueur ----

    public String player(UUID uuid) {
        return playerLoops.get(uuid);
    }

    public void setPlayer(UUID uuid, String track) {
        playerLoops.put(uuid, track);
        persist();
    }

    public void clearPlayer(UUID uuid) {
        playerLoops.remove(uuid);
        persist();
    }

    private void persist() {
        var yml = data.yml();
        yml.set("loop.global.active", globalActive);
        yml.set("loop.global.track", globalTrack);
        yml.set("loop.players", null); // réécrit proprement
        for (Map.Entry<UUID, String> e : playerLoops.entrySet()) {
            yml.set("loop.players." + e.getKey() + ".active", true);
            yml.set("loop.players." + e.getKey() + ".track", e.getValue());
        }
        data.save();
    }
}
