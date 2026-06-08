package com.mooncore.api.stats;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service public de statistiques. Les modules y enregistrent des compteurs (kills, blocs,
 * missions…) et les lisent pour les classements, missions et progression.
 * <p>
 * Les écritures sont en write-behind sur le profil en cache (joueur en ligne) ; pour un
 * joueur hors-ligne, l'incrément est poussé en base de façon asynchrone.
 */
public interface StatisticsService {

    /** Valeur courante d'une stat pour un joueur EN LIGNE (0 si hors-ligne/non chargé). */
    long get(UUID player, String statKey);

    /** Incrémente une stat (montant possiblement négatif) avec une raison d'audit. */
    void increment(UUID player, String statKey, long amount, String reason);

    /** Fixe une stat à une valeur précise. */
    void set(UUID player, String statKey, long value, String reason);

    /** {@code true} si le profil du joueur est chargé en mémoire. */
    boolean isLoaded(UUID player);

    /** Snapshot des stats d'un joueur en ligne (vide si non chargé). */
    Map<String, Long> snapshot(UUID player);

    /** Charge (async) les stats d'un joueur, en ligne ou non, pour affichage. */
    CompletableFuture<Map<String, Long>> loadAsync(UUID player);
}
