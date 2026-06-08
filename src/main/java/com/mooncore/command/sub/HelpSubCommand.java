package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.MoonCommand;
import com.mooncore.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/** {@code /moon help [player|admin]} — liste les sous-commandes accessibles. */
public final class HelpSubCommand implements SubCommand {

    private final MoonCommand root;

    public HelpSubCommand(MoonCommand root) {
        this.root = root;
    }

    @Override public String name() { return "help"; }
    @Override public List<String> aliases() { return List.of("?", "aide"); }
    @Override public String permission() { return "mooncore.help"; }
    @Override public String description() { return "Affiche l'aide"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String filter = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null;

        if ("player".equals(filter)) {
            sender.sendMessage(cm.message("help-player-header"));
            send(plugin, sender, "player");
        } else if ("admin".equals(filter)) {
            sender.sendMessage(cm.message("help-admin-header"));
            send(plugin, sender, "admin");
        } else {
            sender.sendMessage(cm.message("help-header"));
            send(plugin, sender, null);
            sender.sendMessage(cm.message("help-footer"));
        }
    }

    private void send(MoonCore plugin, CommandSender sender, String category) {
        var cm = plugin.configManager();
        for (SubCommand sub : root.subCommands()) {
            if (category != null && !category.equals(sub.category())) continue;
            if (sub.permission() != null && !sender.hasPermission(sub.permission())) continue;
            sender.sendMessage(cm.message("help-entry",
                    "command", sub.name(),
                    "description", sub.description()));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("player", "admin");
        return List.of();
    }
}
