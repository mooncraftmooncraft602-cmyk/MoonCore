package com.mooncore.modules.enditems;

import com.mooncore.MoonCore;
import com.mooncore.util.Cooldowns;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/** Comportements actifs des objets endgame (ex. Bâton de Rappel). */
public final class EndItemListener implements Listener {

    private static final long RECALL_COOLDOWN_MS = 60_000;

    private final MoonCore plugin;
    private final EndgameItemsModule module;
    private final Cooldowns<UUID> recallCooldown = new Cooldowns<>();

    public EndItemListener(MoonCore plugin, EndgameItemsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null) return;
        if (!EndgameItemsModule.RECALL_STAFF.equals(module.idOf(e.getItem()))) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        if (!recallCooldown.tryAcquire(p.getUniqueId(), now, RECALL_COOLDOWN_MS)) {
            long left = recallCooldown.remaining(p.getUniqueId(), now, RECALL_COOLDOWN_MS) / 1000;
            p.sendMessage(plugin.configManager().prefixed("item-recall-cooldown", "seconds", String.valueOf(left)));
            return;
        }

        Location target = p.getRespawnLocation();
        if (target == null) target = p.getWorld().getSpawnLocation();
        p.teleport(target);
        p.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        p.sendMessage(plugin.configManager().prefixed("item-recall-done"));
    }
}
