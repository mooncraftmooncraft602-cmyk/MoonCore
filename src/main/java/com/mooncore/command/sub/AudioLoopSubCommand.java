package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.audio.AudioManagerModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon loop <player|all|stop|info>} — boucles musicales forcées.
 * <ul>
 *   <li>{@code /moon loop player <joueur> <track>}</li>
 *   <li>{@code /moon loop all <track>}</li>
 *   <li>{@code /moon loop stop player <joueur>} / {@code /moon loop stop all}</li>
 *   <li>{@code /moon loop info <joueur>}</li>
 * </ul>
 */
public final class AudioLoopSubCommand implements SubCommand {

    private final AudioManagerModule module;

    public AudioLoopSubCommand(AudioManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "loop"; }
    @Override public String permission() { return "mooncore.admin.audio"; }
    @Override public String description() { return "Boucles musicales forcées"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "all" -> {
                if (args.length < 2) { sender.sendMessage(cm.prefixed("loop-all-usage")); return; }
                if (!module.tracks().exists(args[1])) { sender.sendMessage(cm.prefixed("audio-track-unknown", "track", args[1])); return; }
                module.loops().setGlobal(args[1]);
                module.applyAll();
                sender.sendMessage(cm.prefixed("loop-all-set", "track", args[1]));
            }
            case "player" -> {
                if (args.length < 3) { sender.sendMessage(cm.prefixed("loop-player-usage")); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(cm.prefixed("loop-offline", "player", args[1])); return; }
                if (!module.tracks().exists(args[2])) { sender.sendMessage(cm.prefixed("audio-track-unknown", "track", args[2])); return; }
                module.loops().setPlayer(target.getUniqueId(), args[2]);
                module.applyPlayer(target);
                sender.sendMessage(cm.prefixed("loop-player-set", "player", target.getName(), "track", args[2]));
            }
            case "stop" -> {
                String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (what.equals("all")) {
                    module.loops().clearGlobal();
                    module.applyAll();
                    sender.sendMessage(cm.prefixed("loop-stop-all"));
                } else if (what.equals("player")) {
                    if (args.length < 3) { sender.sendMessage(cm.prefixed("loop-stop-player-usage")); return; }
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) { sender.sendMessage(cm.prefixed("loop-offline", "player", args[2])); return; }
                    module.loops().clearPlayer(target.getUniqueId());
                    module.applyPlayer(target);
                    sender.sendMessage(cm.prefixed("loop-stop-player", "player", target.getName()));
                } else {
                    sender.sendMessage(cm.prefixed("loop-stop-usage"));
                }
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(cm.prefixed("loop-info-usage")); return; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(cm.prefixed("loop-offline", "player", args[1])); return; }
                var resolved = module.resolve(target);
                sender.sendMessage(cm.message("loop-info",
                        "player", target.getName(),
                        "global", module.loops().global() == null ? "—" : module.loops().global(),
                        "playerloop", module.loops().player(target.getUniqueId()) == null ? "—" : module.loops().player(target.getUniqueId()),
                        "current", resolved == null ? "—" : resolved.trackId() + " (" + resolved.source() + ")"));
            }
            default -> sender.sendMessage(cm.prefixed("loop-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("player", "all", "stop", "info"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("info"))) {
            return onlineNames(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) return trackIds(args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) return filter(List.of("all", "player"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("player")) return trackIds(args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("stop") && args[1].equalsIgnoreCase("player")) return onlineNames(args[2]);
        return List.of();
    }

    private List<String> onlineNames(String prefix) {
        List<String> out = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        return filter(out, prefix);
    }

    private List<String> trackIds(String prefix) {
        List<String> ids = new ArrayList<>();
        module.tracks().all().forEach(t -> ids.add(t.id()));
        return filter(ids, prefix);
    }

    private List<String> filter(List<String> options, String prefix) {
        String pfx = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(o);
        return out;
    }
}
