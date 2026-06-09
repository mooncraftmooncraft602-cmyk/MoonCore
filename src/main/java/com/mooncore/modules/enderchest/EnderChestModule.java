package com.mooncore.modules.enderchest;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Remplace le plugin <b>EnderChest</b> : {@code /moon ec} ouvre son propre enderchest,
 * {@code /moon ec <joueur>} (admin) ouvre celui d'un joueur en ligne. Le module EST la
 * sous-commande (s'enregistre lui-même sur {@code /moon}).
 */
@ModuleInfo(id = "enderchest", name = "EnderChest")
public final class EnderChestModule extends AbstractModule implements SubCommand {

    @Override protected void onEnable() { plugin().rootCommand().register(this); }
    @Override protected void onDisable() { }

    @Override public String name() { return "ec"; }
    @Override public List<String> aliases() { return List.of("enderchest"); }
    @Override public String permission() { return "mooncore.ec.use"; }
    @Override public String description() { return "Ouvre ton enderchest (admin: celui d'un autre)"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        Player p = (Player) sender;
        if (args.length >= 1) {
            if (!p.hasPermission("mooncore.admin.enderchest")) {
                p.sendMessage(plugin.configManager().prefixed("no-permission"));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                p.sendMessage(Text.mm("<red>Joueur introuvable ou hors-ligne : <white>" + args[0]));
                return;
            }
            p.openInventory(target.getEnderChest());
            p.sendMessage(Text.mm("<gray>Enderchest de <white>" + target.getName() + "<gray> ouvert."));
            return;
        }
        p.openInventory(p.getEnderChest());
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        if (args.length == 1 && sender.hasPermission("mooncore.admin.enderchest")) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pre)).sorted().toList();
        }
        return List.of();
    }
}
