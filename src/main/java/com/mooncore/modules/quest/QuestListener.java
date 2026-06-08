package com.mooncore.modules.quest;

import com.mooncore.api.mission.ObjectiveType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Charge/sauvegarde les quêtes et fait progresser l'étape courante via les événements. */
public final class QuestListener implements Listener {

    private final QuestManagerModule module;

    public QuestListener(QuestManagerModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { module.load(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { module.unload(e.getPlayer().getUniqueId()); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMine(BlockBreakEvent e) { module.track(e.getPlayer().getUniqueId(), ObjectiveType.MINE_BLOCK, 1); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) { module.track(e.getPlayer().getUniqueId(), ObjectiveType.PLACE_BLOCK, 1); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        module.track(killer.getUniqueId(),
                (e.getEntity() instanceof Player) ? ObjectiveType.KILL_PLAYER : ObjectiveType.KILL_MOB, 1);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            module.track(e.getPlayer().getUniqueId(), ObjectiveType.FISH_CATCH, 1);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            module.track(p.getUniqueId(), ObjectiveType.CRAFT_ITEM, Math.max(1, e.getRecipe().getResult().getAmount()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) {
            module.track(p.getUniqueId(), ObjectiveType.BREED_ANIMAL, 1);
        }
    }
}
