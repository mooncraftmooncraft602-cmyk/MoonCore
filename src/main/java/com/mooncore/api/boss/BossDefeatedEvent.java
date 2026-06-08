package com.mooncore.api.boss;

import com.mooncore.core.event.MoonEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Émis quand un boss est vaincu. {@code damageByPlayer} liste les dégâts infligés par
 * chaque joueur (pour la distribution du loot et les statistiques).
 */
public record BossDefeatedEvent(String bossId, UUID topDamager, Map<UUID, Double> damageByPlayer)
        implements MoonEvent {
}
