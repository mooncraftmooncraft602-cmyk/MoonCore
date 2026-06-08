package com.mooncore.modules.audio;

/**
 * Sources audio par ordre de priorité décroissante. La résolution choisit toujours la
 * source active la plus prioritaire (voir {@link AudioPriorityResolver}).
 */
public enum AudioSource {
    GLOBAL_LOOP,   // 1 — priorité maximale
    PLAYER_LOOP,   // 2
    EVENT,         // 3 — events + boss
    ZONE,          // 4
    DEFAULT        // 5
}
