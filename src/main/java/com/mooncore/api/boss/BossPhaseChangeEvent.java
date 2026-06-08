package com.mooncore.api.boss;

import com.mooncore.core.event.MoonEvent;

import java.util.UUID;

/** Émis quand un boss change de phase (pour intensifier la musique, etc.). */
public record BossPhaseChangeEvent(String bossId, UUID entityId, String phaseName) implements MoonEvent {
}
