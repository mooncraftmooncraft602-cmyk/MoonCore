package com.mooncore.api.boss;

import com.mooncore.core.event.MoonEvent;

import java.util.UUID;

/** Émis quand un boss apparaît (pour déclencher musique, annonces, etc.). */
public record BossSpawnEvent(String bossId, UUID entityId) implements MoonEvent {
}
