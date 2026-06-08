package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.event.EventManagerModule;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon event <list|start|stop|active>} — gestion des événements. */
public final class EventSubCommand implements SubCommand {

    private final EventManagerModule module;

    public EventSubCommand(EventManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "event"; }
    @Override public List<String> aliases() { return List.of("events"); }
    @Override public String permission() { return "mooncore.admin.events"; }
    @Override public String description() { return "Gestion des événements"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                sender.sendMessage(cm.message("event-list-header"));
                if (module.eventIds().isEmpty()) {
                    sender.sendMessage(cm.message("event-list-empty"));
                    return;
                }
                module.eventIds().forEach(id -> sender.sendMessage(cm.message("event-list-entry",
                        "id", id, "state", module.isActive(id) ? "<green>actif</green>" : "<gray>inactif</gray>")));
            }
            case "active" -> sender.sendMessage(cm.message("event-active",
                    "list", module.activeIds().isEmpty() ? "—" : String.join(", ", module.activeIds())));
            case "start" -> {
                if (args.length < 2) { sender.sendMessage(cm.prefixed("event-start-usage")); return; }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!module.exists(id)) { sender.sendMessage(cm.prefixed("event-unknown", "id", id)); return; }
                sender.sendMessage(module.start(id)
                        ? cm.prefixed("event-started", "id", id)
                        : cm.prefixed("event-already", "id", id));
            }
            case "stop" -> {
                if (args.length < 2) { sender.sendMessage(cm.prefixed("event-stop-usage")); return; }
                String id = args[1].toLowerCase(Locale.ROOT);
                sender.sendMessage(module.stop(id)
                        ? cm.prefixed("event-stopped", "id", id)
                        : cm.prefixed("event-not-active", "id", id));
            }
            default -> sender.sendMessage(cm.prefixed("event-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("list", "start", "stop", "active"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            return filter(new ArrayList<>(module.eventIds()), args[1]);
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
