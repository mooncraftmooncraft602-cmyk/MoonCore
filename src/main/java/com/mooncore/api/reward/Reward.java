package com.mooncore.api.reward;

import java.util.List;

/** Une récompense nommée = une liste ordonnée d'{@link RewardAction}. */
public record Reward(String id, List<RewardAction> actions) {
    public Reward {
        if (actions == null) actions = List.of();
    }
}
