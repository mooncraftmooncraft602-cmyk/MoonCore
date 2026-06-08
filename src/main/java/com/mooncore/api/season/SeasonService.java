package com.mooncore.api.season;

/**
 * Service public de saison. Les données saisonnières sont partitionnées par {@code seasonId}
 * (colonne dans les tables) : changer de saison repart d'un état vierge tout en archivant
 * naturellement l'ancienne saison.
 */
public interface SeasonService {

    /** Saison active courante. */
    SeasonInfo current();

    /** Jours restants avant la fin (−1 si pas de fin définie, 0 si terminée). */
    long daysRemaining();
}
