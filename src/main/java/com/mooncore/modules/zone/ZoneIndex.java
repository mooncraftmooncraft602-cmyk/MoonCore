package com.mooncore.modules.zone;

import com.mooncore.api.zone.Region;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index spatial des régions par chunk, pour une recherche O(régions dans le chunk)
 * sans aucun scan global. Une région est référencée dans tous les chunks qu'elle couvre.
 */
public final class ZoneIndex {

    // world -> (chunkKey -> régions)
    private final Map<String, Map<Long, List<Region>>> byWorld = new ConcurrentHashMap<>();
    // accès par nom (unicité)
    private final Map<String, Region> byName = new ConcurrentHashMap<>();

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public void add(Region region) {
        byName.put(region.name().toLowerCase(java.util.Locale.ROOT), region);
        Map<Long, List<Region>> world = byWorld.computeIfAbsent(region.world(), k -> new ConcurrentHashMap<>());
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                world.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(region);
            }
        }
    }

    public void remove(Region region) {
        byName.remove(region.name().toLowerCase(java.util.Locale.ROOT));
        Map<Long, List<Region>> world = byWorld.get(region.world());
        if (world == null) return;
        for (int cx = region.minChunkX(); cx <= region.maxChunkX(); cx++) {
            for (int cz = region.minChunkZ(); cz <= region.maxChunkZ(); cz++) {
                List<Region> list = world.get(chunkKey(cx, cz));
                if (list != null) {
                    list.remove(region);
                    if (list.isEmpty()) world.remove(chunkKey(cx, cz));
                }
            }
        }
    }

    /** Régions contenant la position, triées par priorité décroissante. */
    public List<Region> regionsAt(Location loc) {
        if (loc.getWorld() == null) return List.of();
        Map<Long, List<Region>> world = byWorld.get(loc.getWorld().getName());
        if (world == null) return List.of();
        List<Region> candidates = world.get(chunkKey(loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<Region> result = new ArrayList<>(2);
        for (Region r : candidates) {
            if (r.contains(loc)) result.add(r);
        }
        result.sort(Comparator.comparingInt(Region::priority).reversed());
        return result;
    }

    public Region byName(String name) {
        return byName.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    public java.util.Collection<Region> all() {
        return byName.values();
    }

    public void clear() {
        byWorld.clear();
        byName.clear();
    }
}
