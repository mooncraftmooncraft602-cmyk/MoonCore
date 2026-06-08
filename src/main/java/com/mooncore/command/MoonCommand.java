package com.mooncore.command;

import com.mooncore.MoonCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Point d'entrée unique {@code /moon}. Dispatche vers les {@link SubCommand} enregistrées,
 * gère permissions, joueur-only et auto-complétion. Les modules enregistrent leurs
 * sous-commandes via {@link #register(SubCommand)}.
 */
public final class MoonCommand implements TabExecutor {

    private final MoonCore plugin;
    private final Map<String, SubCommand> byName = new LinkedHashMap<>();
    private final Map<String, SubCommand> byAlias = new LinkedHashMap<>();

    public MoonCommand(MoonCore plugin) {
        this.plugin = plugin;
    }

    public void register(SubCommand sub) {
        byName.put(sub.name().toLowerCase(Locale.ROOT), sub);
        for (String alias : sub.aliases()) {
            byAlias.put(alias.toLowerCase(Locale.ROOT), sub);
        }
    }

    public java.util.Collection<SubCommand> subCommands() {
        return byName.values();
    }

    private SubCommand find(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        SubCommand sub = byName.get(key);
        return sub != null ? sub : byAlias.get(key);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            find("help").execute(plugin, sender, args);
            return true;
        }

        SubCommand sub = find(args[0]);
        if (sub == null) {
            sender.sendMessage(plugin.configManager().prefixed("unknown-subcommand", "arg", args[0]));
            return true;
        }

        if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
            sender.sendMessage(plugin.configManager().prefixed("no-permission"));
            return true;
        }

        if (sub.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(plugin.configManager().prefixed("players-only"));
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        sub.execute(plugin, sender, subArgs);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (SubCommand sub : byName.values()) {
                if (!sub.name().startsWith(prefix)) continue;
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) continue;
                out.add(sub.name());
            }
            return out;
        }

        SubCommand sub = find(args[0]);
        if (sub == null) return List.of();
        if (sub.permission() != null && !sender.hasPermission(sub.permission())) return List.of();
        return sub.tabComplete(plugin, sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
