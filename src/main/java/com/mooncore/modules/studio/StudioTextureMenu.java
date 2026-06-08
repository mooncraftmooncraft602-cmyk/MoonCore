package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.paint.BlockPaintTarget;
import com.mooncore.modules.customitem.paint.ItemPaintTarget;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Raccourcis texture pour eviter de chercher les commandes. */
public final class StudioTextureMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private Inventory inv;

    private StudioTextureMenu(MoonCore plugin, ChatInput chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p) {
        StudioTextureMenu menu = new StudioTextureMenu(plugin, chat);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>> Textures"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        CustomBlockManagerModule cb = plugin.moduleManager().get(CustomBlockManagerModule.class);
        BossManagerModule boss = plugin.moduleManager().get(BossManagerModule.class);

        inv.setItem(10, StudioItems.btn(Material.NETHER_STAR, "<gold>Textures items",
                "<gray>ouvre la liste des items",
                "<dark_gray>" + (ci == null ? "module inactif" : ci.rawDefs().size() + " item(s)")));
        inv.setItem(12, StudioItems.btn(Material.NOTE_BLOCK, "<aqua>Textures blocs",
                "<gray>texture unique ou faces haut/cotes/bas",
                "<dark_gray>" + (cb == null ? "module inactif" : cb.rawDefs().size() + " bloc(s)")));
        inv.setItem(14, StudioItems.btn(Material.WITHER_SKELETON_SKULL, "<red>Textures boss",
                "<gray>casque modele custom pour boss",
                "<dark_gray>" + (boss == null ? "module inactif" : boss.bossIds().size() + " boss")));
        inv.setItem(16, StudioItems.btn(Material.BRUSH, "<light_purple>Peindre item tenu",
                "<gray>item custom ou bloc custom dans la main"));

        inv.setItem(28, StudioItems.btn(Material.GRASS_BLOCK, "<aqua>Importer vanilla",
                "<gray>permet d'utiliser les textures Minecraft comme base"));
        inv.setItem(30, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Texture IA item tenu",
                "<gray>decris une nouvelle texture pour l'item custom en main"));
        inv.setItem(32, StudioItems.btn(Material.MAP, "<green>Rebuild + resend"));
        inv.setItem(34, StudioItems.btn(Material.COMPASS, "<white>Actualiser"));
        inv.setItem(49, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        switch (slot) {
            case 10 -> StudioItemMenu.open(plugin, chat, p, 0);
            case 12 -> StudioBlockMenu.open(plugin, chat, p, 0);
            case 14 -> StudioBossMenu.open(plugin, chat, p, 0);
            case 16 -> paintHeld(p);
            case 28 -> {
                p.closeInventory();
                p.performCommand("moon item importvanilla");
                p.performCommand("moon block importvanilla");
            }
            case 30 -> retextureHeldItem(p);
            case 32 -> StudioItems.rebuildAndResend(plugin, p);
            case 34 -> { build(); p.sendActionBar(Text.mm("<gray>Textures actualisees")); }
            case 49 -> StudioHubMenu.open(plugin, chat, p);
            default -> { }
        }
    }

    private void paintHeld(Player p) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (ci == null) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        String itemId = ci.factory().idOf(hand);
        if (itemId != null && ci.rawDef(itemId) != null) {
            p.closeInventory();
            ci.paintManager().open(p, new ItemPaintTarget(ci, itemId), 16, null, () -> {
                if (p.isOnline()) open(plugin, chat, p);
            });
            return;
        }
        CustomBlockManagerModule cb = plugin.moduleManager().get(CustomBlockManagerModule.class);
        String blockId = cb == null ? null : cb.idFromItem(hand);
        if (blockId != null && cb.rawDef(blockId) != null) {
            p.closeInventory();
            ci.paintManager().open(p, new BlockPaintTarget(cb, blockId), 16, null, () -> {
                if (p.isOnline()) open(plugin, chat, p);
            });
            return;
        }
        p.sendMessage(Text.mm("<red>Tiens un item custom ou bloc custom dans la main."));
    }

    private void retextureHeldItem(Player p) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (ci == null) return;
        String itemId = ci.factory().idOf(p.getInventory().getItemInMainHand());
        if (itemId == null || ci.rawDef(itemId) == null) {
            p.sendMessage(Text.mm("<red>Tiens un item custom dans la main."));
            return;
        }
        p.closeInventory();
        chat.request(p, "<yellow>Description de texture IA :", in -> p.performCommand("moon ai retexture " + itemId + " " + in));
    }

    @Override
    public Inventory getInventory() { return inv; }
}
