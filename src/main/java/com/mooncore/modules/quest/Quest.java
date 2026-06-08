package com.mooncore.modules.quest;

import java.util.List;

/**
 * Quête scénarisée : suite ordonnée d'étapes, conditionnée par un tier minimum, avec une
 * récompense finale et de l'XP de progression.
 */
public record Quest(String id, String displayName, int requiredTier,
                    String finalRewardId, long progressionXp, List<QuestStep> steps) {
    public Quest {
        if (steps == null) steps = List.of();
    }
}
