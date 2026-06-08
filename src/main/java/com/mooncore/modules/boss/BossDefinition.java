package com.mooncore.modules.boss;

import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Définition data-driven d'un boss (chargée depuis YAML).
 */
public record BossDefinition(
        String id,
        String displayName,
        EntityType entityType,
        double maxHealth,
        double damage,
        double speed,
        double armor,
        List<BossPhase> phases,
        String lootRewardId,
        long progressionXp,
        String barColor) {

    public BossDefinition {
        if (phases == null || phases.isEmpty()) {
            phases = List.of(new BossPhase("default", 100, List.of()));
        }
        if (maxHealth <= 0) maxHealth = 100;
    }
}
