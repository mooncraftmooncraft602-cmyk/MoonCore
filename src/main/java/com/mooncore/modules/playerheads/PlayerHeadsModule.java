package com.mooncore.modules.playerheads;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Remplace <b>PlayerHeads</b> : à la mort, chance de lâcher la TÊTE de l'entité — tête de joueur
 * (avec le skin) ou tête de mob vanilla (zombie, squelette, creeper, etc.). Chances configurables
 * dans {@code modules/playerheads.yml} (mob-chance, player-chance, player-needs-killer).
 */
@ModuleInfo(id = "playerheads", name = "PlayerHeads")
public final class PlayerHeadsModule extends AbstractModule implements Listener {

    private double mobChance, playerChance;
    private boolean playerNeedsKiller;

    @Override
    protected void onEnable() { load(); registerListener(this); }

    @Override protected void onDisable() { }
    @Override protected void onReload() { reloadModuleConfig(); load(); }

    private void load() {
        mobChance = clamp(moduleConfig().getDouble("mob-chance", 0.05));
        playerChance = clamp(moduleConfig().getDouble("player-chance", 1.0));
        playerNeedsKiller = moduleConfig().getBoolean("player-needs-killer", true);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (e.getEntity() instanceof Player dead) {
            if (playerNeedsKiller && dead.getKiller() == null) return;
            if (rnd.nextDouble() < playerChance) e.getDrops().add(playerHead(dead));
            return;
        }
        Material head = mobHead(e.getEntityType());
        if (head != null && rnd.nextDouble() < mobChance) e.getDrops().add(new ItemStack(head));
    }

    private static ItemStack playerHead(Player p) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(p);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Têtes de mob vanilla disponibles comme items. */
    private static Material mobHead(EntityType type) {
        return switch (type) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case PIGLIN -> Material.PIGLIN_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            default -> null;
        };
    }
}
