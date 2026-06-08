package com.mooncore.command.sub;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.enchant.CustomEnchant;
import com.mooncore.modules.enchant.EnchantManagerModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /moon enchant list} — liste les enchantements.
 * {@code /moon enchant give <id> [niveau]} — applique sur l'objet en main.
 */
public final class EnchantSubCommand implements SubCommand {

    private final EnchantManagerModule module;

    public EnchantSubCommand(EnchantManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "enchant"; }
    @Override public List<String> aliases() { return List.of("enchants", "ench"); }
    @Override public String permission() { return "mooncore.admin.enchants"; }
    @Override public String description() { return "Gestion des enchantements custom"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        var cm = plugin.configManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                sender.sendMessage(cm.message("enchant-list-header", "count", String.valueOf(module.all().size())));
                for (CustomEnchant e : module.all()) {
                    sender.sendMessage(cm.message("enchant-list-entry",
                            "id", e.id(), "name", e.displayName(),
                            "max", String.valueOf(e.maxLevel()),
                            "target", e.target().name().toLowerCase(Locale.ROOT)));
                }
            }
            case "give" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cm.prefixed("players-only"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(cm.prefixed("enchant-give-usage"));
                    return;
                }
                CustomEnchant e = module.byId(args[1]);
                if (e == null) {
                    sender.sendMessage(cm.prefixed("enchant-unknown", "id", args[1]));
                    return;
                }
                int level = e.maxLevel();
                if (args.length >= 3) {
                    try { level = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    sender.sendMessage(cm.prefixed("enchant-no-item"));
                    return;
                }
                if (module.apply(hand, e, level)) {
                    sender.sendMessage(cm.prefixed("enchant-applied",
                            "name", e.displayName(), "level", String.valueOf(level)));
                } else {
                    sender.sendMessage(cm.prefixed("enchant-incompatible",
                            "name", e.displayName(), "target", e.target().name().toLowerCase(Locale.ROOT)));
                }
            }
            default -> sender.sendMessage(cm.prefixed("enchant-usage"));
        }
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1) return filter(List.of("list", "give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = new ArrayList<>();
            module.all().forEach(e -> ids.add(e.id()));
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
