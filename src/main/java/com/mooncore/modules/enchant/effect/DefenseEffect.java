package com.mooncore.modules.enchant.effect;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

/** Effet déclenché quand le porteur subit des dégâts. */
@FunctionalInterface
public interface DefenseEffect {
    void onDamaged(Player defender, int level, EntityDamageEvent event);
}
