package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.update.UpdateModule;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * {@code /moon plugins reinstall} — télécharge et applique la dernière version de MoonCore
 * depuis GitHub (sans relancer le serveur si le hot-reload est possible ; sinon appliqué
 * au prochain redémarrage). {@code /moon plugins check} = vérifie sans installer.
 */
public final class PluginsSubCommand implements SubCommand {

    private final UpdateModule module;

    public PluginsSubCommand(UpdateModule module) {
        this.module = module;
    }

    @Override public String name() { return "plugins"; }
    @Override public List<String> aliases() { return List.of("plugin"); }
    @Override public String permission() { return "mooncore.admin.plugins"; }
    @Override public String description() { return "(admin) reinstall|check MoonCore depuis GitHub"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "reinstall", "update" -> module.reinstall(s);
            case "check" -> { module.check(true); s.sendMessage(Text.mm("<gray>[Plugins] Vérification GitHub lancée (voir console).")); }
            default -> s.sendMessage(Text.mm("<gray>Usage : <white>/moon plugins reinstall</white> (maj depuis GitHub) <dark_gray>| <white>check</white>"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] args) {
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            return java.util.stream.Stream.of("reinstall", "check").filter(x -> x.startsWith(pre)).toList();
        }
        return List.of();
    }
}
