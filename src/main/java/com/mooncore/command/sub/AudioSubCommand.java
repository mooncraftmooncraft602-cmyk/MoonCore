package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.zone.Region;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.audio.AudioManagerModule;
import com.mooncore.modules.zone.RegionSelection;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon audio <play|stop|volume|reload|debug|zone>} — contrôle audio admin. */
public final class AudioSubCommand implements SubCommand {

    private final AudioManagerModule module;

    public AudioSubCommand(AudioManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "audio"; }
    @Override public String permission() { return "mooncore.admin.audio"; }
    @Override public String description() { return "Contrôle audio (tracks, zones, volume)"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "debug";
        switch (sub) {
            case "play" -> {
                if (args.length < 3) { sender.sendMessage(cm.prefixed("audio-play-usage")); return; }
                String track = args[1];
                if (!module.tracks().exists(track)) { sender.sendMessage(cm.prefixed("audio-track-unknown", "track", track)); return; }
                forEachTarget(args[2], p -> module.playOneShot(p, track));
                sender.sendMessage(cm.prefixed("audio-played", "track", track, "target", args[2]));
            }
            case "stop" -> {
                if (args.length < 2) { sender.sendMessage(cm.prefixed("audio-stop-usage")); return; }
                forEachTarget(args[1], p -> module.audioState().stop(p));
                sender.sendMessage(cm.prefixed("audio-stopped", "target", args[1]));
            }
            case "volume" -> {
                if (args.length < 2) { sender.sendMessage(cm.prefixed("audio-volume-usage")); return; }
                try {
                    float v = Float.parseFloat(args[1]);
                    module.setMasterVolumePersistent(v);
                    sender.sendMessage(cm.prefixed("audio-volume-set", "value", String.valueOf(module.audioState().masterVolume())));
                } catch (NumberFormatException e) {
                    sender.sendMessage(cm.prefixed("audio-volume-usage"));
                }
            }
            case "reload" -> {
                module.reloadAudio();
                sender.sendMessage(cm.prefixed("audio-reloaded"));
            }
            case "debug" -> {
                sender.sendMessage(cm.message("audio-debug-header"));
                sender.sendMessage(cm.message("audio-debug-line",
                        "tracks", String.valueOf(module.tracks().all().size()),
                        "zones", String.valueOf(module.zones().zones().size()),
                        "global", module.loops().global() == null ? "—" : module.loops().global(),
                        "event", module.events().currentTrack() == null ? "—" : module.events().currentTrack()));
            }
            case "zone" -> zone(plugin, sender, args);
            default -> sender.sendMessage(cm.prefixed("audio-usage"));
        }
    }

    private void zone(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        if (action.equals("list")) {
            sender.sendMessage(cm.message("audio-zone-list-header"));
            for (Region r : module.zones().zones()) {
                sender.sendMessage(cm.message("audio-zone-list-entry",
                        "name", r.name(), "track", String.valueOf(module.zones().trackOf(r.name()))));
            }
            return;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(cm.prefixed("players-only")); return; }
        RegionSelection sel = module.zones().selection(p);
        switch (action) {
            case "setpos1" -> { sel.setPos1(p.getLocation()); sender.sendMessage(cm.prefixed("audio-zone-pos1")); }
            case "setpos2" -> { sel.setPos2(p.getLocation()); sender.sendMessage(cm.prefixed("audio-zone-pos2")); }
            case "create" -> {
                if (args.length < 3) { sender.sendMessage(cm.prefixed("audio-zone-create-usage")); return; }
                Region r = module.zones().create(args[2], sel);
                if (r == null) { sender.sendMessage(cm.prefixed("audio-zone-create-fail")); return; }
                module.zones().setEditing(p.getUniqueId(), r.name());
                sender.sendMessage(cm.prefixed("audio-zone-created", "name", r.name()));
            }
            case "settrack" -> {
                if (args.length < 3) { sender.sendMessage(cm.prefixed("audio-zone-settrack-usage")); return; }
                String name = module.zones().editing(p.getUniqueId());
                if (name == null) { sender.sendMessage(cm.prefixed("audio-zone-no-editing")); return; }
                if (!module.tracks().exists(args[2])) { sender.sendMessage(cm.prefixed("audio-track-unknown", "track", args[2])); return; }
                module.zones().setTrack(name, args[2]);
                module.applyAll();
                sender.sendMessage(cm.prefixed("audio-zone-track-set", "name", name, "track", args[2]));
            }
            case "delete" -> {
                String name = args.length >= 3 ? args[2] : module.zones().editing(p.getUniqueId());
                if (name == null) { sender.sendMessage(cm.prefixed("audio-zone-delete-usage")); return; }
                sender.sendMessage(module.zones().delete(name)
                        ? cm.prefixed("audio-zone-deleted", "name", name)
                        : cm.prefixed("audio-zone-unknown", "name", name));
                module.applyAll();
            }
            default -> sender.sendMessage(cm.prefixed("audio-zone-usage"));
        }
    }

    private void forEachTarget(String target, java.util.function.Consumer<Player> action) {
        if (target.equalsIgnoreCase("all")) {
            Bukkit.getOnlinePlayers().forEach(action);
        } else {
            Player p = Bukkit.getPlayerExact(target);
            if (p != null) action.accept(p);
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("play", "stop", "volume", "reload", "debug", "zone"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("play")) {
            return trackIds(args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("zone")) {
            return filter(List.of("create", "setpos1", "setpos2", "settrack", "delete", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("zone") && args[1].equalsIgnoreCase("settrack")) {
            return trackIds(args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("play")) {
            List<String> out = new ArrayList<>(List.of("all"));
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            return out;
        }
        return List.of();
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
