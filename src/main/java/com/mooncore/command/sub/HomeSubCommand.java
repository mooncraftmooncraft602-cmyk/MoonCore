package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.home.HomeManagerModule;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon home} (liste) · {@code /moon home <nom>} (tp) ·
 * {@code /moon home set|del <nom>}.
 */
public final class HomeSubCommand implements SubCommand {

    private final HomeManagerModule module;

    public HomeSubCommand(HomeManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "home"; }
    @Override public List<String> aliases() { return List.of("homes"); }
    @Override public String permission() { return "mooncore.home.use"; }
    @Override public String description() { return "Gère et rejoint tes homes"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        Player p = (Player) sender;

        if (args.length == 0) {
            var homes = module.homesOf(p.getUniqueId()).keySet();
            sender.sendMessage(cm.message("home-list",
                    "count", String.valueOf(homes.size()),
                    "max", String.valueOf(module.maxHomes()),
                    "homes", homes.isEmpty() ? "—" : String.join(", ", homes)));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (args.length < 2) { p.sendMessage(cm.prefixed("home-set-usage")); return; }
                switch (module.setHome(p, args[1])) {
                    case OK -> p.sendMessage(cm.prefixed("home-set-ok", "name", args[1].toLowerCase(Locale.ROOT)));
                    case INVALID_NAME -> p.sendMessage(cm.prefixed("home-invalid-name"));
                    case LIMIT_REACHED -> p.sendMessage(cm.prefixed("home-limit", "max", String.valueOf(module.maxHomes())));
                }
            }
            case "del", "delete", "remove" -> {
                if (args.length < 2) { p.sendMessage(cm.prefixed("home-del-usage")); return; }
                p.sendMessage(module.delHome(p.getUniqueId(), args[1])
                        ? cm.prefixed("home-deleted", "name", args[1].toLowerCase(Locale.ROOT))
                        : cm.prefixed("home-unknown"));
            }
            default -> p.sendMessage(cm.prefixed(module.teleportHome(p, args[0])));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("set", "del"));
            out.addAll(module.homesOf(p.getUniqueId()).keySet());
            String pfx = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(pfx));
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("del")) {
            return new ArrayList<>(module.homesOf(p.getUniqueId()).keySet());
        }
        return List.of();
    }
}
