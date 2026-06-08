package com.mooncore.modules.boss;

/** Capacités de boss disponibles (data-driven via YAML). */
public enum BossAbilityType {
    SUMMON,       // invoque des sbires (count, magnitude=type via param plus tard)
    HEAL,         // régénère des PV (magnitude)
    POISON,       // empoisonne les joueurs proches (magnitude = durée en ticks)
    IGNITE,       // enflamme les joueurs proches (magnitude = durée en ticks)
    EXPLODE,      // explosion au niveau du boss (magnitude = puissance)
    AOE_DAMAGE,   // dégâts de zone (magnitude = dégâts)
    TELEPORT,     // se téléporte vers un joueur proche
    DASH,         // bondit vers le joueur le plus proche (magnitude = force)
    SHIELD        // résistance temporaire (magnitude = durée en ticks)
}
