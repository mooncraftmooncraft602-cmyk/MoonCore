package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.antiafk.AntiAfkModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon antiafk <list|check>} — diagnostic AntiAFK. */
public final class AntiAfkSubCommand implements SubCommand {

    private final AntiAfkModule module;

    public AntiAfkSubCommand(AntiAfkModule module) {
        this.module = module;
    }

    @Override public String name() { return "antiafk"; }
    @Override public String permission() { return "mooncore.admin.debug"; }
    @Override public String description() { return "Diagnostic AntiAFK"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                sender.sendMessage(cm.message("antiafk-list-header"));
                boolean any = false;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (module.isAfk(p.getUniqueId())) {
                        any = true;
                        sender.sendMessage(cm.message("antiafk-list-entry",
                                "player", p.getName(),
                                "idle", String.valueOf(module.idleMillis(p.getUniqueId()) / 1000)));
                    }
                }
                if (!any) sender.sendMessage(cm.message("antiafk-list-empty"));
            }
            case "check" -> {
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("antiafk-check-usage"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(cm.prefixed("antiafk-check-offline", "player", args[1]));
                    return;
                }
                sender.sendMessage(cm.message("antiafk-check",
                        "player", target.getName(),
                        "afk", module.isAfk(target.getUniqueId()) ? "oui" : "non",
                        "idle", String.valueOf(module.idleMillis(target.getUniqueId()) / 1000),
                        "mult", String.format(Locale.ROOT, "%.2f", module.gainMultiplier(target.getUniqueId()))));
            }
            default -> sender.sendMessage(cm.prefixed("antiafk-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("list", "check")) {
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
