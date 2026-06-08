package com.mooncore.modules.quest;

import com.mooncore.api.mission.ObjectiveType;

/** Une étape de quête : objectif à atteindre + récompense d'étape facultative. */
public record QuestStep(String description, ObjectiveType type, int target, String rewardId) {
    public QuestStep {
        if (target <= 0) target = 1;
    }
}
