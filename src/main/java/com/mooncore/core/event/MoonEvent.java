package com.mooncore.core.event;

/**
 * Marqueur des événements internes MoonCore diffusés via l'{@link EventBus}.
 * <p>
 * Distinct des {@code org.bukkit.event.Event} : permet aux modules de réagir à des
 * faits métier (montée de tier, boss vaincu, gain anormal…) sans coupler producteur
 * et consommateur.
 */
public interface MoonEvent {
}
