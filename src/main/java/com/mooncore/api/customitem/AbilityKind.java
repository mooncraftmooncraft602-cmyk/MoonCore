package com.mooncore.api.customitem;

/** Nature d'une capacité portée par un objet custom. */
public enum AbilityKind {
    /** Déclenchée par le joueur (clic droit). Soumise à un cooldown. */
    ACTIVE,
    /** Toujours active tant que l'objet est tenu/porté. */
    PASSIVE
}
