package com.mooncore.modules.customitem.paint;

import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Assistant d'ANIMATION : transforme la texture actuelle en texture animée (bande de
 * frames + .png.mcmeta) en un clic, selon un style procédural. Nombre d'images et vitesse
 * réglables. Routé par {@link PaintListener}.
 */
public final class AnimationMenu implements InventoryHolder {

    private static final int[] FRAMES = {4, 6, 8, 12, 16, 24};
    private static final int[] SPEEDS = {1, 2, 3, 5, 8};

    private final PaintSession session;
    private int frames = 8;
    private int frametime = 2;
    private Inventory inv;

    private AnimationMenu(PaintSession session) { this.session = session; }

    public static void open(PaintSession session) {
        Player p = session.player();
        if (p == null) return;
        AnimationMenu m = new AnimationMenu(session);
        m.inv = Bukkit.createInventory(m, 45, Text.mm("<gradient:#8a2be2:#c77dff>Animation de texture</gradient>"));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        ItemStack pane = btn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, pane);

        // Styles (clic = appliquer puis fermer).
        inv.setItem(10, btn(Material.REDSTONE, "<aqua>Pulsation", "<gray>la texture « respire » (clair/foncé)"));
        inv.setItem(11, btn(Material.FIREWORK_STAR, "<aqua>Arc-en-ciel", "<gray>la teinte défile sur tout le spectre"));
        inv.setItem(12, btn(Material.GLOWSTONE_DUST, "<aqua>Scintillement", "<gray>luminosité aléatoire (étincelle)"));
        inv.setItem(13, btn(Material.PAPER, "<aqua>Défilement", "<gray>l'image défile en boucle"));
        inv.setItem(14, btn(Material.GLOWSTONE, "<aqua>Lueur", "<gray>monte puis redescend en luminosité"));
        inv.setItem(15, btn(Material.PISTON, "<aqua>Secousse", "<gray>petites vibrations"));

        inv.setItem(29, btn(Material.ITEM_FRAME, "<yellow>Images : <white>" + frames, "<dark_gray>clic = changer"));
        inv.setItem(31, btn(Material.CLOCK, "<yellow>Vitesse : <white>" + frametime + " ticks/img",
                "<gray>plus bas = plus rapide", "<dark_gray>clic = changer"));
        inv.setItem(33, btn(Material.BOOK, "<gray>Astuce", "<gray>dessine d'abord ta texture,",
                "<gray>puis choisis un style ci-dessus."));

        inv.setItem(39, btn(Material.OAK_DOOR, "<yellow>← Retour"));
        inv.setItem(44, btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int slot, boolean right) {
        switch (slot) {
            case 10 -> apply(p, "pulse");
            case 11 -> apply(p, "rainbow");
            case 12 -> apply(p, "flicker");
            case 13 -> apply(p, "scroll");
            case 14 -> apply(p, "glow");
            case 15 -> apply(p, "shake");
            case 29 -> { frames = cycle(FRAMES, frames, right); rebuild(); }
            case 31 -> { frametime = cycle(SPEEDS, frametime, right); rebuild(); }
            case 39 -> PaintAssistantMenu.open(session);
            case 44 -> p.closeInventory();
            default -> { }
        }
    }

    private void apply(Player p, String style) {
        session.applyAnimation(style, frames, frametime);
        p.closeInventory();
    }

    private static int cycle(int[] arr, int cur, boolean back) {
        int idx = 0;
        for (int i = 0; i < arr.length; i++) if (arr[i] == cur) { idx = i; break; }
        idx = (idx + (back ? -1 : 1) + arr.length) % arr.length;
        return arr[idx];
    }

    private void rebuild() { build(); }

    private static ItemStack btn(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                java.util.List<net.kyori.adventure.text.Component> l = new java.util.ArrayList<>();
                for (String s : lore) l.add(Text.mm(s).decoration(TextDecoration.ITALIC, false));
                meta.lore(l);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public Inventory getInventory() { return inv; }
}
