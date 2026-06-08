package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.team.Team;
import com.mooncore.modules.team.TeamManagerModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /moon team <create|invite|accept|leave|kick|disband|info>} — gestion des équipes. */
public final class TeamSubCommand implements SubCommand {

    private final TeamManagerModule module;

    public TeamSubCommand(TeamManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "team"; }
    @Override public List<String> aliases() { return List.of("teams", "equipe"); }
    @Override public String permission() { return "mooncore.team.use"; }
    @Override public String description() { return "Gestion de ton équipe"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        Player p = (Player) sender;
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        switch (sub) {
            case "create" -> {
                if (args.length < 2) { p.sendMessage(cm.prefixed("team-create-usage")); return; }
                switch (module.create(p.getUniqueId(), args[1])) {
                    case OK -> p.sendMessage(cm.prefixed("team-created", "name", args[1]));
                    case ALREADY_IN_TEAM -> p.sendMessage(cm.prefixed("team-already-in"));
                    case INVALID_NAME -> p.sendMessage(cm.prefixed("team-invalid-name"));
                    case NAME_TAKEN -> p.sendMessage(cm.prefixed("team-name-taken", "name", args[1]));
                }
            }
            case "invite" -> {
                if (args.length < 2) { p.sendMessage(cm.prefixed("team-invite-usage")); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { p.sendMessage(cm.prefixed("team-player-offline", "player", args[1])); return; }
                if (module.invite(p.getUniqueId(), target.getUniqueId())) {
                    p.sendMessage(cm.prefixed("team-invited", "player", target.getName()));
                    target.sendMessage(cm.prefixed("team-invite-received", "player", p.getName()));
                } else {
                    p.sendMessage(cm.prefixed("team-invite-failed"));
                }
            }
            case "accept" -> {
                Team t = module.accept(p.getUniqueId());
                p.sendMessage(t != null ? cm.prefixed("team-joined", "name", t.name())
                        : cm.prefixed("team-no-invite"));
            }
            case "leave" -> p.sendMessage(module.leave(p.getUniqueId())
                    ? cm.prefixed("team-left") : cm.prefixed("team-not-in"));
            case "disband" -> p.sendMessage(module.disband(p.getUniqueId())
                    ? cm.prefixed("team-disbanded") : cm.prefixed("team-not-owner"));
            case "kick" -> {
                if (args.length < 2) { p.sendMessage(cm.prefixed("team-kick-usage")); return; }
                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
                UUID targetId = target != null ? target.getUniqueId()
                        : (Bukkit.getPlayerExact(args[1]) != null ? Bukkit.getPlayerExact(args[1]).getUniqueId() : null);
                if (targetId == null) { p.sendMessage(cm.prefixed("team-player-offline", "player", args[1])); return; }
                p.sendMessage(module.kick(p.getUniqueId(), targetId)
                        ? cm.prefixed("team-kicked", "player", args[1]) : cm.prefixed("team-kick-failed"));
            }
            case "info" -> {
                Team t = module.team(p.getUniqueId());
                if (t == null) { p.sendMessage(cm.prefixed("team-not-in")); return; }
                p.sendMessage(cm.message("team-info-header", "name", t.name()));
                p.sendMessage(cm.message("team-info-members",
                        "count", String.valueOf(t.size()), "max", String.valueOf(module.maxMembers())));
            }
            default -> p.sendMessage(cm.prefixed("team-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : List.of("create", "invite", "accept", "leave", "kick", "disband", "info")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
