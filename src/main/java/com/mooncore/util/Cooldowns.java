package com.mooncore.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cooldowns par clé, génériques et purs (horloge injectée). Réutilisable par les modules
 * (objets endgame, capacités, etc.).
 */
public final class Cooldowns<K> {

    private final Map<K, Long> lastUse = new ConcurrentHashMap<>();

    /** Millisecondes restantes avant disponibilité (0 si prêt). */
    public long remaining(K key, long nowMs, long durationMs) {
        Long last = lastUse.get(key);
        if (last == null) return 0;
        long elapsed = nowMs - last;
        return elapsed >= durationMs ? 0 : durationMs - elapsed;
    }

    /** Tente d'acquérir : démarre le cooldown et retourne true si c'était prêt. */
    public boolean tryAcquire(K key, long nowMs, long durationMs) {
        if (remaining(key, nowMs, durationMs) > 0) return false;
        lastUse.put(key, nowMs);
        return true;
    }

    public void clear(K key) { lastUse.remove(key); }
    public void clearAll() { lastUse.clear(); }
}
