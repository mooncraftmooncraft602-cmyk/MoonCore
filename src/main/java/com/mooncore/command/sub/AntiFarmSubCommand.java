package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.antifarm.AntiFarmModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon antifarm <info|check>} — diagnostic AntiFarm. */
public final class AntiFarmSubCommand implements SubCommand {

    private final AntiFarmModule module;

    public AntiFarmSubCommand(AntiFarmModule module) {
        this.module = module;
    }

    @Override public String name() { return "antifarm"; }
    @Override public String permission() { return "mooncore.admin.antifarm"; }
    @Override public String description() { return "Diagnostic AntiFarm"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "info" -> {
                sender.sendMessage(cm.message("antifarm-info-header"));
                sender.sendMessage(cm.message("antifarm-info-limits",
                        "chunk", String.valueOf(module.maxPerChunk()),
                        "player", String.valueOf(module.maxPerPlayer()),
                        "team", String.valueOf(module.maxPerTeam()),
                        "entities", String.valueOf(module.entityMaxPerChunk())));
                sender.sendMessage(cm.message("antifarm-info-total",
                        "total", String.valueOf(module.registry().total())));
            }
            case "check" -> {
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("antifarm-check-usage"));
                    return;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (target == null) {
                    sender.sendMessage(cm.prefixed("antifarm-check-unknown", "player", args[1]));
                    return;
                }
                long now = System.currentTimeMillis();
                sender.sendMessage(cm.message("antifarm-check",
                        "player", args[1],
                        "spawners", String.valueOf(module.registry().ownerCount(target.getUniqueId())),
                        "kills", String.valueOf(module.yieldLimiter().recentKills(target.getUniqueId(), now)),
                        "factor", String.format(Locale.ROOT, "%.2f",
                                module.yieldLimiter().factor(target.getUniqueId(), now))));
            }
            default -> sender.sendMessage(cm.prefixed("antifarm-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("info", "check")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
            return names;
        }
        return List.of();
    }
}
