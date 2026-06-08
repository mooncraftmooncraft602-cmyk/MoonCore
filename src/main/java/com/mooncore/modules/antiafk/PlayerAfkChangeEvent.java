package com.mooncore.modules.antiafk;

import com.mooncore.core.event.MoonEvent;

import java.util.UUID;

/** Émis sur l'EventBus quand un joueur passe AFK ou en revient. */
public record PlayerAfkChangeEvent(UUID player, boolean nowAfk) implements MoonEvent {
}
