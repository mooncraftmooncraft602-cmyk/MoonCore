package com.mooncore.modules.enchant.effect;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Effet déclenché quand le porteur frappe une cible en mêlée. */
@FunctionalInterface
public interface MeleeHitEffect {
    void onHit(Player attacker, LivingEntity victim, int level, EntityDamageByEntityEvent event);
}
