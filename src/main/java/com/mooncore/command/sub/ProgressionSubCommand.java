package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.progression.ProgressionModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon progression} — voir sa progression.
 * {@code /moon progression <addxp|settier> <joueur> <valeur>} — admin.
 */
public final class ProgressionSubCommand implements SubCommand {

    private final ProgressionModule module;

    public ProgressionSubCommand(ProgressionModule module) {
        this.module = module;
    }

    @Override public String name() { return "progression"; }
    @Override public List<String> aliases() { return List.of("prog", "tier"); }
    @Override public String permission() { return "mooncore.progression.view"; }
    @Override public String description() { return "Affiche ta progression"; }
    @Override public String category() { return "player"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(cm.prefixed("progression-usage-console"));
                return;
            }
            showSelf(plugin, p);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("addxp") || sub.equals("settier"))) {
            if (!sender.hasPermission("mooncore.admin.progression")) {
                sender.sendMessage(cm.prefixed("no-permission"));
                return;
            }
            if (args.length < 3) {
                sender.sendMessage(cm.prefixed("progression-admin-usage"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(cm.prefixed("progression-offline", "player", args[1]));
                return;
            }
            long value;
            try { value = Long.parseLong(args[2]); } catch (NumberFormatException e) {
                sender.sendMessage(cm.prefixed("progression-admin-usage"));
                return;
            }
            if (sub.equals("addxp")) {
                module.addXp(target.getUniqueId(), value, "admin");
                sender.sendMessage(cm.prefixed("progression-addxp", "xp", String.valueOf(value), "player", target.getName()));
            } else {
                module.setTier(target.getUniqueId(), (int) value);
                sender.sendMessage(cm.prefixed("progression-settier", "tier", String.valueOf(value), "player", target.getName()));
            }
            return;
        }

        sender.sendMessage(cm.prefixed("progression-usage"));
    }

    private void showSelf(MoonCore plugin, Player p) {
        var cm = plugin.configManager();
        var id = p.getUniqueId();
        int tier = module.tier(id);
        long xp = module.xp(id);
        long next = module.nextTierXp(id);
        p.sendMessage(cm.message("progression-header"));
        p.sendMessage(cm.message("progression-line",
                "tier", String.valueOf(tier),
                "max", String.valueOf(module.maxTier()),
                "xp", String.valueOf(xp),
                "next", next < 0 ? "MAX" : String.valueOf(next)));
        var unlocks = module.unlocks(id);
        p.sendMessage(cm.message("progression-unlocks",
                "unlocks", unlocks.isEmpty() ? "—" : String.join(", ", unlocks)));
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("mooncore.admin.progression")) {
            return filter(List.of("addxp", "settier"), args[0]);
        }
        if (args.length == 2 && sender.hasPermission("mooncore.admin.progression")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String pfx = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.startsWith(pfx)) out.add(o);
        return out;
    }
}
