package com.mooncore.modules.audio;

import com.mooncore.MoonCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Charge et fournit les pistes définies dans {@code tracks.yml}. */
public final class TrackManager {

    private final MoonCore plugin;
    private final Map<String, Track> tracks = new LinkedHashMap<>();

    public TrackManager(MoonCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        tracks.clear();
        File file = new File(plugin.getDataFolder(), "tracks.yml");
        if (!file.exists() && plugin.getResource("tracks.yml") != null) {
            plugin.saveResource("tracks.yml", false);
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("tracks");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null) continue;
            tracks.put(id, new Track(
                    id,
                    t.getString("sound", "minecraft:music.overworld"),
                    t.getString("bedrock-sound", ""),
                    (float) t.getDouble("volume", 1.0),
                    (float) t.getDouble("pitch", 1.0),
                    t.getBoolean("loop", false),
                    t.getInt("fade-in", 20),
                    t.getInt("fade-out", 20),
                    t.getInt("length-seconds", 60)));
        }
        plugin.logger().info("AudioManager : " + tracks.size() + " piste(s) chargée(s).");
    }

    public Track get(String id) {
        return id == null ? null : tracks.get(id);
    }

    public boolean exists(String id) {
        return tracks.containsKey(id);
    }

    public Collection<Track> all() {
        return tracks.values();
    }
}
