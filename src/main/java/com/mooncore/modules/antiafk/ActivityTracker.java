package com.mooncore.modules.antiafk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Suit la dernière activité de chaque joueur et son état AFK. Logique pure (horloge
 * injectée) pour être testable sans serveur.
 */
public final class ActivityTracker {

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> afk = new ConcurrentHashMap<>();

    /** Enregistre une activité (réinitialise le compteur d'inactivité). */
    public void record(UUID player, long nowMs) {
        lastActivity.put(player, nowMs);
    }

    public long idleMillis(UUID player, long nowMs) {
        Long last = lastActivity.get(player);
        return last == null ? 0L : Math.max(0L, nowMs - last);
    }

    public boolean isAfk(UUID player) {
        return afk.getOrDefault(player, false);
    }

    /**
     * Recalcule l'état AFK du joueur selon le seuil et retourne {@code true} si l'état
     * a changé lors de cet appel (transition à signaler/logger).
     */
    public boolean evaluate(UUID player, long nowMs, long thresholdMs) {
        boolean shouldBeAfk = idleMillis(player, nowMs) >= thresholdMs;
        Boolean current = afk.get(player);
        boolean prev = current != null && current; // baseline : actif tant qu'inconnu
        if (prev != shouldBeAfk) {
            afk.put(player, shouldBeAfk);
            return true;
        }
        if (current == null) {
            afk.put(player, shouldBeAfk); // initialise silencieusement, sans transition
        }
        return false;
    }

    public void remove(UUID player) {
        lastActivity.remove(player);
        afk.remove(player);
    }

    public java.util.Set<UUID> tracked() {
        return lastActivity.keySet();
    }

    public void clear() {
        lastActivity.clear();
        afk.clear();
    }
}
