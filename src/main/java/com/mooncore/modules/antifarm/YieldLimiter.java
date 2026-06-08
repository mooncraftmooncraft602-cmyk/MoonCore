package com.mooncore.modules.antifarm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Réduction progressive des rendements de farm. Compte les kills d'un joueur sur une
 * fenêtre glissante : sous {@code softCap}, rendement plein (×1) ; au-delà, le facteur
 * décroît linéairement jusqu'à {@code minFactor} atteint à {@code hardCap}.
 * <p>
 * Logique pure : l'horloge est injectée ({@code nowMs}) pour être testable.
 */
public final class YieldLimiter {

    private final int softCap;
    private final int hardCap;
    private final long windowMs;
    private final double minFactor;

    private final Map<UUID, Deque<Long>> kills = new ConcurrentHashMap<>();

    public YieldLimiter(int softCap, int hardCap, long windowMs, double minFactor) {
        this.softCap = Math.max(0, softCap);
        this.hardCap = Math.max(this.softCap + 1, hardCap);
        this.windowMs = windowMs;
        this.minFactor = Math.max(0.0, Math.min(1.0, minFactor));
    }

    /** Enregistre un kill et retourne le facteur de rendement applicable (0..1). */
    public double recordAndFactor(UUID player, long nowMs) {
        Deque<Long> dq = kills.computeIfAbsent(player, k -> new ArrayDeque<>());
        synchronized (dq) {
            dq.addLast(nowMs);
            prune(dq, nowMs);
            return factorFor(dq.size());
        }
    }

    /** Facteur courant sans enregistrer de kill. */
    public double factor(UUID player, long nowMs) {
        Deque<Long> dq = kills.get(player);
        if (dq == null) return 1.0;
        synchronized (dq) {
            prune(dq, nowMs);
            return factorFor(dq.size());
        }
    }

    public int recentKills(UUID player, long nowMs) {
        Deque<Long> dq = kills.get(player);
        if (dq == null) return 0;
        synchronized (dq) {
            prune(dq, nowMs);
            return dq.size();
        }
    }

    private double factorFor(int count) {
        if (count <= softCap) return 1.0;
        if (count >= hardCap) return minFactor;
        double t = (double) (count - softCap) / (hardCap - softCap);
        return 1.0 - t * (1.0 - minFactor);
    }

    private void prune(Deque<Long> dq, long nowMs) {
        long cutoff = nowMs - windowMs;
        while (!dq.isEmpty() && dq.peekFirst() < cutoff) {
            dq.pollFirst();
        }
    }

    /** Purge périodique des joueurs inactifs (appel hors chemin chaud). */
    public void cleanup(long nowMs) {
        kills.entrySet().removeIf(entry -> {
            Deque<Long> dq = entry.getValue();
            synchronized (dq) {
                prune(dq, nowMs);
                return dq.isEmpty();
            }
        });
    }
}
