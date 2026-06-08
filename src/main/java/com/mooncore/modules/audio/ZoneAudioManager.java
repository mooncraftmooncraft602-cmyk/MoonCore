package com.mooncore.modules.audio;

import com.mooncore.api.zone.Region;
import com.mooncore.modules.zone.RegionSelection;
import com.mooncore.modules.zone.ZoneIndex;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zones audio (cuboïdes indépendantes) associées à une piste. Réutilise l'index spatial par
 * chunk ({@link ZoneIndex}) et la sélection ({@link RegionSelection}) du ZoneManager, sans
 * modifier ce dernier. Persistées dans {@code audio-data.yml}.
 */
public final class ZoneAudioManager {

    private final AudioData data;
    private final ZoneIndex index = new ZoneIndex();
    private final Map<String, String> zoneTrack = new ConcurrentHashMap<>();
    private final Map<UUID, RegionSelection> selections = new ConcurrentHashMap<>();
    private final Map<UUID, String> editing = new ConcurrentHashMap<>();

    public ZoneAudioManager(AudioData data) {
        this.data = data;
    }

    public void load() {
        index.clear();
        zoneTrack.clear();
        ConfigurationSection sec = data.yml().getConfigurationSection("audio-zones");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection z = sec.getConfigurationSection(name);
            if (z == null) continue;
            List<Integer> min = z.getIntegerList("min");
            List<Integer> max = z.getIntegerList("max");
            if (min.size() < 3 || max.size() < 3) continue;
            Region r = new Region(name, z.getString("world", "world"),
                    min.get(0), min.get(1), min.get(2), max.get(0), max.get(1), max.get(2), 0);
            index.add(r);
            String track = z.getString("track", null);
            if (track != null) zoneTrack.put(name.toLowerCase(Locale.ROOT), track);
        }
    }

    public RegionSelection selection(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new RegionSelection());
    }

    public void setEditing(UUID uuid, String name) {
        editing.put(uuid, name.toLowerCase(Locale.ROOT));
    }

    /** Purge l'état transitoire d'un joueur (sélection en cours + zone en édition). À appeler au quit. */
    public void purge(UUID uuid) {
        selections.remove(uuid);
        editing.remove(uuid);
    }

    public String editing(UUID uuid) {
        return editing.get(uuid);
    }

    /** Crée une zone audio à partir de la sélection du joueur. null si nom pris/sélection incomplète. */
    public Region create(String name, RegionSelection sel) {
        String id = name.toLowerCase(Locale.ROOT);
        if (index.byName(id) != null || !sel.isComplete()) return null;
        Location a = sel.pos1();
        Location b = sel.pos2();
        Region r = new Region(id, a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), 0);
        index.add(r);
        persist();
        return r;
    }

    public boolean setTrack(String name, String trackId) {
        Region r = index.byName(name);
        if (r == null) return false;
        zoneTrack.put(name.toLowerCase(Locale.ROOT), trackId);
        persist();
        return true;
    }

    public boolean delete(String name) {
        Region r = index.byName(name);
        if (r == null) return false;
        index.remove(r);
        zoneTrack.remove(name.toLowerCase(Locale.ROOT));
        persist();
        return true;
    }

    /** Piste audio à un emplacement (zone de plus haute priorité), ou null. */
    public String trackAt(Location loc) {
        for (Region r : index.regionsAt(loc)) {
            String track = zoneTrack.get(r.name().toLowerCase(Locale.ROOT));
            if (track != null) return track;
        }
        return null;
    }

    public java.util.Collection<Region> zones() {
        return index.all();
    }

    public String trackOf(String name) {
        return zoneTrack.get(name.toLowerCase(Locale.ROOT));
    }

    private void persist() {
        var yml = data.yml();
        yml.set("audio-zones", null);
        for (Region r : index.all()) {
            String path = "audio-zones." + r.name();
            yml.set(path + ".world", r.world());
            yml.set(path + ".min", List.of(r.minX(), r.minY(), r.minZ()));
            yml.set(path + ".max", List.of(r.maxX(), r.maxY(), r.maxZ()));
            String track = zoneTrack.get(r.name().toLowerCase(Locale.ROOT));
            if (track != null) yml.set(path + ".track", track);
        }
        data.save();
    }
}
