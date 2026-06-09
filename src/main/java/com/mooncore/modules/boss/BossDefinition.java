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
        String barColor,
        String textureKey,
        int textureCustomModelData,
        java.util.Map<String, String> equipment) {

    public BossDefinition {
        if (phases == null || phases.isEmpty()) {
            phases = List.of(new BossPhase("default", 100, List.of()));
        }
        if (maxHealth <= 0) maxHealth = 100;
        if (textureKey != null && textureKey.isBlank()) textureKey = null;
        if (equipment == null) equipment = java.util.Map.of();
    }
}
