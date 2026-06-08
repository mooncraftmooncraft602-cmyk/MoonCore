package com.mooncore.core.module;

import com.mooncore.MoonCore;

/**
 * Contrat d'un module MoonCore. Un module est une unité fonctionnelle autonome
 * (anti-farm, progression, boss…) activable/désactivable indépendamment.
 * <p>
 * L'implémentation de référence est {@link AbstractModule} ; les modules concrets
 * étendent celle-ci plutôt que d'implémenter cette interface directement.
 */
public interface MoonModule {

    /** Métadonnées (id, dépendances) issues de {@link ModuleInfo}. */
    ModuleInfo info();

    /** Identifiant unique (= {@code info().id()}). */
    String id();

    /** État courant. */
    ModuleState state();

    /** {@code true} si le module est actuellement ENABLED. */
    boolean isEnabled();

    /**
     * Appelé par le {@link ModuleManager} pour activer le module.
     * Ne pas appeler directement.
     */
    void enableModule(MoonCore plugin) throws Exception;

    /**
     * Appelé par le {@link ModuleManager} pour désactiver le module.
     * Ne pas appeler directement.
     */
    void disableModule();

    /** Rechargement à chaud de la configuration du module (sans cycle disable/enable). */
    void reloadModule();
}
