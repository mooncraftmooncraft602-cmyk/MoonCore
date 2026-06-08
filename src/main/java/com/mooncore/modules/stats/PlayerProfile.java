package com.mooncore.modules.stats;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Profil joueur en mémoire : identité, temps de jeu et compteurs de stats. Données pures
 * (sans Bukkit), testables. Marqué {@code dirty} dès qu'une valeur change, pour le
 * write-behind.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private volatile String name;
    private final long firstJoin;
    private volatile long lastSeen;
    private volatile long playtimeSeconds;
    private final String seasonId;

    private final Map<String, Long> stats = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public PlayerProfile(UUID uuid, String name, long firstJoin, long lastSeen,
                         long playtimeSeconds, String seasonId) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = firstJoin;
        this.lastSeen = lastSeen;
        this.playtimeSeconds = playtimeSeconds;
        this.seasonId = seasonId;
    }

    public long get(String key) {
        return stats.getOrDefault(key, 0L);
    }

    @SuppressWarnings("null")
    public long add(String key, long amount) {
        long updated = stats.merge(key, amount, Long::sum);
        dirty.set(true);
        return updated;
    }

    public void set(String key, long value) {
        stats.put(key, value);
        dirty.set(true);
    }

    public void addPlaytimeSeconds(long seconds) {
        this.playtimeSeconds += seconds;
        dirty.set(true);
    }

    public boolean isDirty() { return dirty.get(); }
    public void clearDirty() { dirty.set(false); }
    public void markDirty() { dirty.set(true); }

    public Map<String, Long> stats() { return Collections.unmodifiableMap(stats); }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; dirty.set(true); }
    public long firstJoin() { return firstJoin; }
    public long lastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; dirty.set(true); }
    public long playtimeSeconds() { return playtimeSeconds; }
    public String seasonId() { return seasonId; }
}
