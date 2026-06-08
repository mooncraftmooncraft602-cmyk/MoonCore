package com.mooncore.api.afk;

import java.util.UUID;

/**
 * Service public AntiAFK, exposé via le ServiceRegistry. Les modules qui distribuent des
 * gains (AntiFarm, économie, missions…) le consultent pour réduire/annuler les récompenses
 * d'un joueur AFK, sans dépendre de l'implémentation.
 */
public interface AntiAfkService {

    /** {@code true} si le joueur est actuellement considéré AFK. */
    boolean isAfk(UUID player);

    /** Temps d'inactivité du joueur en millisecondes. */
    long idleMillis(UUID player);

    /**
     * Multiplicateur de gain à appliquer aux récompenses ({@code 1.0} si actif,
     * valeur réduite/0 si AFK selon la configuration).
     */
    double gainMultiplier(UUID player);
}
