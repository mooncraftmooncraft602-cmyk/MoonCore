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

/**
 * Menu de l'éditeur : palette paginée (4 pages) + rangée « outils couleur » (importer une
 * texture vanilla/existante, recoloriser, pipette monde, page, hex) + contrôles (brosse,
 * symétrie, suppr couleur, annuler/refaire, sauver, quitter). Clics routés par PaintListener.
 */
public final class PaintSettingsMenu implements InventoryHolder {

    private static final int PALETTE = 36; // 4 rangées de 9
    private static final int PAGES = 4;
    private static final String[] PAGE_NAMES = {"Vifs", "Pastels", "Terres & gris", "Nuancier"};
    private final PaintSession session;
    private final int[] paletteColors = new int[PALETTE];
    private int page = 0;
    private Inventory inv;

    private PaintSettingsMenu(PaintSession session) {
        this.session = session;
    }

    public static void open(PaintSession session) {
        Player p = session.player();
        if (p == null) return;
        PaintSettingsMenu m = new PaintSettingsMenu(session);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Palette & réglages</gradient>"));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        generatePage();
        for (int i = 0; i < PALETTE; i++) inv.setItem(i, swatch(paletteColors[i]));

        // Rangée outils couleur (36..44).
        inv.setItem(36, btn(Material.PAINTING, "<aqua>Importer une texture", "<gray>vanilla/existant (ex deepslate_diamond_ore)"));
        inv.setItem(37, btn(Material.INK_SAC, "<aqua>Recoloriser", "<gray>passe TOUTE la texture vers la couleur actuelle", "<dark_gray>(garde la forme + l'ombrage)"));
        inv.setItem(38, btn(Material.SPYGLASS, "<aqua>Pipette monde", "<gray>prendre la couleur d'un bloc autour"));
        inv.setItem(39, btn(Material.BOOKSHELF, "<aqua>Couleurs : " + PAGE_NAMES[page], "<dark_gray>clic = page suivante"));
        inv.setItem(40, btn(Material.NAME_TAG, "<aqua>Couleur hex…", "<gray>saisir #RRGGBB"));
        inv.setItem(41, btn(Material.NETHER_STAR, "<gradient:#8a2be2:#c77dff>Assistant avancé</gradient>",
                "<gray>formes, transformations, effets, IA"));
        inv.setItem(42, btn(Material.TARGET, "<aqua>Précision pixel",
                "<gray>coordonnées, nudges 1 px, peindre sans viser"));
        inv.setItem(43, btn(Material.SPYGLASS, "<aqua>Zoom toile : x" + session.zoom(),
                "<gray>grossit vraiment la zone visée",
                "<dark_gray>clic = zoom + · clic droit = zoom -"));
        inv.setItem(44, btn(session.cursorPinned() ? Material.REDSTONE_TORCH : Material.LEVER,
                "<aqua>Curseur : " + (session.cursorPinned() ? "<light_purple>verrouillé" : "<green>libre"),
                "<gray>clic = verrouiller/libérer",
                "<dark_gray>clic droit = recentrer la loupe"));

        // Contrôles (45..53).
        inv.setItem(45, btn(Material.BRUSH, "<yellow>Brosse : " + session.brush(), "<dark_gray>clic = +1"));
        inv.setItem(46, btn(Material.ITEM_FRAME, "<yellow>Symétrie : " + session.symmetry()));
        inv.setItem(47, btn(Material.TNT, "<red>Supprimer cette couleur", "<dark_gray>clic droit = tout effacer"));
        inv.setItem(48, btn(Material.COMPASS, "<yellow>Sensibilité curseur : "
                        + String.format(java.util.Locale.ROOT, "%.1f", session.sensitivity()),
                "<gray>bas = curseur lent et PRÉCIS",
                "<gray>haut = curseur rapide",
                "<dark_gray>les bords se prennent en visant à côté",
                "<dark_gray>clic gauche = +0,2 · clic droit = -0,2"));
        inv.setItem(49, currentColorItem());
        inv.setItem(50, btn(Material.RED_DYE, "<yellow>Annuler"));
        inv.setItem(51, btn(Material.LIME_DYE, "<yellow>Refaire"));
        inv.setItem(52, btn(Material.EMERALD_BLOCK, "<green>SAUVEGARDER"));
        inv.setItem(53, btn(Material.BARRIER, "<red>Quitter l'éditeur"));
    }

