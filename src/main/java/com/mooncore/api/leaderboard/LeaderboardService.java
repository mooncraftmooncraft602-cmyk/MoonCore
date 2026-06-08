package com.mooncore.api.leaderboard;

import java.util.List;
import java.util.Set;

/**
 * Service public de classements. Les snapshots sont précalculés en asynchrone et servis
 * depuis le cache (lecture instantanée, sans requête sur le thread principal).
 */
public interface LeaderboardService {

    /** Ids des classements configurés. */
    Set<String> boards();

    /** Snapshot courant d'un classement (vide si inconnu ou pas encore calculé). */
    List<LeaderboardEntry> top(String boardId);

    /** Titre lisible d'un classement. */
    String title(String boardId);
}
