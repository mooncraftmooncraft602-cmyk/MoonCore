package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Premiere page de Moon Studio. */
public final class StudioHubMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private Inventory inv;

    private StudioHubMenu(MoonCore plugin, ChatInput chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p) {
        StudioHubMenu menu = new StudioHubMenu(plugin, chat);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Moon Studio</gradient> <dark_gray>> creation admin"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        var ci = plugin.moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
        var cb = plugin.moduleManager().get(com.mooncore.modules.customblock.CustomBlockManagerModule.class);
        var boss = plugin.moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
        var rp = plugin.services().get(com.mooncore.api.resourcepack.ResourcePackService.class).orElse(null);

        inv.setItem(10, StudioItems.btn(Material.NETHER_STAR, "<gold>Items",
                "<gray>creer, editer, texture, stats, capacites",
                "<dark_gray>" + (ci == null ? "module inactif" : ci.rawDefs().size() + " item(s)")));
        inv.setItem(12, StudioItems.btn(Material.CRAFTING_TABLE, "<yellow>Recettes",
                "<gray>editeur 3x3 visuel par item",
                "<dark_gray>clic = choisir un resultat"));
        inv.setItem(14, StudioItems.btn(Material.DEEPSLATE_DIAMOND_ORE, "<aqua>Blocs & minerais",
                "<gray>textures, drops, worldgen, test en monde",
                "<dark_gray>" + (cb == null ? "module inactif" : cb.rawDefs().size() + " bloc(s)")));
        inv.setItem(16, StudioItems.btn(Material.ZOMBIE_HEAD, "<green>Mobs",
                "<gray>creatures custom, spawn test, textures",
                "<dark_gray>base moteur boss/miniboss"));

        inv.setItem(28, StudioItems.btn(Material.WITHER_SKELETON_SKULL, "<red>Boss",
                "<gray>templates, spawn test, stats, textures",
                "<dark_gray>" + (boss == null ? "module inactif" : boss.bossIds().size() + " boss")));
        inv.setItem(30, StudioItems.btn(Material.PAINTING, "<light_purple>Textures",
                "<gray>hub peinture item/bloc/boss",
                "<dark_gray>item tenu, vanilla, IA"));
        inv.setItem(32, StudioItems.btn(Material.ENCHANTED_BOOK, "<gradient:#8a2be2:#c77dff>Assistant IA</gradient>",
                "<gray>decris ce que tu veux creer",
                "<dark_gray>ex: minerai lunaire avec texture"));
        inv.setItem(34, StudioItems.btn(Material.MAP, "<green>Resource pack",
                "<gray>rebuild, resend, URL, import vanilla",
                "<dark_gray>" + (rp == null ? "module inactif" : rp.url())));
        inv.setItem(45, StudioItems.btn(Material.COMPASS, "<white>Tableau de bord",
                "<gray>items: <white>" + (ci == null ? 0 : ci.rawDefs().size()),
                "<gray>blocs: <white>" + (cb == null ? 0 : cb.rawDefs().size()),
                "<gray>boss: <white>" + (boss == null ? 0 : boss.bossIds().size())));
        inv.setItem(49, StudioItems.btn(Material.BARRIER, "<red>Fermer"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        switch (slot) {
            case 10 -> StudioItemMenu.open(plugin, chat, p, 0);
            case 12 -> StudioRecipeMenu.open(plugin, chat, p, 0);
            case 14 -> StudioBlockMenu.open(plugin, chat, p, 0);
            case 16 -> StudioMobMenu.open(plugin, chat, p, 0);
            case 28 -> StudioBossMenu.open(plugin, chat, p, 0);
            case 30 -> StudioTextureMenu.open(plugin, chat, p);
            case 32 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Decris ce que Moon Studio doit creer :", in ->
                        p.performCommand("moon ai create " + in + " texture"));
            }
            case 34 -> StudioPackMenu.open(plugin, chat, p);
            case 45 -> { build(); p.sendActionBar(Text.mm("<gray>Studio actualise")); }
            case 49 -> p.closeInventory();
            default -> { }
        }
    }

    @Override
    public Inventory getInventory() { return inv; }
}
