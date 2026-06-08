package com.mooncore.modules.antifarm;

import com.mooncore.MoonCore;
import com.mooncore.api.zone.ZoneFlag;
import com.mooncore.api.zone.ZoneService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/**
 * Applique les règles AntiFarm : limites de spawners (pose), plafond d'entités par chunk
 * (spawn) et réduction de rendement (mort de mob).
 */
public final class AntiFarmListener implements Listener {

    private final MoonCore plugin;
    private final AntiFarmModule module;

    public AntiFarmListener(MoonCore plugin, AntiFarmModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    // ---- Pose de spawner : limites + flag de zone ----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getType() != Material.SPAWNER) return;
        Player p = e.getPlayer();
        if (module.bypasses(p)) return;

        Block b = e.getBlockPlaced();
        Location l = b.getLocation();
        String world = l.getWorld().getName();
        var cm = plugin.configManager();

        // Flag de zone nospawner (si le module Zone est présent).
        Optional<ZoneService> zone = plugin.services().get(ZoneService.class);
        if (zone.isPresent() && zone.get().flag(l, ZoneFlag.NO_SPAWNER)) {
            e.setCancelled(true);
            p.sendMessage(cm.prefixed("antifarm-spawner-zone"));
            return;
        }

        int cx = l.getBlockX() >> 4, cz = l.getBlockZ() >> 4;
        if (module.maxPerChunk() > 0 && module.registry().chunkCount(world, cx, cz) >= module.maxPerChunk()) {
            e.setCancelled(true);
            p.sendMessage(cm.prefixed("antifarm-spawner-chunk", "max", String.valueOf(module.maxPerChunk())));
            return;
        }
        if (module.maxPerPlayer() > 0 && module.registry().ownerCount(p.getUniqueId()) >= module.maxPerPlayer()) {
            e.setCancelled(true);
            p.sendMessage(cm.prefixed("antifarm-spawner-player", "max", String.valueOf(module.maxPerPlayer())));
            return;
        }

        String team = module.teamOf(p.getUniqueId()).orElse(null);
        if (team != null && module.maxPerTeam() > 0 && module.registry().teamCount(team) >= module.maxPerTeam()) {
            e.setCancelled(true);
            p.sendMessage(cm.prefixed("antifarm-spawner-team", "max", String.valueOf(module.maxPerTeam())));
            return;
        }

        // Différé d'un tick : un handler postérieur (HIGHEST/MONITOR d'un plugin de protection) peut
        // encore annuler la pose. On ne compte/persiste le spawner que s'il est réellement posé,
        // sinon un slot « fantôme » bloquerait de futures poses légitimes et serait persisté au reload.
        SpawnerRegistry.Entry entry = new SpawnerRegistry.Entry(
                world, l.getBlockX(), l.getBlockY(), l.getBlockZ(), p.getUniqueId(), team);
        plugin.schedulers().syncLater(() -> {
            if (b.getType() != Material.SPAWNER) return; // pose annulée entre-temps
            if (module.registry().add(entry)) {
                module.store().save(entry);
            }
        }, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER) return;
        Location l = e.getBlock().getLocation();
        SpawnerRegistry.Entry removed = module.registry().remove(
                l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        if (removed != null) {
            module.store().delete(removed.locKey());
        }
    }

    // ---- Plafond d'entités par chunk (protection perfs) ----

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (module.entityMaxPerChunk() <= 0) return;
        if (!module.countedReasons().contains(e.getSpawnReason())) return;

        // Comptage borné : un seul chunk (déjà chargé puisqu'un mob y apparaît).
        int living = 0;
        for (Entity ent : e.getLocation().getChunk().getEntities()) {
            if (ent instanceof LivingEntity && !(ent instanceof Player)) living++;
        }
        if (living >= module.entityMaxPerChunk()) {
            e.setCancelled(true);
            int chunkX = e.getLocation().getBlockX() >> 4;
            int chunkZ = e.getLocation().getBlockZ() >> 4;
            module.alertAdmins(plugin.configManager().message("antifarm-alert-density",
                    "chunk", chunkX + "," + chunkZ,
                    "count", String.valueOf(living)));
        }
    }

    // ---- Réduction progressive des rendements ----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        if (!module.yieldEnabled()) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null || module.bypasses(killer)) return;

        double yieldFactor = module.yieldLimiter().recordAndFactor(killer.getUniqueId(), System.currentTimeMillis());
        // Combine avec AntiAFK (softdep) : un kill AFK rapporte le multiplicateur configuré.
        double afkMult = plugin.services().get(com.mooncore.api.afk.AntiAfkService.class)
                .map(s -> s.gainMultiplier(killer.getUniqueId()))
                .orElse(1.0);
        final double factor = yieldFactor * afkMult;
        if (factor >= 1.0) return;

        e.getDrops().forEach(stack -> {
            int reduced = (int) Math.floor(stack.getAmount() * factor);
            stack.setAmount(Math.max(0, reduced));
        });
        e.getDrops().removeIf(stack -> stack.getAmount() <= 0);
        e.setDroppedExp((int) Math.floor(e.getDroppedExp() * factor));
    }
}
