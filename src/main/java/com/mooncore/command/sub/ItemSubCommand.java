package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.api.item.CustomItemService;
import com.mooncore.command.SubCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon item list} — liste les objets endgame.
 * {@code /moon item give <id> [joueur]} — donne un objet endgame.
 */
public final class ItemSubCommand implements SubCommand {

    private final CustomItemService service;

    public ItemSubCommand(CustomItemService service) {
        this.service = service;
    }

    @Override public String name() { return "item"; }
    @Override public List<String> aliases() { return List.of("items", "endgame"); }
    @Override public String permission() { return "mooncore.admin.rewards"; }
    @Override public String description() { return "Objets endgame"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                sender.sendMessage(cm.message("item-list-header"));
                service.ids().forEach(id -> sender.sendMessage(cm.message("item-list-entry", "id", id)));
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("item-give-usage"));
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                Player target = (args.length >= 3) ? Bukkit.getPlayerExact(args[2])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    sender.sendMessage(cm.prefixed("item-give-target"));
                    return;
                }
                if (service.give(target, id)) {
                    sender.sendMessage(cm.prefixed("item-given", "id", id, "player", target.getName()));
                } else {
                    sender.sendMessage(cm.prefixed("item-unknown", "id", id));
                }
            }
            default -> sender.sendMessage(cm.prefixed("item-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("list", "give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(new ArrayList<>(service.ids()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
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
