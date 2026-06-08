package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@code /moon boss <list|spawn>} — gestion des boss. */
public final class BossSubCommand implements SubCommand {

    private final BossManagerModule module;

    public BossSubCommand(BossManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "boss"; }
    @Override public List<String> aliases() { return List.of("bosses"); }
    @Override public String permission() { return "mooncore.admin.bosses"; }
    @Override public String description() { return "Gestion des boss"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                sender.sendMessage(cm.message("boss-list-header"));
                if (module.bossIds().isEmpty()) {
                    sender.sendMessage(cm.message("boss-list-empty"));
                    return;
                }
                module.bossIds().forEach(id -> sender.sendMessage(cm.message("boss-list-entry", "id", id)));
                sender.sendMessage(cm.message("boss-active", "count", String.valueOf(module.activeCount())));
            }
            case "spawn" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cm.prefixed("players-only"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("boss-spawn-usage"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!module.exists(id)) {
                    sender.sendMessage(cm.prefixed("boss-unknown", "id", id));
                    return;
                }
                if (module.spawn(id, p.getLocation())) {
                    sender.sendMessage(cm.prefixed("boss-spawn-ok", "id", id));
                } else {
                    sender.sendMessage(cm.prefixed("boss-spawn-fail", "id", id));
                }
            }
            case "paint", "draw" -> paint(plugin, sender, args);
            default -> sender.sendMessage(cm.prefixed("boss-usage"));
        }
    }

    private void paint(MoonCore plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.configManager().prefixed("players-only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.mm("<red>/moon boss paint <id> [taille 16|32|64] [base]"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!module.exists(id)) {
            sender.sendMessage(plugin.configManager().prefixed("boss-unknown", "id", id));
            return;
        }
        var ci = plugin.moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
        if (ci == null || ci.paintManager() == null) {
            sender.sendMessage(Text.mm("<red>Éditeur indisponible (module custom-item inactif)."));
            return;
        }
        int size = 16;
        java.io.File source = null;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("16") || args[i].equals("32") || args[i].equals("64")) size = Integer.parseInt(args[i]);
            else {
                source = com.mooncore.modules.customitem.paint.PaintManager.resolveTexture(plugin, args[i]);
                if (source == null) sender.sendMessage(Text.mm("<yellow>Texture source introuvable : " + args[i] + " (toile vide)."));
                else sender.sendMessage(Text.mm("<gray>Import de la texture <white>" + args[i] + "<gray> comme base."));
            }
        }
        ci.paintManager().open(p, new com.mooncore.modules.customitem.paint.BossPaintTarget(module, id), size, source);
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("list", "spawn", "paint"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("paint"))) {
            return filter(new ArrayList<>(module.bossIds()), args[1]);
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
