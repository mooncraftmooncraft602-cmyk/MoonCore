package com.mooncore.api.economy;

import com.mooncore.core.event.MoonEvent;

import java.util.UUID;

/**
 * Émis quand les gains cumulés d'un joueur sur la fenêtre d'observation dépassent le seuil
 * configuré (potentielle ferme à argent / exploit). Statistics et AdminTools peuvent réagir.
 */
public record AbnormalGainEvent(UUID player, double windowTotal, String lastReason) implements MoonEvent {
}
