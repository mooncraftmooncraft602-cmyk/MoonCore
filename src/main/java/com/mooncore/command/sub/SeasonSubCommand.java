package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.season.SeasonInfo;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.season.SeasonManagerModule;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * {@code /moon season info|list} — infos de saison.
 * {@code /moon season set <id>} — bascule de saison (admin).
 */
public final class SeasonSubCommand implements SubCommand {

    private final SeasonManagerModule module;

    public SeasonSubCommand(SeasonManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "season"; }
    @Override public List<String> aliases() { return List.of("seasons", "saison"); }
    @Override public String permission() { return "mooncore.help"; }
    @Override public String description() { return "Infos et gestion de saison"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "info" -> {
                SeasonInfo s = module.current();
                long remaining = module.daysRemaining();
                sender.sendMessage(cm.message("season-info",
                        "id", s != null ? s.seasonId() : "?",
                        "remaining", remaining < 0 ? "∞" : String.valueOf(remaining)));
            }
            case "list" -> {
                sender.sendMessage(cm.message("season-list-header"));
                try {
                    for (SeasonInfo s : module.all()) {
                        sender.sendMessage(cm.message("season-list-entry",
                                "id", s.seasonId(),
                                "state", s.active() ? "<green>active</green>" : "<gray>archivée</gray>"));
                    }
                } catch (Exception e) {
                    sender.sendMessage(cm.prefixed("season-error"));
                }
            }
            case "set" -> {
                if (!sender.hasPermission("mooncore.admin.seasons")) {
                    sender.sendMessage(cm.prefixed("no-permission"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("season-set-usage"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                try {
                    module.switchTo(id);
                    sender.sendMessage(cm.prefixed("season-set-ok", "id", id));
                } catch (Exception e) {
                    sender.sendMessage(cm.prefixed("season-error"));
                }
            }
            default -> sender.sendMessage(cm.prefixed("season-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> base = sender.hasPermission("mooncore.admin.seasons")
                    ? List.of("info", "list", "set") : List.of("info", "list");
            List<String> out = new java.util.ArrayList<>();
            for (String s : base) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        return List.of();
    }
}
