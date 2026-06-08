package com.mooncore.modules.antiafk;

import com.mooncore.api.afk.AntiAfkService;
import com.mooncore.command.sub.AntiAfkSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * AntiAFKManager : détecte l'inactivité (multi-signaux) et expose un multiplicateur de
 * gain via {@link AntiAfkService} pour que les autres modules réduisent les récompenses
 * des joueurs AFK. Tout est en mémoire (aucune persistance nécessaire).
 */
@ModuleInfo(id = "anti-afk", name = "AntiAFKManager", softDepends = {"statistics"})
public final class AntiAfkModule extends AbstractModule implements AntiAfkService {

    private final ActivityTracker tracker = new ActivityTracker();
    private BukkitTask scanTask;

    private long thresholdMs;
    private double gainMultiplierWhenAfk;
    private boolean rotationCounts;
    private boolean logTransitions;
    private boolean notifyPlayer;

    @Override
    protected void onEnable() {
        loadConfig();
        services().register(AntiAfkService.class, this);
        registerListener(new ActivityListener(this));
        plugin().rootCommand().register(new AntiAfkSubCommand(this));

        // Initialise les joueurs déjà connectés (reload à chaud).
        long now = System.currentTimeMillis();
        Bukkit.getOnlinePlayers().forEach(p -> tracker.record(p.getUniqueId(), now));

        // Scan régulier (1 s) sur le thread principal : transitions AFK + perms + messages.
        scanTask = schedulers().syncTimer(this::scan, 20L, 20L);
    }

    @Override
    protected void onDisable() {
        if (scanTask != null) scanTask.cancel();
        services().unregister(AntiAfkService.class);
        tracker.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration c = moduleConfig();
        this.thresholdMs = c.getLong("afk-threshold-seconds", 300) * 1000L;
        this.gainMultiplierWhenAfk = c.getDouble("gain-multiplier-when-afk", 0.0);
        this.rotationCounts = c.getBoolean("rotation-counts-as-activity", true);
        this.logTransitions = c.getBoolean("log-transitions", true);
        this.notifyPlayer = c.getBoolean("notify-player", true);
    }

    private void scan() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            // Le staff en bypass n'est jamais AFK : on rafraîchit son activité.
            if (p.hasPermission("mooncore.bypass.antiafk")) {
                tracker.record(id, now);
            }
            if (tracker.evaluate(id, now, thresholdMs)) {
                boolean afk = tracker.isAfk(id);
                onTransition(p, afk);
            }
        }
    }

    private void onTransition(Player p, boolean afk) {
        eventBus().post(new PlayerAfkChangeEvent(p.getUniqueId(), afk));
        if (logTransitions) {
            log().info(p.getName() + (afk ? " est passé AFK." : " n'est plus AFK."));
        }
        if (notifyPlayer) {
            p.sendMessage(plugin().configManager().prefixed(afk ? "antiafk-now-afk" : "antiafk-back"));
        }
    }

    // ---- API listener ----

    public ActivityTracker tracker() { return tracker; }

    /** Réinitialise l'inactivité du joueur (appelé par les events d'activité). */
    public void touch(UUID player) {
        boolean wasAfk = tracker.isAfk(player);
        tracker.record(player, System.currentTimeMillis());
        if (wasAfk) {
            // Repassage actif immédiat (sans attendre le prochain scan).
            tracker.evaluate(player, System.currentTimeMillis(), thresholdMs);
            Player p = Bukkit.getPlayer(player);
            if (p != null) onTransition(p, false);
        }
    }

    public boolean rotationCounts() { return rotationCounts; }

    // ---- AntiAfkService ----

    @Override
    public boolean isAfk(UUID player) {
        return tracker.isAfk(player);
    }

    @Override
    public long idleMillis(UUID player) {
        return tracker.idleMillis(player, System.currentTimeMillis());
    }

    @Override
    public double gainMultiplier(UUID player) {
        return tracker.isAfk(player) ? gainMultiplierWhenAfk : 1.0;
    }
}
