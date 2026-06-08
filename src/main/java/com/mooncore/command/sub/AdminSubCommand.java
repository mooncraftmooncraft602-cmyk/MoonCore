package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.afk.AntiAfkService;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.antifarm.AntiFarmModule;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.event.EventManagerModule;
import com.mooncore.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /moon admin <inspect|debug|info>} — outils d'administration transverses : agrège
 * les infos des différents modules pour un diagnostic rapide.
 */
public final class AdminSubCommand implements SubCommand {

    @Override public String name() { return "admin"; }
    @Override public String permission() { return "mooncore.admin.debug"; }
    @Override public String description() { return "Outils d'administration (inspect/debug)"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "inspect" -> inspect(plugin, sender, args);
            case "debug" -> debug(plugin, sender);
            case "info" -> sender.sendMessage(cm.prefixed("version",
                    "version", plugin.getPluginMeta().getVersion(),
                    "season", plugin.getConfig().getString("core.season-id", "?"),
                    "modules", String.valueOf(plugin.moduleManager().enableOrder().size())));
            default -> sender.sendMessage(cm.prefixed("admin-usage"));
        }
    }

    private void inspect(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length < 2) {
            sender.sendMessage(cm.prefixed("admin-inspect-usage"));
            return;
        }
        Player t = Bukkit.getPlayerExact(args[1]);
        if (t == null) {
            sender.sendMessage(cm.prefixed("admin-inspect-offline", "player", args[1]));
            return;
        }
        var id = t.getUniqueId();
        var s = plugin.services();

        sender.sendMessage(cm.message("admin-inspect-header", "player", t.getName()));

        s.get(ProgressionService.class).ifPresent(pr -> sender.sendMessage(cm.message("admin-inspect-prog",
                "tier", String.valueOf(pr.tier(id)), "xp", String.valueOf(pr.xp(id)))));

        s.get(EconomyService.class).ifPresent(eco -> sender.sendMessage(cm.message("admin-inspect-eco",
                "balance", String.format(Locale.ROOT, "%.2f", eco.balance(id)))));

        s.get(AntiAfkService.class).ifPresent(afk -> sender.sendMessage(cm.message("admin-inspect-afk",
                "afk", afk.isAfk(id) ? "oui" : "non",
                "idle", TimeFormat.shortDuration(afk.idleMillis(id) / 1000))));

        s.get(StatisticsService.class).ifPresent(st -> sender.sendMessage(cm.message("admin-inspect-stats",
                "kills", String.valueOf(st.get(id, StatKeys.MOB_KILLS)),
                "pvp", String.valueOf(st.get(id, StatKeys.PLAYER_KILLS)),
                "deaths", String.valueOf(st.get(id, StatKeys.DEATHS)),
                "bosses", String.valueOf(st.get(id, StatKeys.BOSS_KILLS)))));

        AntiFarmModule af = plugin.moduleManager().get(AntiFarmModule.class);
        if (af != null) {
            sender.sendMessage(cm.message("admin-inspect-spawners",
                    "count", String.valueOf(af.registry().ownerCount(id))));
        }
    }

    private void debug(MoonCore plugin, CommandSender sender) {
        var cm = plugin.configManager();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);

        long activeModules = plugin.moduleManager().all().stream()
                .filter(com.mooncore.core.module.MoonModule::isEnabled).count();

        BossManagerModule boss = plugin.moduleManager().get(BossManagerModule.class);
        EventManagerModule event = plugin.moduleManager().get(EventManagerModule.class);

        sender.sendMessage(cm.message("admin-debug-header"));
        sender.sendMessage(cm.message("admin-debug-modules",
                "active", String.valueOf(activeModules),
                "total", String.valueOf(plugin.moduleManager().all().size())));
        sender.sendMessage(cm.message("admin-debug-memory",
                "used", String.valueOf(usedMb), "max", String.valueOf(maxMb)));
        sender.sendMessage(cm.message("admin-debug-runtime",
                "bosses", String.valueOf(boss != null ? boss.activeCount() : 0),
                "events", String.valueOf(event != null ? event.activeIds().size() : 0),
                "players", String.valueOf(Bukkit.getOnlinePlayers().size())));
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new java.util.ArrayList<>();
            for (String s : List.of("inspect", "debug", "info")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
