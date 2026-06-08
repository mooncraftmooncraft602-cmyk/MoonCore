package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.mission.Mission;
import com.mooncore.api.mission.MissionScope;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.missions.MissionModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon missions [daily|weekly|seasonal]} — voir ses missions.
 * {@code /moon missions claim <id>} — réclamer une récompense.
 */
public final class MissionsSubCommand implements SubCommand {

    private final MissionModule module;

    public MissionsSubCommand(MissionModule module) {
        this.module = module;
    }

    @Override public String name() { return "missions"; }
    @Override public List<String> aliases() { return List.of("mission", "quetes"); }
    @Override public String permission() { return "mooncore.missions.view"; }
    @Override public String description() { return "Affiche tes missions"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        Player p = (Player) sender;

        if (args.length >= 2 && args[0].equalsIgnoreCase("claim")) {
            String id = args[1];
            module.claim(p, id).thenAccept(ok -> plugin.schedulers().sync(() ->
                    p.sendMessage(ok ? cm.prefixed("missions-claimed", "id", id)
                            : cm.prefixed("missions-claim-failed", "id", id))));
            return;
        }

        MissionScope scope = MissionScope.DAILY;
        if (args.length >= 1) {
            try {
                scope = MissionScope.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        sender.sendMessage(cm.message("missions-header", "scope", scope.name().toLowerCase(Locale.ROOT)));
        List<Mission> missions = module.missions(scope);
        if (missions.isEmpty()) {
            sender.sendMessage(cm.message("missions-empty"));
            return;
        }
        for (Mission m : missions) {
            int prog = module.progress(p.getUniqueId(), m.id());
            String state = module.isClaimed(p.getUniqueId(), m.id()) ? "<green>réclamée</green>"
                    : prog >= m.target() ? "<gold>à réclamer</gold>"
                    : "<gray>" + prog + "/" + m.target() + "</gray>";
            sender.sendMessage(cm.message("missions-entry",
                    "id", m.id(), "desc", m.description(), "state", state));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("daily", "weekly", "seasonal", "claim"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim") && sender instanceof Player p) {
            List<String> ids = new ArrayList<>();
            for (MissionScope s : MissionScope.values()) {
                module.missions(s).forEach(m -> {
                    if (module.isComplete(p.getUniqueId(), m.id()) && !module.isClaimed(p.getUniqueId(), m.id())) {
                        ids.add(m.id());
                    }
                });
            }
            return filter(ids, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String pfx = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(o);
        return out;
    }
}
