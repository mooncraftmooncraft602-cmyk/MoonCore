package com.mooncore.modules.antifarm;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre en mémoire des spawners posés, indexé pour des comptages O(1) par chunk,
 * par propriétaire et par équipe. Aucune analyse du monde n'est nécessaire : les
 * compteurs sont tenus à jour par les events de pose/casse.
 * <p>
 * Logique pure (clés textuelles + UUID) pour être testable sans Bukkit.
 */
public final class SpawnerRegistry {

    /** Un spawner enregistré. {@code team} peut être {@code null}. */
    public record Entry(String world, int x, int y, int z, UUID owner, String team) {
        public String locKey() { return SpawnerRegistry.locKey(world, x, y, z); }
        public String chunkKey() { return SpawnerRegistry.chunkKey(world, x >> 4, z >> 4); }
    }

    private final Map<String, Entry> byLoc = new ConcurrentHashMap<>();
    private final Map<String, Integer> perChunk = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> perOwner = new ConcurrentHashMap<>();
    private final Map<String, Integer> perTeam = new ConcurrentHashMap<>();

    public static String locKey(String world, int x, int y, int z) {
        return world + ';' + x + ';' + y + ';' + z;
    }

    public static String chunkKey(String world, int cx, int cz) {
        return world + ';' + cx + ';' + cz;
    }

    /** Ajoute un spawner. Sans effet (retourne false) s'il est déjà connu à cette position. */
    @SuppressWarnings("null")
    public boolean add(Entry e) {
        if (byLoc.putIfAbsent(e.locKey(), e) != null) return false;
        perChunk.merge(e.chunkKey(), 1, Integer::sum);
        perOwner.merge(e.owner(), 1, Integer::sum);
        if (e.team() != null) perTeam.merge(e.team(), 1, Integer::sum);
        return true;
    }

    /** Retire le spawner à cette position, ou null s'il n'existait pas. */
    public Entry remove(String world, int x, int y, int z) {
        Entry e = byLoc.remove(locKey(world, x, y, z));
        if (e == null) return null;
        decrement(perChunk, e.chunkKey());
        decrementOwner(e.owner());
        if (e.team() != null) decrement(perTeam, e.team());
        return e;
    }

    public boolean contains(String world, int x, int y, int z) {
        return byLoc.containsKey(locKey(world, x, y, z));
    }

    public int chunkCount(String world, int cx, int cz) {
        return perChunk.getOrDefault(chunkKey(world, cx, cz), 0);
    }

    public int ownerCount(UUID owner) {
        return perOwner.getOrDefault(owner, 0);
    }

    public int teamCount(String team) {
        return team == null ? 0 : perTeam.getOrDefault(team, 0);
    }

    public int total() {
        return byLoc.size();
    }

    public void clear() {
        byLoc.clear();
        perChunk.clear();
        perOwner.clear();
        perTeam.clear();
    }

    @SuppressWarnings("null")
    private static void decrement(Map<String, Integer> map, String key) {
        map.merge(key, -1, Integer::sum);
        map.computeIfPresent(key, (k, v) -> v <= 0 ? null : v);
    }

    @SuppressWarnings("null")
    private void decrementOwner(UUID key) {
        perOwner.merge(key, -1, Integer::sum);
        perOwner.computeIfPresent(key, (k, v) -> v <= 0 ? null : v);
    }
}
