package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.reward.RewardManagerModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon reward <list|give>} — gestion des récompenses. */
public final class RewardSubCommand implements SubCommand {

    private final RewardManagerModule module;

    public RewardSubCommand(RewardManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "reward"; }
    @Override public List<String> aliases() { return List.of("rewards"); }
    @Override public String permission() { return "mooncore.admin.rewards"; }
    @Override public String description() { return "Gestion des récompenses"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                sender.sendMessage(cm.message("reward-list-header"));
                if (module.rewardIds().isEmpty()) {
                    sender.sendMessage(cm.message("reward-list-empty"));
                    return;
                }
                module.rewardIds().forEach(id -> sender.sendMessage(cm.message("reward-list-entry", "id", id)));
            }
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(cm.prefixed("reward-give-usage"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(cm.prefixed("reward-give-offline", "player", args[1]));
                    return;
                }
                if (module.give(target, args[2])) {
                    sender.sendMessage(cm.prefixed("reward-given", "id", args[2], "player", target.getName()));
                } else {
                    sender.sendMessage(cm.prefixed("reward-unknown", "id", args[2]));
                }
            }
            default -> sender.sendMessage(cm.prefixed("reward-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("list", "give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(new ArrayList<>(module.rewardIds()), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
}
