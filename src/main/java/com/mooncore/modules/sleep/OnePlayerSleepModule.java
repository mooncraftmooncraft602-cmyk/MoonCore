package com.mooncore.modules.sleep;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Remplace <b>OnePlayerSleep / BetterSleep</b> : règle le gamerule
 * {@code playersSleepingPercentage} (défaut 1 %) sur tous les mondes → un seul joueur
 * suffit à passer la nuit. Annonce le saut de nuit ({@link TimeSkipEvent}).
 */
@ModuleInfo(id = "one-player-sleep", name = "OnePlayerSleep")
public final class OnePlayerSleepModule extends AbstractModule implements Listener {

    private int percentage;
    private boolean announce;

    @Override
    protected void onEnable() {
        load();
        applyToAllWorlds();
        registerListener(this);
    }

    @Override protected void onDisable() { }
    @Override protected void onReload() { reloadModuleConfig(); load(); applyToAllWorlds(); }

    private void load() {
        percentage = Math.max(0, Math.min(100, moduleConfig().getInt("sleeping-percentage", 1)));
        announce = moduleConfig().getBoolean("announce-skip", true);
    }

    private void applyToAllWorlds() {
        for (World w : plugin().getServer().getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, percentage);
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (e.getWorld().getEnvironment() == World.Environment.NORMAL) {
            e.getWorld().setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, percentage);
        }
    }

    @EventHandler
    public void onTimeSkip(TimeSkipEvent e) {
        if (announce && e.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            e.getWorld().getPlayers().forEach(p ->
                    p.sendMessage(Text.mm("<aqua>☾ <gray>La nuit a été passée — bonne journée !")));
        }
    }
}
