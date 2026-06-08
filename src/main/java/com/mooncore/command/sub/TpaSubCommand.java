package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.home.HomeManagerModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /moon tpa <joueur>} (demande) · {@code /moon tpa accept|deny}.
 * À l'acceptation, le demandeur est téléporté vers la cible.
 */
public final class TpaSubCommand implements SubCommand {

    private final HomeManagerModule module;

    public TpaSubCommand(HomeManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "tpa"; }
    @Override public String permission() { return "mooncore.tpa.use"; }
    @Override public String description() { return "Demande de téléportation vers un joueur"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        Player p = (Player) sender;
        if (args.length == 0) { p.sendMessage(cm.prefixed("tpa-usage")); return; }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("accept")) {
            Player requester = module.acceptTpa(p);
            if (requester != null) {
                p.sendMessage(cm.prefixed("tpa-accepted", "player", requester.getName()));
                requester.sendMessage(cm.prefixed("tpa-accepted-by", "player", p.getName()));
            } else {
                p.sendMessage(cm.prefixed("tpa-none"));
            }
            return;
        }
        if (first.equals("deny") || first.equals("refuse")) {
            p.sendMessage(module.denyTpa(p) ? cm.prefixed("tpa-denied") : cm.prefixed("tpa-none"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) {
            p.sendMessage(cm.prefixed("tpa-target-invalid"));
            return;
        }
        if (module.requestTpa(p, target)) {
            p.sendMessage(cm.prefixed("tpa-sent", "player", target.getName()));
            target.sendMessage(cm.prefixed("tpa-received", "player", p.getName()));
        } else {
            p.sendMessage(cm.prefixed("tpa-zone-denied"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new java.util.ArrayList<>(List.of("accept", "deny"));
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
            String pfx = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(pfx));
            return out;
        }
        return List.of();
    }
}
