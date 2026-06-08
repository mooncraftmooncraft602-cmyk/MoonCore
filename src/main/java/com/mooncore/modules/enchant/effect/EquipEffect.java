package com.mooncore.modules.enchant.effect;

import org.bukkit.entity.Player;

/**
 * Effet passif appliqué périodiquement tant que l'objet est équipé/tenu. Typiquement un
 * effet de potion rafraîchi à chaque tick (s'auto-retire quand l'objet est retiré).
 */
@FunctionalInterface
public interface EquipEffect {
    void onTick(Player player, int level);
}
