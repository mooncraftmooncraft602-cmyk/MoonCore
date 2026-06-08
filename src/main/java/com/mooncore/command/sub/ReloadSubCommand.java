package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** {@code /moon reload [module]} — recharge la config globale ou d'un module. */
public final class ReloadSubCommand implements SubCommand {

    @Override public String name() { return "reload"; }
    @Override public String permission() { return "mooncore.admin.reload"; }
    @Override public String description() { return "Recharge la configuration"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length == 0) {
            cm.reloadAll();
            plugin.logger().setDebug(plugin.getConfig().getBoolean("core.debug", false));
            plugin.moduleManager().reload(null);
            sender.sendMessage(cm.prefixed("reload-success", "target", "tout"));
            return;
        }

        String module = args[0].toLowerCase(java.util.Locale.ROOT);
        cm.reloadModuleConfig(module);
        boolean ok = plugin.moduleManager().reload(module);
        if (ok) {
            sender.sendMessage(cm.prefixed("reload-success", "target", module));
        } else {
            sender.sendMessage(cm.prefixed("reload-module-not-found", "module", module));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            plugin.moduleManager().all().forEach(m -> {
                if (m.id().startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) out.add(m.id());
            });
            return out;
        }
        return List.of();
    }
}
