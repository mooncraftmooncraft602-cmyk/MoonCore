package com.mooncore.modules.season;

/** Calculs de calendrier de saison (purs, testables). */
public final class Seasons {

    private static final long DAY_MS = 86_400_000L;

    private Seasons() {}

    /** Jours restants : −1 si pas de fin, 0 si déjà terminée, sinon arrondi au jour supérieur. */
    public static long daysRemaining(long nowMs, long endsAtMs) {
        if (endsAtMs <= 0) return -1;
        if (nowMs >= endsAtMs) return 0;
        return (long) Math.ceil((endsAtMs - nowMs) / (double) DAY_MS);
    }

    /** Date de fin à partir d'un début et d'une durée en jours (0 = pas de fin). */
    public static long endFromStart(long startMs, int lengthDays) {
        return lengthDays <= 0 ? 0 : startMs + lengthDays * DAY_MS;
    }
}
