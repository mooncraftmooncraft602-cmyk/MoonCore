package com.mooncore.api.event;

import com.mooncore.core.event.MoonEvent;

/** Émis quand un événement de jeu démarre ou se termine. */
public record EventStateEvent(String eventId, boolean started) implements MoonEvent {
}
