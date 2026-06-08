package com.mooncore.api.mission;

/**
 * Définition d'une mission (data-driven). {@code rewardId} référence une récompense du
 * RewardManager ; {@code progressionXp} est l'XP de progression accordée à la complétion.
 */
public record Mission(String id, MissionScope scope, ObjectiveType type, int target,
                      String rewardId, String description, long progressionXp) {
    public Mission {
        if (target <= 0) target = 1;
    }
}
