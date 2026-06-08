package com.mooncore.util;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Point d'entrée centralisé du scheduling, pour pouvoir basculer vers les schedulers
 * régionalisés de Folia ultérieurement sans toucher au code des modules.
 * <p>
 * Implémentation actuelle : BukkitScheduler (Paper/Purpur). La détection Folia est
 * exposée via {@link #isFolia()} pour adaptation future.
 */
public final class Schedulers {

    private final Plugin plugin;
    private final boolean folia;

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
        this.folia = detectFolia();
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public boolean isFolia() { return folia; }

    /** Exécute sur le thread principal. */
    public BukkitTask sync(Runnable task) {
        return plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /** Exécute sur le thread principal après {@code delayTicks}. */
    public BukkitTask syncLater(Runnable task, long delayTicks) {
        return plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /** Tâche répétée sur le thread principal. */
    public BukkitTask syncTimer(Runnable task, long delayTicks, long periodTicks) {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    /** Exécute hors du thread principal (jamais d'API monde dedans). */
    public BukkitTask async(Runnable task) {
        return plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    /** Tâche répétée asynchrone. */
    public BukkitTask asyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    /** Convertit des secondes en ticks (20 ticks/s). */
    public static long secondsToTicks(long seconds) {
        return seconds * 20L;
    }
}
