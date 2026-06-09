package com.mooncore.modules.vanish;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remplace le plugin <b>Vanish</b> : {@code /moon vanish} rend un membre du staff invisible
 * pour les autres joueurs (caché de la liste des entités + masqué à la connexion des nouveaux).
 * État en mémoire (réinitialisé au redémarrage). Le module EST la sous-commande et le listener.
 */
@ModuleInfo(id = "vanish", name = "Vanish")
public final class VanishModule extends AbstractModule implements SubCommand, Listener {

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    @Override
    protected void onEnable() {
        plugin().rootCommand().register(this);
        registerListener(this);
    }

    @Override
    protected void onDisable() {
        // Rends tout le monde visible (évite des joueurs fantômes après un reload).
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null) for (Player o : Bukkit.getOnlinePlayers()) o.showEntity(plugin(), v);
        }
        vanished.clear();
    }

    public boolean isVanished(UUID id) { return vanished.contains(id); }

    // ---- SubCommand ----

    @Override public String name() { return "vanish"; }
    @Override public java.util.List<String> aliases() { return java.util.List.of("v"); }
    @Override public String permission() { return "mooncore.vanish.use"; }
    @Override public String description() { return "(staff) devient invisible pour les autres"; }
    @Override public String category() { return "admin"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        Player p = (Player) sender;
        if (vanished.remove(p.getUniqueId())) {
            for (Player o : Bukkit.getOnlinePlayers()) o.showEntity(plugin, p);
            p.sendMessage(Text.mm("<green>Tu es de nouveau visible."));
        } else {
            vanished.add(p.getUniqueId());
            for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p) && !o.hasPermission("mooncore.vanish.see")) o.hideEntity(plugin, p);
            p.sendMessage(Text.mm("<gray>Tu es maintenant <white>invisible</white> (vanish)."));
        }
    }

    // ---- Listener ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player joiner = e.getPlayer();
        // Cache les joueurs déjà en vanish au nouveau venu.
        for (UUID id : vanished) {
            Player v = Bukkit.getPlayer(id);
            if (v != null && !v.equals(joiner) && !joiner.hasPermission("mooncore.vanish.see")) joiner.hideEntity(plugin(), v);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        vanished.remove(e.getPlayer().getUniqueId()); // pas de persistance du vanish entre sessions
    }
}
