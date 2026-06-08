package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.leaderboard.LeaderboardEntry;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.leaderboard.LeaderboardManagerModule;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon leaderboard [id]} — affiche un classement (ou la liste des classements). */
public final class LeaderboardSubCommand implements SubCommand {

    private final LeaderboardManagerModule module;

    public LeaderboardSubCommand(LeaderboardManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "leaderboard"; }
    @Override public List<String> aliases() { return List.of("lb", "top", "classement"); }
    @Override public String permission() { return "mooncore.leaderboard.view"; }
    @Override public String description() { return "Affiche les classements"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length == 0) {
            sender.sendMessage(cm.message("leaderboard-list-header"));
            if (module.boards().isEmpty()) {
                sender.sendMessage(cm.message("leaderboard-list-empty"));
                return;
            }
            module.boards().forEach(id -> sender.sendMessage(
                    cm.message("leaderboard-list-entry", "id", id, "title", module.title(id))));
            return;
        }

        String id = args[0].toLowerCase(Locale.ROOT);
        if (!module.boards().contains(id)) {
            sender.sendMessage(cm.prefixed("leaderboard-unknown", "id", id));
            return;
        }
        List<LeaderboardEntry> entries = module.top(id);
        sender.sendMessage(cm.message("leaderboard-header", "title", module.title(id)));
        if (entries.isEmpty()) {
            sender.sendMessage(cm.message("leaderboard-empty"));
            return;
        }
        for (LeaderboardEntry e : entries) {
            sender.sendMessage(cm.message("leaderboard-entry",
                    "rank", String.valueOf(e.rank()),
                    "player", e.name(),
                    "value", String.valueOf(e.value())));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String id : module.boards()) if (id.startsWith(pfx)) out.add(id);
            return out;
        }
        return List.of();
    }
}
