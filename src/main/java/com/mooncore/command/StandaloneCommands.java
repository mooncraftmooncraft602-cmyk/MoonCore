package com.mooncore.command;

import com.mooncore.MoonCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rend chaque {@link SubCommand} de {@code /moon} utilisable AUSSI en commande autonome
 * (ex. {@code /ec}, {@code /heal}, {@code /warp}, {@code /msg}…) — plus besoin de préfixer
 * par {@code /moon}. Enregistre dynamiquement les commandes dans le {@link CommandMap} du
 * serveur après l'activation des modules. {@code /moon <sous-commande>} continue de fonctionner.
 *
 * <p>En cas de collision de nom avec un autre plugin encore installé (Essentials, EnderChest…),
 * Bukkit conserve le nom existant et la version MoonCore reste joignable via {@code /mooncore:<nom>}.
 */
public final class StandaloneCommands {

    private StandaloneCommands() {}

    /** Enregistre toutes les sous-commandes comme commandes autonomes. Renvoie le nombre traité. */
    public static int registerAll(MoonCore plugin, MoonCommand root) {
        CommandMap map = plugin.getServer().getCommandMap();
        int n = 0;
        for (SubCommand sub : root.subCommands()) {
            map.register("mooncore", build(plugin, sub));
            n++;
        }
        return n;
    }

    private static Command build(MoonCore plugin, SubCommand sub) {
        String desc = sub.description() == null ? "" : sub.description();
        Command cmd = new Command(sub.name(), desc, "/" + sub.name(), new ArrayList<>(sub.aliases())) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) {
                    sender.sendMessage(plugin.configManager().prefixed("no-permission"));
                    return true;
                }
                if (sub.playerOnly() && !(sender instanceof Player)) {
                    sender.sendMessage(plugin.configManager().prefixed("players-only"));
                    return true;
                }
                sub.execute(plugin, sender, args);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (sub.permission() != null && !sender.hasPermission(sub.permission())) return List.of();
                return sub.tabComplete(plugin, sender, args);
            }
        };
        if (sub.permission() != null) cmd.setPermission(sub.permission());
        return cmd;
    }
}
