package com.mooncore.modules.clearlag;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Remplace <b>ClearLag</b> : supprime périodiquement les objets au sol (et éventuellement les
 * flèches au sol) avec un compte à rebours annoncé. {@code /moon clearlag} déclenche un nettoyage
 * immédiat. Config {@code modules/clearlag.yml} (interval-seconds, warn-seconds, clear-arrows).
 */
@ModuleInfo(id = "clearlag", name = "ClearLag")
public final class ClearLagModule extends AbstractModule implements SubCommand {

    private int interval;
    private boolean clearArrows;
    private List<Integer> warnAt;
    private BukkitTask timer;
    private int countdown;

    @Override
    protected void onEnable() {
        load();
        plugin().rootCommand().register(this);
        countdown = interval;
        timer = schedulers().syncTimer(this::tickSecond, 20L, 20L); // chaque seconde
    }

    @Override protected void onDisable() { if (timer != null) timer.cancel(); }
    @Override protected void onReload() { reloadModuleConfig(); load(); countdown = interval; }

    private void load() {
        interval = Math.max(30, moduleConfig().getInt("interval-seconds", 600));
        clearArrows = moduleConfig().getBoolean("clear-arrows", true);
        warnAt = moduleConfig().getIntegerList("warn-seconds");
        if (warnAt.isEmpty()) warnAt = List.of(60, 30, 10, 5, 3, 2, 1);
    }

    private void tickSecond() {
        countdown--;
        if (warnAt.contains(countdown)) {
            broadcast(Text.mm("<gold>⚠ Nettoyage des objets au sol dans <white>" + countdown + "s</white>."));
        }
        if (countdown <= 0) {
            int removed = clearNow();
            broadcast(Text.mm("<green>🧹 " + removed + " objet(s) au sol supprimé(s) (anti-lag)."));
            countdown = interval;
        }
    }

    /** Supprime les objets (et flèches) au sol de tous les mondes. Renvoie le nombre retiré. */
    public int clearNow() {
        int removed = 0;
        for (World w : plugin().getServer().getWorlds()) {
            for (Item it : w.getEntitiesByClass(Item.class)) { it.remove(); removed++; }
            if (clearArrows) {
                for (Arrow a : w.getEntitiesByClass(Arrow.class)) {
                    if (a.isOnGround()) { a.remove(); removed++; }
                }
            }
        }
        return removed;
    }

    private void broadcast(net.kyori.adventure.text.Component c) {
        plugin().getServer().getOnlinePlayers().forEach(p -> p.sendMessage(c));
    }

    @Override public String name() { return "clearlag"; }
    @Override public String permission() { return "mooncore.admin.clearlag"; }
    @Override public String description() { return "(admin) supprime les objets au sol maintenant"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        int removed = clearNow();
        countdown = interval;
        sender.sendMessage(Text.mm("<green>🧹 Nettoyage immédiat : " + removed + " objet(s) au sol supprimé(s)."));
    }
}
