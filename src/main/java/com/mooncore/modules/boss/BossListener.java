package com.mooncore.modules.boss;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Suit les dégâts infligés aux boss et déclenche la distribution du loot à leur mort. */
public final class BossListener implements Listener {

    private final BossManagerModule module;

    public BossListener(BossManagerModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (!module.isBoss(victim)) return;

        Player damager = resolvePlayer(e.getDamager());
        if (damager != null) {
            module.recordDamage(victim.getUniqueId(), damager.getUniqueId(), e.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        if (module.isBoss(e.getEntity())) {
            module.handleDeath(e.getEntity().getUniqueId());
        }
    }

    private Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
