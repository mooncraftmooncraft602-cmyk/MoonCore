package com.mooncore.core.module;

/** Cycle de vie d'un module. */
public enum ModuleState {
    /** Instancié et enregistré, pas encore activé. */
    REGISTERED,
    /** Activation en cours. */
    ENABLING,
    /** Actif et opérationnel. */
    ENABLED,
    /** Désactivation en cours. */
    DISABLING,
    /** Désactivé proprement. */
    DISABLED,
    /** Désactivé par configuration (jamais chargé). */
    DISABLED_BY_CONFIG,
    /** Échec d'activation (lui-même ou une dépendance). */
    FAILED
}
