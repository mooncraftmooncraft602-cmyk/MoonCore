package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.stats.StatisticsModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** {@code /moon stats [joueur]} — affiche les statistiques d'un joueur. */
public final class StatsSubCommand implements SubCommand {

    private static final List<String> SHOWN = List.of(
            StatKeys.BLOCKS_MINED, StatKeys.BLOCKS_PLACED, StatKeys.MOB_KILLS,
            StatKeys.PLAYER_KILLS, StatKeys.DEATHS, StatKeys.BOSS_KILLS,
            StatKeys.MISSIONS_COMPLETED);

    private final StatisticsModule module;

    public StatsSubCommand(StatisticsModule module) {
        this.module = module;
    }

    @Override public String name() { return "stats"; }
    @Override public String permission() { return "mooncore.stats.view"; }
    @Override public String description() { return "Affiche tes statistiques"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();

        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(cm.prefixed("stats-usage-console"));
                return;
            }
            target = p;
        } else {
            if (!sender.hasPermission("mooncore.admin.statistics")) {
                sender.sendMessage(cm.prefixed("no-permission"));
                return;
            }
            Player onlineTarget = Bukkit.getPlayerExact(args[0]);
            target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                sender.sendMessage(cm.prefixed("stats-unknown", "player", args[0]));
                return;
            }
        }

        final String name = target.getName() != null ? target.getName() : args.length > 0 ? args[0] : "?";
        module.loadAsync(target.getUniqueId()).thenAccept(stats ->
                plugin.schedulers().sync(() -> render(plugin, sender, name, stats)));
    }

    private void render(MoonCore plugin, CommandSender sender, String name, Map<String, Long> stats) {
        var cm = plugin.configManager();
        sender.sendMessage(cm.message("stats-header", "player", name));
        for (String key : SHOWN) {
            sender.sendMessage(cm.message("stats-entry",
                    "key", key, "value", String.valueOf(stats.getOrDefault(key, 0L))));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("mooncore.admin.statistics")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
