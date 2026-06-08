package com.mooncore.modules.zone;

import com.mooncore.MoonCore;
import com.mooncore.api.zone.ZoneFlag;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applique les {@link ZoneFlag} d'interaction avec le monde. Les flags consommés par
 * d'autres modules (NO_HOME, NO_TPA, NO_CLAIM, NO_SPAWNER côté limites) ne sont pas
 * traités ici mais via {@code ZoneService}.
 */
public final class ZoneListener implements Listener {

    private static final long DENY_MSG_COOLDOWN_MS = 1500;

    private final MoonCore plugin;
    private final ZoneModule zone;
    private final Map<UUID, Long> lastDenyMsg = new ConcurrentHashMap<>();

    public ZoneListener(MoonCore plugin, ZoneModule zone) {
        this.plugin = plugin;
        this.zone = zone;
    }

    // ---- Casse / pose ----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (zone.bypasses(e.getPlayer())) return;
        Location l = e.getBlock().getLocation();
        if (zone.flag(l, ZoneFlag.NO_BLOCK_BREAK) || zone.flag(l, ZoneFlag.NO_GRIEF)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (zone.bypasses(e.getPlayer())) return;
        Location l = e.getBlock().getLocation();
        if (zone.flag(l, ZoneFlag.NO_BLOCK_PLACE) || zone.flag(l, ZoneFlag.NO_GRIEF)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ---- Interactions ----

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (zone.bypasses(p)) return;

        // Ender pearl
        if (e.getItem() != null && e.getItem().getType() == Material.ENDER_PEARL
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (zone.flag(p.getLocation(), ZoneFlag.NO_ENDERPEARL)) {
                e.setCancelled(true);
                deny(p);
                return;
            }
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
            if (zone.flag(e.getClickedBlock().getLocation(), ZoneFlag.NO_INTERACT)) {
                e.setCancelled(true);
                deny(p);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (zone.bypasses(p)) return;
        if (e.getEntity().getType() == org.bukkit.entity.EntityType.ENDER_PEARL
                && zone.flag(p.getLocation(), ZoneFlag.NO_ENDERPEARL)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    // ---- Combat / dégâts ----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Player attacker)) return;
        if (zone.bypasses(attacker)) return;
        if (zone.flag(victim.getLocation(), ZoneFlag.NO_PVP)) {
            e.setCancelled(true);
            deny(attacker);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        if (zone.flag(victim.getLocation(), ZoneFlag.NO_DAMAGE)) {
            e.setCancelled(true);
        }
    }

    // ---- Items ----

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (zone.bypasses(e.getPlayer())) return;
        if (zone.flag(e.getPlayer().getLocation(), ZoneFlag.NO_ITEM_DROP)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (zone.bypasses(p)) return;
        if (zone.flag(e.getItem().getLocation(), ZoneFlag.NO_ITEM_PICKUP)) {
            e.setCancelled(true);
        }
    }

    // ---- Spawns ----

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        Location l = e.getLocation();
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER
                && zone.flag(l, ZoneFlag.NO_SPAWNER)) {
            e.setCancelled(true);
            return;
        }
        if (zone.flag(l, ZoneFlag.NO_MOB_SPAWN)) {
            e.setCancelled(true);
        }
    }

    // ---- Lit / élytres / commandes ----

    @EventHandler(ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (zone.bypasses(e.getPlayer())) return;
        if (zone.flag(e.getBed().getLocation(), ZoneFlag.NO_BED)) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!e.isGliding()) return;
        if (zone.bypasses(p)) return;
        if (zone.flag(p.getLocation(), ZoneFlag.NO_ELYTRA)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (zone.bypasses(p)) return;
        if (zone.flag(p.getLocation(), ZoneFlag.NO_COMMAND)) {
            e.setCancelled(true);
            deny(p);
        }
    }

    // ---- Explosions (grief) ----

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> zone.flag(b.getLocation(), ZoneFlag.NO_GRIEF));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> zone.flag(b.getLocation(), ZoneFlag.NO_GRIEF));
    }

    // ---- Mouvement (enter/leave/flight) ----

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        // Garde-fou perf : ne traiter que les changements de bloc.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        if (zone.bypasses(p)) return;

        if (zone.flag(to, ZoneFlag.NO_ENTER) && !zone.flag(from, ZoneFlag.NO_ENTER)) {
            e.setTo(from);
            deny(p);
            return;
        }
        if (zone.flag(from, ZoneFlag.NO_LEAVE) && !zone.flag(to, ZoneFlag.NO_LEAVE)) {
            e.setTo(from);
            deny(p);
            return;
        }
        if (zone.flag(to, ZoneFlag.NO_FLIGHT) && p.isFlying()
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }

    private void deny(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastDenyMsg.get(player.getUniqueId());
        if (last != null && now - last < DENY_MSG_COOLDOWN_MS) return;
        lastDenyMsg.put(player.getUniqueId(), now);
        player.sendMessage(plugin.configManager().prefixed("zone-denied"));
    }
}
