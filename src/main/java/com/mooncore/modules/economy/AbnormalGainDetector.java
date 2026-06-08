package com.mooncore.modules.economy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Détecte les gains anormaux : somme glissante des crédits d'un joueur sur une fenêtre.
 * Si le total dépasse le seuil, signale (une seule fois jusqu'à repasser sous le seuil).
 * Logique pure (horloge injectée).
 */
public final class AbnormalGainDetector {

    private record Gain(long time, double amount) {}

    private final long windowMs;
    private final double threshold;

    private final Map<UUID, Deque<Gain>> gains = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> flagged = new ConcurrentHashMap<>();

    public AbnormalGainDetector(long windowMs, double threshold) {
        this.windowMs = windowMs;
        this.threshold = threshold;
    }

    /**
     * Enregistre un gain et retourne {@code true} si le seuil vient d'être franchi
     * (transition sous→sur le seuil). Reste {@code false} tant qu'on est déjà au-dessus.
     */
    public boolean record(UUID player, double amount, long nowMs) {
        if (threshold <= 0) return false;
        Deque<Gain> dq = gains.computeIfAbsent(player, k -> new ArrayDeque<>());
        double total;
        synchronized (dq) {
            dq.addLast(new Gain(nowMs, amount));
            total = pruneAndSum(dq, nowMs);
        }
        boolean over = total >= threshold;
        Boolean was = flagged.put(player, over);
        return over && (was == null || !was);
    }

    public double windowTotal(UUID player, long nowMs) {
        Deque<Gain> dq = gains.get(player);
        if (dq == null) return 0;
        synchronized (dq) {
            return pruneAndSum(dq, nowMs);
        }
    }

    private double pruneAndSum(Deque<Gain> dq, long nowMs) {
        long cutoff = nowMs - windowMs;
        double sum = 0;
        while (!dq.isEmpty() && dq.peekFirst().time() < cutoff) {
            dq.pollFirst();
        }
        for (Gain g : dq) sum += g.amount();
        return sum;
    }

    public void cleanup(long nowMs) {
        gains.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                boolean empty = pruneAndSum(e.getValue(), nowMs) == 0 && e.getValue().isEmpty();
                if (empty) flagged.remove(e.getKey());
                return empty;
            }
        });
    }
}
