package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.zone.Region;
import com.mooncore.api.zone.ZoneFlag;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.zone.RegionSelection;
import com.mooncore.modules.zone.ZoneModule;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon zone <wand|create|delete|list|info|flag|priority>} — gestion des zones. */
public final class ZoneSubCommand implements SubCommand {

    private final MoonCore plugin;
    private final ZoneModule zone;

    public ZoneSubCommand(MoonCore plugin, ZoneModule zone) {
        this.plugin = plugin;
        this.zone = zone;
    }

    @Override public String name() { return "zone"; }
    @Override public String permission() { return "mooncore.admin.zones"; }
    @Override public String description() { return "Gestion des zones et flags"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length == 0) {
            sender.sendMessage(cm.prefixed("zone-usage"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> wand(sender);
            case "create" -> create(sender, args);
            case "delete", "remove" -> delete(sender, args);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "flag" -> flag(sender, args);
            case "priority" -> priority(sender, args);
            default -> sender.sendMessage(cm.prefixed("zone-usage"));
        }
    }

    private void wand(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.configManager().prefixed("players-only"));
            return;
        }
        p.getInventory().addItem(new ItemStack(Material.GOLDEN_HOE));
        p.sendMessage(plugin.configManager().prefixed("zone-wand-given"));
    }

    private void create(CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (!(sender instanceof Player p)) {
            sender.sendMessage(cm.prefixed("players-only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(cm.prefixed("zone-create-usage"));
            return;
        }
        RegionSelection sel = zone.selection(p);
        if (!sel.isComplete()) {
            sender.sendMessage(cm.prefixed("zone-need-selection"));
            return;
        }
        int priority = 0;
        if (args.length >= 3) {
            try { priority = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
        }
        Region region = zone.createRegion(args[1], sel, priority);
        if (region == null) {
            sender.sendMessage(cm.prefixed("zone-exists", "name", args[1]));
            return;
        }
        sender.sendMessage(cm.prefixed("zone-created", "name", region.name()));
    }

    private void delete(CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length < 2) {
            sender.sendMessage(cm.prefixed("zone-delete-usage"));
            return;
        }
        if (zone.deleteRegion(args[1])) {
            sender.sendMessage(cm.prefixed("zone-deleted", "name", args[1]));
        } else {
            sender.sendMessage(cm.prefixed("zone-not-found", "name", args[1]));
        }
    }

    private void list(CommandSender sender) {
        var cm = plugin.configManager();
        sender.sendMessage(cm.message("zone-list-header"));
        if (zone.regions().isEmpty()) {
            sender.sendMessage(cm.message("zone-list-empty"));
            return;
        }
        for (Region r : zone.regions()) {
            sender.sendMessage(cm.message("zone-list-entry",
                    "name", r.name(),
                    "world", r.world(),
                    "priority", String.valueOf(r.priority())));
        }
    }

    private void info(CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length < 2) {
            sender.sendMessage(cm.prefixed("zone-info-usage"));
            return;
        }
        Region r = zone.region(args[1]);
        if (r == null) {
            sender.sendMessage(cm.prefixed("zone-not-found", "name", args[1]));
            return;
        }
        sender.sendMessage(cm.message("zone-info-header", "name", r.name()));
        sender.sendMessage(cm.message("zone-info-bounds",
                "world", r.world(),
                "min", r.minX() + "," + r.minY() + "," + r.minZ(),
                "max", r.maxX() + "," + r.maxY() + "," + r.maxZ(),
                "priority", String.valueOf(r.priority())));
        if (r.flags().isEmpty()) {
            sender.sendMessage(cm.message("zone-info-noflags"));
        } else {
            r.flags().forEach((f, v) -> sender.sendMessage(cm.message("zone-info-flag",
                    "flag", f.key(), "value", String.valueOf(v))));
        }
    }

    private void flag(CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length < 4) {
            sender.sendMessage(cm.prefixed("zone-flag-usage"));
            return;
        }
        Region r = zone.region(args[1]);
        if (r == null) {
            sender.sendMessage(cm.prefixed("zone-not-found", "name", args[1]));
            return;
        }
        var flagOpt = ZoneFlag.byKey(args[2]);
        if (flagOpt.isEmpty()) {
            sender.sendMessage(cm.prefixed("zone-flag-unknown", "flag", args[2]));
            return;
        }
        Boolean value;
        String val = args[3].toLowerCase(Locale.ROOT);
        if (val.equals("unset") || val.equals("none")) value = null;
        else if (val.equals("true") || val.equals("on") || val.equals("yes")) value = true;
        else if (val.equals("false") || val.equals("off") || val.equals("no")) value = false;
        else {
            sender.sendMessage(cm.prefixed("zone-flag-usage"));
            return;
        }
        zone.setFlag(r.name(), flagOpt.get(), value);
        sender.sendMessage(cm.prefixed("zone-flag-set",
                "flag", flagOpt.get().key(),
                "value", value == null ? "unset" : String.valueOf(value),
                "name", r.name()));
    }

    private void priority(CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        if (args.length < 3) {
            sender.sendMessage(cm.prefixed("zone-priority-usage"));
            return;
        }
        Region r = zone.region(args[1]);
        if (r == null) {
            sender.sendMessage(cm.prefixed("zone-not-found", "name", args[1]));
            return;
        }
        try {
            int prio = Integer.parseInt(args[2]);
            r.setPriority(prio);
            // L'index trie par priorité à la requête : pas besoin de réindexer, juste persister.
            zone.saveRegion(r);
            sender.sendMessage(cm.prefixed("zone-priority-set", "name", r.name(), "priority", String.valueOf(prio)));
        } catch (NumberFormatException ex) {
            sender.sendMessage(cm.prefixed("zone-priority-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("wand", "create", "delete", "list", "info", "flag", "priority"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("delete") || sub.equals("info") || sub.equals("flag") || sub.equals("priority"))) {
            List<String> names = new ArrayList<>();
            zone.regions().forEach(r -> names.add(r.name()));
            return filter(names, args[1]);
        }
        if (args.length == 3 && sub.equals("flag")) {
            List<String> keys = new ArrayList<>();
            for (ZoneFlag f : ZoneFlag.values()) keys.add(f.key());
            return filter(keys, args[2]);
        }
        if (args.length == 4 && sub.equals("flag")) {
            return filter(List.of("true", "false", "unset"), args[3]);
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
