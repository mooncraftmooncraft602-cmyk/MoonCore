package com.mooncore.api.progression;

import java.util.UUID;

/**
 * Service public de progression saisonnière. Les modules accordent de l'XP (boss, missions,
 * quêtes…) et vérifient les déblocages pour conditionner l'accès au contenu (gating).
 */
public interface ProgressionService {

    /** Tier courant du joueur (1..maxTier). Renvoie 1 si non chargé. */
    int tier(UUID player);

    /** XP total accumulé sur la saison. */
    long xp(UUID player);

    /** Seuil d'XP du tier suivant, ou -1 si le joueur est au tier maximum. */
    long nextTierXp(UUID player);

    /** Accorde de l'XP (déclenche une montée de tier si le seuil est franchi). */
    void addXp(UUID player, long amount, String reason);

    /** {@code true} si la fonctionnalité {@code feature} est débloquée au tier du joueur. */
    boolean isUnlocked(UUID player, String feature);

    /** Tier maximum défini par la configuration. */
    int maxTier();
}
