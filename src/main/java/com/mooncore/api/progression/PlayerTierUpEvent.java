package com.mooncore.api.progression;

import com.mooncore.core.event.MoonEvent;

import java.util.UUID;

/** Émis quand un joueur change de tier de progression saisonnière. */
public record PlayerTierUpEvent(UUID player, int oldTier, int newTier) implements MoonEvent {
}
