package com.mooncore.modules.essentials;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Attrs;
import com.mooncore.util.Text;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remplace les commandes utilitaires d'<b>EssentialsX</b> (volet QoL) : {@code /moon}
 * heal, feed, fly, god, repair, hat, near, workbench, back. État god/back en mémoire +
 * listeners. Les homes/spawn/tpa/afk sont déjà couverts par d'autres modules MoonCore.
 */
@ModuleInfo(id = "essentials", name = "Essentials")
public final class EssentialsModule extends AbstractModule implements Listener {

    private final Set<UUID> god = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> back = new ConcurrentHashMap<>();

    @Override
    protected void onEnable() {
        registerListener(this);
        var rc = plugin().rootCommand();
        rc.register(new Heal());
        rc.register(new Feed());
        rc.register(new Fly());
        rc.register(new God());
        rc.register(new Repair());
        rc.register(new Hat());
        rc.register(new Near());
        rc.register(new Workbench());
        rc.register(new Back());
    }

    @Override protected void onDisable() { god.clear(); back.clear(); }

    // ---- Listeners (god + mémorisation /back) ----

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && god.contains(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onTp(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld() != null) back.put(e.getPlayer().getUniqueId(), e.getFrom().clone());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        back.put(e.getEntity().getUniqueId(), e.getEntity().getLocation().clone());
    }

    // ================= commandes (classes internes) =================

    private abstract class Base implements SubCommand {
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }
    }

    private final class Heal extends Base {
        @Override public String name() { return "heal"; }
        @Override public String permission() { return "mooncore.heal.use"; }
        @Override public String description() { return "(staff) régénère la vie/faim"; }
        @Override public String category() { return "admin"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            AttributeInstance inst = p.getAttribute(Attrs.MAX_HEALTH);
            p.setHealth(inst != null ? inst.getValue() : 20.0);
            p.setFoodLevel(20); p.setSaturation(20f); p.setFireTicks(0);
            p.sendMessage(Text.mm("<green>❤ Soigné."));
        }
    }

    private final class Feed extends Base {
        @Override public String name() { return "feed"; }
        @Override public String permission() { return "mooncore.feed.use"; }
        @Override public String description() { return "(staff) restaure la faim"; }
        @Override public String category() { return "admin"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            p.setFoodLevel(20); p.setSaturation(20f);
            p.sendMessage(Text.mm("<green>🍗 Rassasié."));
        }
    }

    private final class Fly extends Base {
        @Override public String name() { return "fly"; }
        @Override public String permission() { return "mooncore.fly.use"; }
        @Override public String description() { return "(staff) active/désactive le vol"; }
        @Override public String category() { return "admin"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            boolean now = !p.getAllowFlight();
            p.setAllowFlight(now);
            if (now) p.setFlying(true);
            p.sendMessage(now ? Text.mm("<aqua>🕊 Vol activé.") : Text.mm("<gray>Vol désactivé."));
        }
    }

    private final class God extends Base {
        @Override public String name() { return "god"; }
        @Override public String permission() { return "mooncore.god.use"; }
        @Override public String description() { return "(staff) invulnérabilité"; }
        @Override public String category() { return "admin"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            if (god.remove(p.getUniqueId())) p.sendMessage(Text.mm("<gray>Mode invincible désactivé."));
            else { god.add(p.getUniqueId()); p.sendMessage(Text.mm("<gold>✦ Mode invincible activé.")); }
        }
    }

    private final class Repair extends Base {
        @Override public String name() { return "repair"; }
        @Override public String permission() { return "mooncore.repair.use"; }
        @Override public String description() { return "(staff) répare l'objet en main"; }
        @Override public String category() { return "admin"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            ItemStack it = p.getInventory().getItemInMainHand();
            if (it.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                dmg.setDamage(0); it.setItemMeta(dmg);
                p.sendMessage(Text.mm("<green>🔧 Objet réparé."));
            } else {
                p.sendMessage(Text.mm("<red>Rien à réparer en main."));
            }
        }
    }

    private final class Hat extends Base {
        @Override public String name() { return "hat"; }
        @Override public String permission() { return "mooncore.hat.use"; }
        @Override public String description() { return "Porte l'objet en main sur la tête"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) { p.sendMessage(Text.mm("<red>Tiens un objet en main.")); return; }
            ItemStack old = p.getInventory().getHelmet();
            p.getInventory().setHelmet(hand.clone());
            p.getInventory().setItemInMainHand(old);
            p.sendMessage(Text.mm("<green>🎩 Chapeau équipé."));
        }
    }

    private final class Near extends Base {
        @Override public String name() { return "near"; }
        @Override public String permission() { return "mooncore.near.use"; }
        @Override public String description() { return "Liste les joueurs proches"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            double r = 100;
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (Player o : p.getWorld().getPlayers()) {
                if (o.equals(p)) continue;
                double d = o.getLocation().distance(p.getLocation());
                if (d <= r) { sb.append(sb.isEmpty() ? "" : ", ").append(o.getName()).append(" <dark_gray>(").append((int) d).append("m)"); n++; }
            }
            p.sendMessage(n == 0 ? Text.mm("<gray>Personne à moins de " + (int) r + "m.")
                    : Text.mm("<gray>Proches (" + n + ") : <white>" + sb));
        }
    }

    private final class Workbench extends Base {
        @Override public String name() { return "workbench"; }
        @Override public List<String> aliases() { return List.of("craft", "wb"); }
        @Override public String permission() { return "mooncore.workbench.use"; }
        @Override public String description() { return "Ouvre un établi"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            ((Player) s).openWorkbench(null, true);
        }
    }

    private final class Back extends Base {
        @Override public String name() { return "back"; }
        @Override public String permission() { return "mooncore.back.use"; }
        @Override public String description() { return "Retourne à ta position précédente (tp/mort)"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            Location loc = back.get(p.getUniqueId());
            if (loc == null || loc.getWorld() == null) { p.sendMessage(Text.mm("<red>Aucune position précédente.")); return; }
            p.teleport(loc);
            p.sendMessage(Text.mm("<green>↩ Retour à la position précédente."));
        }
    }
}