    /** Remplit {@link #paletteColors} (36 = 4 rangées) selon la page. */
    private void generatePage() {
        int idx = 0;
        switch (page) {
            case 1 -> { for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(c / 9f, 1f - r * 0.25f, 1f); }
            case 2 -> {
                for (int c = 0; c < 9; c++) paletteColors[idx++] = gray(c / 8f);
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(0.083f, 0.6f, 0.3f + c * 0.07f);
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(0.07f, 0.35f, 0.55f + c * 0.05f);
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(0.30f, 0.5f, 0.3f + c * 0.07f);
            }
            case 3 -> {
                float[] hsbv = rgbToHsb(session.color());
                float h = hsbv[0], sat = hsbv[1];
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(h, sat, 0.15f + c * 0.105f);
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(h, sat * (c / 8f), 0.5f + c * 0.06f);
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(wrap(h - 0.06f), sat, 0.2f + c * 0.095f);
                for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(wrap(h + 0.06f), sat, 0.2f + c * 0.095f);
            }
            default -> { for (int r = 0; r < 4; r++) for (int c = 0; c < 9; c++) paletteColors[idx++] = hsb(c / 9f, 1f, 1f - r * 0.22f); }
        }
    }

    public void click(Player p, int rawSlot, boolean rightClick) {
        if (rawSlot < 0 || rawSlot >= 54) return;
        if (rawSlot < PALETTE) {
            session.setColor(0xFF000000 | paletteColors[rawSlot]);
            p.sendActionBar(Text.mm("<aqua>Couleur #" + String.format("%06X", paletteColors[rawSlot])));
            rebuild();
            return;
        }
        switch (rawSlot) {
            case 36 -> { // importer une texture comme base
                p.closeInventory();
                var c = session.chat();
                if (c != null) c.request(p, "<yellow>Nom de la texture à importer (ex <white>deepslate_diamond_ore</white>) :",
                        in -> session.importBase(in));
            }
            case 37 -> session.recolorToCurrent();
            case 38 -> { p.closeInventory(); session.enterWorldPick(); }
            case 41 -> PaintAssistantMenu.open(session);
            case 42 -> PaintPrecisionMenu.open(session);
            case 43 -> { session.cycleZoom(rightClick); rebuild(); }
            case 44 -> {
                if (rightClick) session.focusViewOnCursor();
                else if (session.cursorPinned()) session.unpinCursor();
                else session.pinCursor(Math.max(0, session.cursorX()), Math.max(0, session.cursorY()));
                rebuild();
            }
            case 39 -> { page = (page + 1) % PAGES; rebuild(); }
            case 40 -> {
                p.closeInventory();
                var c = session.chat();
                if (c != null) c.request(p, "<yellow>Couleur hex (ex <white>#FF8800</white>) :", in -> {
                    Integer rgb = parseHex(in);
                    if (rgb == null) p.sendMessage(Text.mm("<red>Hex invalide : " + in));
                    else session.setColor(0xFF000000 | rgb);
                });
            }
            case 45 -> { session.setBrush(session.brush() % 4 + 1); rebuild(); }
            case 46 -> { session.cycleSymmetry(); rebuild(); }
            case 48 -> {
                session.setSensitivity(session.sensitivity() + (rightClick ? -0.2 : 0.2));
                p.sendActionBar(Text.mm("<aqua>Sensibilité curseur : "
                        + String.format(java.util.Locale.ROOT, "%.1f", session.sensitivity())));
                rebuild();
            }
            case 47 -> {
                if (rightClick) { session.clearCanvas(); p.sendActionBar(Text.mm("<red>Toile effacée")); }
                else { session.deleteCurrentColor(); p.sendActionBar(Text.mm("<red>Couleur supprimée partout")); }
            }
            case 50 -> session.undo();
            case 51 -> session.redo();
            case 52 -> { session.save(); p.closeInventory(); }
            case 53 -> { p.closeInventory(); session.exit(); }
            default -> { }
        }
    }

    private static Integer parseHex(String s) {
        String t = s.trim().replace("#", "");
        if (!t.matches("[0-9a-fA-F]{6}")) return null;
        return Integer.parseInt(t, 16);
    }

    private void rebuild() { build(); }

    private ItemStack currentColorItem() {
        ItemStack it = swatch(session.color() & 0xFFFFFF);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Text.mm("<aqua>Couleur actuelle #" + String.format("%06X", session.color() & 0xFFFFFF))
                    .decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(m);
        }
        return it;
    }

    private static float wrap(float h) { return (h % 1f + 1f) % 1f; }
    private static int hsb(float h, float s, float b) { return java.awt.Color.getHSBColor(h, clamp01(s), clamp01(b)).getRGB() & 0xFFFFFF; }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static int gray(float t) { int g = Math.round(clamp01(t) * 255); return (g << 16) | (g << 8) | g; }
    private static float[] rgbToHsb(int argb) {
        return java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
    }

    private static ItemStack swatch(int rgb) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(Color.fromRGB(rgb));
            lm.displayName(Text.mm("<white>#" + String.format("%06X", rgb)).decoration(TextDecoration.ITALIC, false));
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
