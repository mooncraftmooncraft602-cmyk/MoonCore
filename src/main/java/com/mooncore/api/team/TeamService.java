package com.mooncore.api.team;

import java.util.Optional;
import java.util.UUID;

/**
 * Service public d'équipe, exposé via le ServiceRegistry par le futur module Teams.
 * Les modules qui regroupent par équipe (AntiFarm, classements…) le consomment en
 * dépendance <b>molle</b> : si absent, le comportement par équipe est simplement ignoré.
 */
public interface TeamService {

    /** Identifiant d'équipe du joueur, le cas échéant. */
    Optional<String> teamId(UUID player);

    /** Nombre de membres d'une équipe (0 si inconnue). */
    int memberCount(String teamId);
}
