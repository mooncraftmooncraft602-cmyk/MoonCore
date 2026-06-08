package com.mooncore.modules.event;

/** Actions composables d'un événement (data-driven). */
public enum EventActionType {
    BROADCAST,   // value (MiniMessage)
    TITLE,       // title, subtitle
    SOUND,       // sound (nom Bukkit)
    COMMAND,     // command (console, %player% par joueur en ligne)
    SPAWN_BOSS,  // boss, world, x, y, z
    REWARD_ALL,  // reward (id, à tous les joueurs en ligne)
    XP_ALL,      // amount (XP de progression à tous)
    ZONE_FLAG    // region, flag, flag-value
}
