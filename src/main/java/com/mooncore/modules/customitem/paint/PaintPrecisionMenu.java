package com.mooncore.modules.customitem.paint;

import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/** Menu d'appoint pour choisir un pixel sans dépendre du regard sur la toile. */
public final class PaintPrecisionMenu implements InventoryHolder {

    private final PaintSession session;
    private Inventory inv;

    private PaintPrecisionMenu(PaintSession session) {
        this.session = session;
    }

    public static void open(PaintSession session) {
        Player p = session.player();
        if (p == null) return;
        if (session.cursorX() < 0 || session.cursorY() < 0) {
            session.pinCursor(session.canvas().size() / 2, session.canvas().size() / 2);
        }
        PaintPrecisionMenu m = new PaintPrecisionMenu(session);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Précision pixel</gradient>"));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        ItemStack pane = btn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        inv.setItem(4, currentPixel());

        inv.setItem(11, btn(Material.ARROW, "<yellow>↖ 1 px"));
        inv.setItem(13, btn(Material.ARROW, "<yellow>↑ 1 px"));
        inv.setItem(15, btn(Material.ARROW, "<yellow>↗ 1 px"));
        inv.setItem(20, btn(Material.ARROW, "<yellow>← 1 px"));
        inv.setItem(22, btn(Material.BRUSH, "<green>Peindre ici", "<gray>outil actuel sur le pixel verrouillé"));
        inv.setItem(24, btn(Material.ARROW, "<yellow>→ 1 px"));
        inv.setItem(29, btn(Material.ARROW, "<yellow>↙ 1 px"));
        inv.setItem(31, btn(Material.ARROW, "<yellow>↓ 1 px"));
        inv.setItem(33, btn(Material.ARROW, "<yellow>↘ 1 px"));

        inv.setItem(36, btn(Material.NAME_TAG, "<aqua>Coordonnées exactes", "<gray>ex : 7 12 ou 7,12"));
        inv.setItem(37, btn(session.cursorPinned() ? Material.REDSTONE_TORCH : Material.LEVER,
                "<aqua>Curseur : " + (session.cursorPinned() ? "<light_purple>verrouillé" : "<green>libre")));
        inv.setItem(38, btn(Material.SPYGLASS, "<aqua>Zoom : x" + session.zoom(),
                "<dark_gray>clic = zoom + · clic droit = zoom -"));
        inv.setItem(39, btn(Material.COMPASS, "<aqua>Centrer la loupe ici"));
        inv.setItem(40, btn(Material.FEATHER, "<green>Crayon ici"));
        inv.setItem(41, btn(Material.BONE, "<red>Gommer ici"));
        inv.setItem(42, btn(Material.BUCKET, "<aqua>Remplir ici"));
        inv.setItem(43, btn(Material.GLASS_BOTTLE, "<aqua>Pipette ici"));

        inv.setItem(49, btn(Material.OAK_DOOR, "<yellow>← Retour palette/réglages"));
        inv.setItem(53, btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int slot, boolean rightClick) {
        switch (slot) {
            case 11 -> nudge(-1, -1);
            case 13 -> nudge(0, -1);
            case 15 -> nudge(1, -1);
            case 20 -> nudge(-1, 0);
            case 22 -> { session.applyToolAtCursor(); rebuild(); }
            case 24 -> nudge(1, 0);
            case 29 -> nudge(-1, 1);
            case 31 -> nudge(0, 1);
            case 33 -> nudge(1, 1);
            case 36 -> {
                p.closeInventory();
                var c = session.chat();
                if (c != null) c.request(p, "<yellow>Pixel exact (x y, ex <white>7 12</white>) :", in -> {
                    int[] xy = parseCoords(in);
                    if (xy == null) p.sendMessage(Text.mm("<red>Coordonnées invalides : " + in));
                    else session.pinCursor(xy[0], xy[1]);
                    Player live = session.player();
                    if (live != null && live.isOnline()) open(session);
                });
            }
            case 37 -> { if (session.cursorPinned()) session.unpinCursor(); else session.pinCursor(session.cursorX(), session.cursorY()); rebuild(); }
            case 38 -> { session.cycleZoom(rightClick); rebuild(); }
            case 39 -> { session.focusViewOnCursor(); rebuild(); }
            case 40 -> { session.setTool(PaintSession.Tool.PENCIL, true); session.applyToolAtCursor(); rebuild(); }
            case 41 -> { session.setTool(PaintSession.Tool.ERASER, true); session.applyToolAtCursor(); rebuild(); }
            case 42 -> { session.setTool(PaintSession.Tool.FILL, true); session.applyToolAtCursor(); rebuild(); }
            case 43 -> {
                int c = session.canvas().get(session.cursorX(), session.cursorY());
                if ((c >>> 24) != 0) session.setColor(c);
                rebuild();
            }
            case 49 -> PaintSettingsMenu.open(session);
            case 53 -> p.closeInventory();
            default -> { }
        }
    }

    private void nudge(int dx, int dy) {
        session.nudgeCursor(dx, dy);
        rebuild();
    }

    private void rebuild() { build(); }

    private ItemStack currentPixel() {
        int x = Math.max(0, session.cursorX()), y = Math.max(0, session.cursorY());
        int sampled = session.canvas().get(x, y);
        int rgb = (sampled >>> 24) == 0 ? session.color() & 0xFFFFFF : sampled & 0xFFFFFF;
        return swatch(rgb, "<aqua>Pixel <white>" + x + "," + y,
                "<gray>couleur : " + (((sampled >>> 24) == 0) ? "transparent" : "#" + String.format("%06X", rgb)),
                "<gray>zoom x" + session.zoom());
    }

    private static int[] parseCoords(String in) {
        String[] parts = in.trim().replace(',', ' ').split("\\s+");
        if (parts.length < 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ItemStack swatch(int rgb, String name, String... lore) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(Color.fromRGB(rgb & 0xFFFFFF));
            lm.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            applyLore(lm, lore);
            lm.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(lm);
        }
        return it;
    }

    private static ItemStack btn(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            applyLore(meta, lore);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    private static void applyLore(ItemMeta meta, String[] lore) {
        if (lore.length == 0) return;
        java.util.List<net.kyori.adventure.text.Component> l = new java.util.ArrayList<>();
        for (String s : lore) l.add(Text.mm(s).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
