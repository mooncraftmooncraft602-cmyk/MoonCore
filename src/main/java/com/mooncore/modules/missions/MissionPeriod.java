package com.mooncore.modules.missions;

import com.mooncore.api.mission.MissionScope;

/**
 * Calcule la clé de période d'une mission selon sa portée. Une nouvelle clé = nouvelle
 * période = compteurs remis à zéro. Basé sur UTC. Logique pure et testable.
 */
public final class MissionPeriod {

    private static final long DAY_MS = 86_400_000L;

    private MissionPeriod() {}

    public static String key(MissionScope scope, long epochMillis, String seasonId) {
        long epochDay = Math.floorDiv(epochMillis, DAY_MS);
        return switch (scope) {
            case DAILY -> "D" + epochDay;
            // Semaines alignées sur l'epoch (jeudi 1970-01-01) ; cohérent et sans dépendance calendrier.
            case WEEKLY -> "W" + Math.floorDiv(epochDay, 7);
            case SEASONAL -> "S" + seasonId;
        };
    }
}
