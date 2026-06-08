package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Centralise les actions resource-pack utiles aux admins. */
public final class StudioPackMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private Inventory inv;

    private StudioPackMenu(MoonCore plugin, ChatInput chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p) {
        StudioPackMenu menu = new StudioPackMenu(plugin, chat);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>> Resource pack"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        ResourcePackService rp = plugin.services().get(ResourcePackService.class).orElse(null);
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        CustomBlockManagerModule cb = plugin.moduleManager().get(CustomBlockManagerModule.class);
        BossManagerModule boss = plugin.moduleManager().get(BossManagerModule.class);

        inv.setItem(4, StudioItems.btn(Material.MAP, "<green>Resource pack",
                "<gray>URL: <white>" + (rp == null ? "module inactif" : rp.url()),
                "<gray>items: <white>" + (ci == null ? 0 : ci.rawDefs().size())
                        + " <gray>blocs: <white>" + (cb == null ? 0 : cb.rawDefs().size())
                        + " <gray>boss: <white>" + (boss == null ? 0 : boss.bossIds().size())));

        inv.setItem(10, StudioItems.btn(Material.EMERALD_BLOCK, "<green>Rebuild + resend",
                "<gray>reconstruit le zip et le renvoie aux joueurs"));
        inv.setItem(11, StudioItems.btn(Material.CARTOGRAPHY_TABLE, "<green>Rebuild seulement",
                "<gray>utile avant de verifier l'URL"));
        inv.setItem(12, StudioItems.btn(Material.ENDER_EYE, "<aqua>Renvoyer seulement",
                "<gray>force les joueurs Java a recevoir le pack actuel"));
        inv.setItem(13, StudioItems.btn(Material.PAPER, "<yellow>Afficher URL",
                "<gray>envoie l'URL versionnee dans le chat"));
        inv.setItem(14, StudioItems.btn(Material.GRASS_BLOCK, "<aqua>Importer vanilla",
                "<gray>importe les textures du client/resource pack source"));
        inv.setItem(15, StudioItems.btn(Material.CLOCK, "<yellow>Reload definitions",
                "<gray>recharge items, blocs, boss depuis les fichiers"));
        inv.setItem(16, StudioItems.btn(Material.COMPASS, "<white>Actualiser"));

        inv.setItem(28, StudioItems.btn(Material.NETHER_STAR, "<gold>Items",
                "<gray>ouvrir les items custom"));
        inv.setItem(30, StudioItems.btn(Material.DEEPSLATE_DIAMOND_ORE, "<aqua>Blocs",
                "<gray>ouvrir blocs et minerais"));
        inv.setItem(32, StudioItems.btn(Material.WITHER_SKELETON_SKULL, "<red>Boss",
                "<gray>ouvrir les boss"));
        inv.setItem(34, StudioItems.btn(Material.PAINTING, "<light_purple>Textures",
                "<gray>ouvrir le hub textures"));
        inv.setItem(49, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        switch (slot) {
            case 10 -> StudioItems.rebuildAndResend(plugin, p);
            case 11 -> StudioItems.rebuild(plugin, p);
            case 12 -> StudioItems.resend(plugin, p);
            case 13 -> showUrl(p);
            case 14 -> {
                p.closeInventory();
                p.performCommand("moon item importvanilla");
                p.performCommand("moon block importvanilla");
            }
            case 15 -> reloadDefinitions(p);
            case 16 -> { build(); p.sendActionBar(Text.mm("<gray>Pack panel actualise")); }
            case 28 -> StudioItemMenu.open(plugin, chat, p, 0);
            case 30 -> StudioBlockMenu.open(plugin, chat, p, 0);
            case 32 -> StudioBossMenu.open(plugin, chat, p, 0);
            case 34 -> StudioTextureMenu.open(plugin, chat, p);
            case 49 -> StudioHubMenu.open(plugin, chat, p);
            default -> { }
        }
    }

    private void showUrl(Player p) {
        plugin.services().get(ResourcePackService.class).ifPresentOrElse(rp -> {
            p.sendMessage(Text.mm("<gradient:#8a2be2:#c77dff>Resource pack</gradient> <gray>URL versionnee :"));
            p.sendMessage(Text.mm("<white>" + rp.url()));
        }, () -> p.sendMessage(Text.mm("<red>Module resource-pack inactif.")));
    }

    private void reloadDefinitions(Player p) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        CustomBlockManagerModule cb = plugin.moduleManager().get(CustomBlockManagerModule.class);
        BossManagerModule boss = plugin.moduleManager().get(BossManagerModule.class);
        if (ci != null) ci.reloadModule();
        if (cb != null) cb.reloadModule();
        if (boss != null) boss.reloadModule();
        p.sendMessage(Text.mm("<green>Definitions rechargees depuis le disque."));
        build();
    }

    @Override
    public Inventory getInventory() { return inv; }
}
