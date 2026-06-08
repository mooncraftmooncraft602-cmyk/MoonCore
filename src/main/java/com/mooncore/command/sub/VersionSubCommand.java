package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.MoonModule;
import org.bukkit.command.CommandSender;

/** {@code /moon version} — version, saison et nombre de modules actifs. */
public final class VersionSubCommand implements SubCommand {

    @Override public String name() { return "version"; }
    @Override public java.util.List<String> aliases() { return java.util.List.of("ver", "about"); }
    @Override public String permission() { return "mooncore.help"; }
    @Override public String description() { return "Version et infos du plugin"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        long active = plugin.moduleManager().all().stream().filter(MoonModule::isEnabled).count();
        sender.sendMessage(plugin.configManager().prefixed("version",
                "version", plugin.getPluginMeta().getVersion(),
                "season", plugin.getConfig().getString("core.season-id", "?"),
                "modules", String.valueOf(active)));
    }
}
