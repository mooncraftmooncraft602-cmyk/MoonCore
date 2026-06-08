package com.mooncore.modules.event;

import java.util.List;

/**
 * Définition d'un événement. {@code autoIntervalSeconds} > 0 déclenche un démarrage
 * périodique automatique ; sinon l'événement est lancé manuellement.
 */
public record GameEvent(String id, String displayName, String type,
                        long durationSeconds, long autoIntervalSeconds,
                        List<EventAction> startActions, List<EventAction> endActions) {
    public GameEvent {
        if (startActions == null) startActions = List.of();
        if (endActions == null) endActions = List.of();
    }
}
