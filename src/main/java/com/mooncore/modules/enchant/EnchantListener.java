package com.mooncore.modules.enchant;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aiguille les événements vers les effets d'enchant : mêlée, défense, minage, plus les
 * déclencheurs spéciaux Dash (échange de main) et Double Saut (bascule de vol).
 */
public final class EnchantListener implements Listener {

    private static final long DASH_COOLDOWN_MS = 3000;

    private final EnchantManagerModule mgr;
    private final Map<UUID, Long> dashCooldown = new ConcurrentHashMap<>();

    public EnchantListener(EnchantManagerModule mgr) {
        this.mgr = mgr;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        Player attacker = resolvePlayer(e.getDamager());
        if (attacker != null) {
            mgr.dispatchMelee(attacker, victim, e);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player def) {
            mgr.dispatchDefense(def, e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent e) {
        mgr.dispatchMining(e.getPlayer(), e.getBlock(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDash(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        int level = mgr.armorLevel(p, "dash");
        if (level <= 0) return;
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = dashCooldown.get(p.getUniqueId());
        if (last != null && now - last < DASH_COOLDOWN_MS) return;
        dashCooldown.put(p.getUniqueId(), now);

        Vector dir = p.getLocation().getDirection().normalize().multiply(1.0 + 0.4 * level);
        dir.setY(Math.max(0.3, dir.getY()));
        p.setVelocity(dir);
        p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.02);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDoubleJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE
                || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (mgr.armorLevel(p, "double-saut") <= 0) return;

        e.setCancelled(true);
        p.setFlying(false);
        p.setAllowFlight(false); // ré-autorisé au sol par l'effet d'équipement
        Vector v = p.getLocation().getDirection().multiply(0.5);
        v.setY(0.9);
        p.setVelocity(v);
        p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 12, 0.3, 0.05, 0.3, 0.02);
    }

    private Player resolvePlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
