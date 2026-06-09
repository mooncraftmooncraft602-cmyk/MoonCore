package com.mooncore.modules.pvp;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Remplace <b>PvPManager</b> (volet « toggle PvP ») : {@code /moon pvp} active/désactive son
 * propre PvP (persisté dans la PDC du joueur). Un coup joueur→joueur est annulé si l'un des deux
 * a le PvP désactivé. État par défaut : activé.
 */
@ModuleInfo(id = "pvp", name = "PvPManager")
public final class PvPManagerModule extends AbstractModule implements SubCommand, Listener {

    private NamespacedKey key;

    @Override
    protected void onEnable() {
        key = new NamespacedKey(plugin(), "pvp_enabled");
        plugin().rootCommand().register(this);
        registerListener(this);
    }

    @Override protected void onDisable() { }

    /** PvP activé pour ce joueur ? (absence de clé = activé par défaut). */
    public boolean pvpEnabled(Player p) {
        Byte b = p.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return b == null || b != 0;
    }

    private void setPvp(Player p, boolean enabled) {
        p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    // ---- SubCommand ----

    @Override public String name() { return "pvp"; }
    @Override public String permission() { return "mooncore.pvp.use"; }
    @Override public String description() { return "Active/désactive ton PvP"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        Player p = (Player) sender;
        boolean now = !pvpEnabled(p);
        setPvp(p, now);
        p.sendMessage(now ? Text.mm("<red>⚔ PvP <white>activé</white> pour toi.")
                : Text.mm("<green>🛡 PvP <white>désactivé</white> pour toi."));
    }

    // ---- Listener ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player victim = e.getEntity() instanceof Player v ? v : null;
        if (victim == null) return;
        Player attacker = resolveAttacker(e);
        if (attacker == null || attacker.equals(victim)) return;

        if (!pvpEnabled(attacker)) {
            e.setCancelled(true);
            attacker.sendActionBar(Text.mm("<gray>Ton PvP est désactivé (<white>/moon pvp</white>)."));
        } else if (!pvpEnabled(victim)) {
            e.setCancelled(true);
            attacker.sendActionBar(Text.mm("<gray>Ce joueur a le PvP désactivé."));
        }
    }

    /** Attaquant joueur direct OU tireur d'un projectile. */
    private static Player resolveAttacker(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
