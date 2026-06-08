package com.mooncore.api.mission;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Service public de missions (journalières/hebdo/saisonnières). */
public interface MissionService {

    /** Missions définies pour une portée. */
    List<Mission> missions(MissionScope scope);

    /** Progression courante du joueur sur une mission (période en cours). */
    int progress(UUID player, String missionId);

    /** {@code true} si la mission est complétée (progression ≥ objectif). */
    boolean isComplete(UUID player, String missionId);

    /** {@code true} si la récompense de la mission a déjà été réclamée pour la période. */
    boolean isClaimed(UUID player, String missionId);

    /** Réclame la récompense. false si inconnue, non complétée ou déjà réclamée. */
    CompletableFuture<Boolean> claim(Player player, String missionId);
}
