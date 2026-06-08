package com.mooncore.api.reward;

import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/**
 * Service public de récompenses. Les modules (progression, missions, quêtes, events) y
 * délèguent l'octroi de gains, en référençant une récompense par id (définie en YAML) ou
 * en passant une {@link Reward} construite à la volée.
 */
public interface RewardService {

    /** Octroie immédiatement une récompense (à appeler sur le thread principal). */
    void give(Player player, Reward reward);

    /** Octroie une récompense nommée. Retourne false si l'id est inconnu. */
    boolean give(Player player, String rewardId);

    /**
     * Octroie une récompense une seule fois par (joueur, source) : idempotent grâce à la
     * table {@code reward_claim}. Retourne true si la récompense a été octroyée, false si
     * déjà réclamée (ou id inconnu).
     */
    CompletableFuture<Boolean> claimOnce(Player player, String rewardId, String source);

    /** Récompense nommée, ou null si inconnue. */
    Reward reward(String id);
}
