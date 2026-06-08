package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.home.HomeManagerModule;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** {@code /moon spawn} (tp) · {@code /moon spawn set} (admin). */
public final class SpawnSubCommand implements SubCommand {

    private final HomeManagerModule module;

    public SpawnSubCommand(HomeManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "spawn"; }
    @Override public String permission() { return "mooncore.spawn.use"; }
    @Override public String description() { return "Rejoint le spawn (set: admin)"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        Player p = (Player) sender;

        if (args.length >= 1 && args[0].equalsIgnoreCase("set")) {
            if (!p.hasPermission("mooncore.admin.spawn")) {
                p.sendMessage(cm.prefixed("no-permission"));
                return;
            }
            module.setSpawn(p.getLocation());
            p.sendMessage(cm.prefixed("spawn-set"));
            return;
        }

        Location spawn = module.getSpawn();
        if (spawn == null) {
            p.sendMessage(cm.prefixed("spawn-not-set"));
            return;
        }
        p.teleport(spawn); // frais EconomyBalancer éventuels (cause PLUGIN)
        p.sendMessage(cm.prefixed("spawn-teleported"));
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("mooncore.admin.spawn")
                && "set".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("set");
        }
        return List.of();
    }
}
