package com.mooncore.modules.messaging;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remplace la messagerie privée d'EssentialsX : {@code /moon msg <joueur> <message>} et
 * {@code /moon reply <message>}. Le corps du message est envoyé en TEXTE BRUT (pas de parsing
 * MiniMessage) → aucune injection de balises/couleurs par les joueurs.
 */
@ModuleInfo(id = "messaging", name = "Messaging")
public final class MessagingModule extends AbstractModule {

    /** Dernier interlocuteur de chaque joueur (pour /reply). */
    private final Map<UUID, UUID> reply = new ConcurrentHashMap<>();

    @Override
    protected void onEnable() {
        plugin().rootCommand().register(new Msg());
        plugin().rootCommand().register(new Reply());
    }

    @Override protected void onDisable() { reply.clear(); }

    private void deliver(Player from, Player to, String body) {
        Component msg = Component.text(body);
        from.sendMessage(Text.mm("<dark_gray>[<gray>moi <dark_gray>→ <white>" + to.getName() + "<dark_gray>] <gray>").append(msg));
        to.sendMessage(Text.mm("<dark_gray>[<white>" + from.getName() + " <dark_gray>→ <gray>moi<dark_gray>] <gray>").append(msg));
        reply.put(from.getUniqueId(), to.getUniqueId());
        reply.put(to.getUniqueId(), from.getUniqueId());
    }

    private final class Msg implements SubCommand {
        @Override public String name() { return "msg"; }
        @Override public List<String> aliases() { return List.of("tell", "w", "pm", "dm"); }
        @Override public String permission() { return "mooncore.msg.use"; }
        @Override public String description() { return "Message privé à un joueur"; }
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }

        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            if (a.length < 2) { p.sendMessage(Text.mm("<red>Usage : /moon msg <joueur> <message>")); return; }
            Player t = Bukkit.getPlayerExact(a[0]);
            if (t == null) { p.sendMessage(Text.mm("<red>Joueur hors-ligne : <white>" + a[0])); return; }
            deliver(p, t, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
        }

        @Override public List<String> tabComplete(MoonCore pl, CommandSender s, String[] a) {
            if (a.length == 1) {
                String pre = a[0].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pre)).sorted().toList();
            }
            return List.of();
        }
    }

    private final class Reply implements SubCommand {
        @Override public String name() { return "reply"; }
        @Override public List<String> aliases() { return List.of("r"); }
        @Override public String permission() { return "mooncore.msg.use"; }
        @Override public String description() { return "Répond au dernier message privé"; }
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }

        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            if (a.length < 1) { p.sendMessage(Text.mm("<red>Usage : /moon reply <message>")); return; }
            UUID id = reply.get(p.getUniqueId());
            Player t = id == null ? null : Bukkit.getPlayer(id);
            if (t == null) { p.sendMessage(Text.mm("<red>Personne à qui répondre.")); return; }
            deliver(p, t, String.join(" ", a));
        }
    }
}
