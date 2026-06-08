package com.mooncore.modules.stats;

import com.mooncore.api.economy.AbnormalGainEvent;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.modules.antiafk.PlayerAfkChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Charge/sauvegarde les profils à la connexion/déconnexion et enregistre les stats de base.
 * S'abonne aussi à des événements internes MoonCore (AFK, gains anormaux) via l'EventBus.
 */
public final class StatsListener implements Listener {

    private final StatisticsModule module;

    public StatsListener(StatisticsModule module) {
        this.module = module;
    }

    // ---- Connexion ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        module.loadProfile(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        module.unloadProfile(e.getPlayer().getUniqueId());
    }

    // ---- Stats de base ----

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMine(BlockBreakEvent e) {
        module.increment(e.getPlayer().getUniqueId(), StatKeys.BLOCKS_MINED, 1, "block-break");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        module.increment(e.getPlayer().getUniqueId(), StatKeys.BLOCKS_PLACED, 1, "block-place");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return; // les kills de joueurs sont comptés via PlayerDeathEvent
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            module.increment(killer.getUniqueId(), StatKeys.MOB_KILLS, 1, "mob-kill");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        module.increment(victim.getUniqueId(), StatKeys.DEATHS, 1, "death");
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            module.increment(killer.getUniqueId(), StatKeys.PLAYER_KILLS, 1, "player-kill");
        }
    }

    // ---- Handlers EventBus (abonnés depuis StatisticsModule#onEnable) ----

    public void onAfkChange(PlayerAfkChangeEvent e) {
        if (e.nowAfk()) {
            module.increment(e.player(), StatKeys.AFK_EPISODES, 1, "afk");
        }
    }

    public void onAbnormalGain(AbnormalGainEvent e) {
        module.increment(e.player(), StatKeys.ABNORMAL_GAIN_FLAGS, 1, "abnormal-gain");
    }
}
