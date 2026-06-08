package com.mooncore.modules.customitem.paint;

import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Routage des entrées de l'éditeur de texture vers la {@link PaintSession} du joueur. */
public final class PaintListener implements Listener {

    private final PaintManager manager;

    public PaintListener(PaintManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        PaintSession s = manager.get(e.getPlayer().getUniqueId());
        if (s != null) s.onSwing(); // clic gauche / drag = dessiner (ou pipette monde)
    }

    /** Sneak pendant la pipette monde = annuler et revenir à la toile. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        PaintSession s = manager.get(e.getPlayer().getUniqueId());
        if (s != null && s.worldPick()) { s.exitWorldPick(); e.getPlayer().sendActionBar(com.mooncore.util.Text.mm("<gray>Pipette annulée")); }
        else if (s != null && s.cursorPinned()) s.unpinCursor();
    }

    /** Protège les blocs du monde tant qu'une session d'édition est ouverte. */
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (manager.has(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRightClick(PlayerInteractEvent e) {
        PaintSession s = manager.get(e.getPlayer().getUniqueId());
        if (s == null) return;
        switch (e.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                e.setCancelled(true);
                s.onRightClick(e.getPlayer().getInventory().getHeldItemSlot());
            }
            default -> { }
        }
    }

    @EventHandler
    public void onSlot(PlayerItemHeldEvent e) {
        PaintSession s = manager.get(e.getPlayer().getUniqueId());
        if (s != null) s.onSlotChange(e.getNewSlot());
    }

    /** Protège l'item frame de la toile (clic droit = rotation). */
    @EventHandler
    public void onFrameInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame)) return;
        if (manager.has(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    /** Protège l'item frame de la toile (clic gauche = casse). */
    @EventHandler
    public void onFrameDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ItemFrame)) return;
        if (e.getDamager() instanceof Player p && manager.has(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (manager.has(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (manager.has(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.close(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PaintSettingsMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (e.getWhoClicked() instanceof Player p) menu.click(p, e.getRawSlot(), e.isRightClick());
    }

    @EventHandler
    public void onAssistantClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PaintAssistantMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (e.getWhoClicked() instanceof Player p) menu.click(p, e.getRawSlot());
    }

    @EventHandler
    public void onTemplatesClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PaintTemplatesMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (e.getWhoClicked() instanceof Player p) menu.click(p, e.getRawSlot());
    }

    @EventHandler
    public void onPrecisionClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PaintPrecisionMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (e.getWhoClicked() instanceof Player p) menu.click(p, e.getRawSlot(), e.isRightClick());
    }

    @EventHandler
    public void onAnimationClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AnimationMenu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (e.getWhoClicked() instanceof Player p) menu.click(p, e.getRawSlot(), e.isRightClick());
    }
}
