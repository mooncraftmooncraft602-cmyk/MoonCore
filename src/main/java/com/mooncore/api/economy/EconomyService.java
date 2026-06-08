package com.mooncore.api.economy;

import java.util.UUID;

/**
 * Façade économique de MoonCore au-dessus de Vault. Les modules qui manipulent de l'argent
 * (boutiques, récompenses, missions…) passent par ici pour bénéficier automatiquement des
 * taxes, du journal d'audit ({@code economy_ledger}) et de la détection de gains anormaux.
 */
public interface EconomyService {

    /** {@code true} si un fournisseur Vault est disponible. */
    boolean isAvailable();

    double balance(UUID player);

    boolean has(UUID player, double amount);

    /** Retire un montant. Retourne false si solde insuffisant ou économie indisponible. */
    boolean withdraw(UUID player, double amount, String reason);

    /** Crédite un montant brut (sans taxe). */
    void deposit(UUID player, double amount, String reason);

    /**
     * Crédite un gain en appliquant la taxe de transaction configurée, journalise et
     * alimente la détection de gains anormaux. Retourne le montant net réellement crédité.
     */
    double depositWithTax(UUID player, double gross, String reason);

    /** Montant de taxe de transaction pour un gain brut donné. */
    double transactionTax(double gross);
}
