package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.MoonModule;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/** {@code /moon modules} — état de chargement de chaque module. */
public final class ModulesSubCommand implements SubCommand {

    @Override public String name() { return "modules"; }
    @Override public String permission() { return "mooncore.admin.debug"; }
    @Override public String description() { return "Liste l'état des modules"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        sender.sendMessage(cm.message("modules-header"));
        for (MoonModule m : plugin.moduleManager().all()) {
            Component state = switch (m.state()) {
                case ENABLED -> cm.message("modules-state-enabled");
                case DISABLED_BY_CONFIG -> cm.message("modules-state-disabled");
                case FAILED -> cm.message("modules-state-failed");
                default -> cm.message("modules-state-other", "raw", m.state().name());
            };
            sender.sendMessage(cm.message("modules-entry", "id", m.id()).append(state));
        }
    }
}
