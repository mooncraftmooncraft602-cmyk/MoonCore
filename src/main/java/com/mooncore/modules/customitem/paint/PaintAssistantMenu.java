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
 * Assistant avancé de création de texture : outils formes (rectangle/ellipse/dégradé),
 * transformations (miroir/rotation/décalage/centrage), effets 1-clic (contour, ombrage
 * auto, éclaircir/assombrir, nettoyage, réduction de palette, auto-amélioration) et
 * génération IA directement dans la toile. Clics routés par {@link PaintListener}.
 */
public final class PaintAssistantMenu implements InventoryHolder {

    private final PaintSession session;
    private Inventory inv;

    private PaintAssistantMenu(PaintSession session) { this.session = session; }

    public static void open(PaintSession session) {
        Player p = session.player();
        if (p == null) return;
        PaintAssistantMenu m = new PaintAssistantMenu(session);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Assistant texture avancé</gradient>"));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        ItemStack pane = btn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        // Ligne 1 : formes (clic = sélectionne l'outil, ferme le menu, dessine en 2 clics).
        inv.setItem(0, btn(Material.STONE, "<aqua>Rectangle plein", "<dark_gray>2 clics : 2 coins"));
        inv.setItem(1, btn(Material.STONE_BRICKS, "<aqua>Rectangle contour", "<dark_gray>2 clics : 2 coins"));
        inv.setItem(2, btn(Material.CLAY_BALL, "<aqua>Ellipse pleine", "<dark_gray>2 clics : boîte"));
        inv.setItem(3, btn(Material.BRICK, "<aqua>Ellipse contour", "<dark_gray>2 clics : boîte"));
        inv.setItem(4, btn(Material.STICK, "<aqua>Ligne", "<dark_gray>2 clics"));
        inv.setItem(5, btn(Material.FIREWORK_STAR, "<aqua>Dégradé", "<gray>couleur actuelle → couleur 2",
                "<dark_gray>2 clics : sens du dégradé"));
        inv.setItem(6, swatch(session.secondaryColor(), "<aqua>Couleur 2 (dégradé) : #"
                + String.format("%06X", session.secondaryColor() & 0xFFFFFF), "<dark_gray>clic = prendre la couleur actuelle"));
        inv.setItem(8, btn(Material.CHEST, "<gold>Modèles & tampons", "<gray>bases d'objet + formes prêtes"));
        inv.setItem(7, btn(Material.MAGMA_CREAM, "<gold>🎞 Animer la texture", "<gray>pulsation, arc-en-ciel, scintillement…"));

        // Ligne 2 : couleurs (teinte/saturation/inversion/bruit/symétrie/fond).
        inv.setItem(9, btn(Material.QUARTZ, "<light_purple>Inverser les couleurs"));
        inv.setItem(10, btn(Material.CYAN_DYE, "<light_purple>Teinte -30°"));
        inv.setItem(11, btn(Material.MAGENTA_DYE, "<light_purple>Teinte +30°"));
        inv.setItem(12, btn(Material.LIME_DYE, "<light_purple>Saturation +"));
        inv.setItem(13, btn(Material.GRAY_DYE, "<light_purple>Saturation -"));
        inv.setItem(14, btn(Material.GUNPOWDER, "<light_purple>Bruit / grain", "<gray>idéal pour les blocs"));
        inv.setItem(15, btn(Material.NAME_TAG, "<light_purple>Symétriser ↔", "<gray>copie la moitié gauche à droite"));
        inv.setItem(16, btn(Material.BUCKET, "<light_purple>Remplir le fond", "<gray>couleur actuelle dans le vide"));

        // Ligne 3 : transformations.
        inv.setItem(18, btn(Material.NAME_TAG, "<yellow>Miroir ↔ horizontal"));
        inv.setItem(19, btn(Material.NAME_TAG, "<yellow>Miroir ↕ vertical"));
        inv.setItem(20, btn(Material.CLOCK, "<yellow>Rotation 90°"));
        inv.setItem(21, btn(Material.ARROW, "<yellow>Décaler ↑"));
        inv.setItem(22, btn(Material.ARROW, "<yellow>Décaler ↓"));
        inv.setItem(23, btn(Material.ARROW, "<yellow>Décaler ←"));
        inv.setItem(24, btn(Material.ARROW, "<yellow>Décaler →"));
        inv.setItem(25, btn(Material.STRUCTURE_VOID, "<yellow>Centrer le dessin"));

        // Ligne 4 : effets.
        inv.setItem(27, btn(Material.BLACK_DYE, "<light_purple>Contour foncé", "<gray>cerne les amas opaques"));
        inv.setItem(28, btn(Material.SUNFLOWER, "<light_purple>Ombrage auto", "<gray>biseau lumière/ombre"));
        inv.setItem(29, btn(Material.GLOWSTONE_DUST, "<light_purple>Éclaircir"));
        inv.setItem(30, btn(Material.COAL, "<light_purple>Assombrir"));
        inv.setItem(31, btn(Material.WATER_BUCKET, "<light_purple>Nettoyer", "<gray>retire les pixels isolés"));
        inv.setItem(32, btn(Material.MAP, "<light_purple>Réduire la palette", "<gray>rendu plus pixel-art"));
        inv.setItem(33, btn(Material.NETHER_STAR, "<gold>Auto-amélioration", "<gray>nettoyage + centrage + ombrage + contour"));

        // Ligne 6 : IA + navigation.
        inv.setItem(45, btn(Material.PAINTING, "<gradient:#8a2be2:#c77dff>Générer avec l'IA</gradient>",
                "<gray>décris l'objet → texture dans la toile", "<dark_gray>(retouchable ensuite)"));
        inv.setItem(49, btn(Material.OAK_DOOR, "<yellow>← Retour palette/réglages"));
        inv.setItem(53, btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int slot) {
        switch (slot) {
            case 0 -> selectShape(p, PaintSession.Tool.RECT, true, "Rectangle plein");
            case 1 -> selectShape(p, PaintSession.Tool.RECT, false, "Rectangle contour");
            case 2 -> selectShape(p, PaintSession.Tool.ELLIPSE, true, "Ellipse pleine");
            case 3 -> selectShape(p, PaintSession.Tool.ELLIPSE, false, "Ellipse contour");
            case 4 -> selectShape(p, PaintSession.Tool.LINE, true, "Ligne");
            case 5 -> selectShape(p, PaintSession.Tool.GRADIENT, true, "Dégradé");
            case 6 -> { session.setSecondaryColor(session.color()); rebuild();
                p.sendActionBar(Text.mm("<aqua>Couleur 2 = #" + String.format("%06X", session.color() & 0xFFFFFF))); }
            case 8 -> PaintTemplatesMenu.open(session);
            case 7 -> AnimationMenu.open(session);
            case 9 -> { session.opInvert(); feedback(p, "Couleurs inversées"); }
            case 10 -> { session.opHueShift(-30); feedback(p, "Teinte -30°"); }
            case 11 -> { session.opHueShift(30); feedback(p, "Teinte +30°"); }
            case 12 -> { session.opSaturation(1.25); feedback(p, "Saturation +"); }
            case 13 -> { session.opSaturation(0.8); feedback(p, "Saturation -"); }
            case 14 -> { session.opNoise(); feedback(p, "Grain ajouté"); }
            case 15 -> { session.opSymmetrize(true); feedback(p, "Symétrisé ↔"); }
            case 16 -> { session.opFillBackground(); feedback(p, "Fond rempli"); }
            case 18 -> { session.opFlipH(); feedback(p, "Miroir horizontal"); }
            case 19 -> { session.opFlipV(); feedback(p, "Miroir vertical"); }
            case 20 -> { session.opRotate(); feedback(p, "Rotation 90°"); }
            case 21 -> { session.opNudge(0, -1); feedback(p, "Décalé ↑"); }
            case 22 -> { session.opNudge(0, 1); feedback(p, "Décalé ↓"); }
            case 23 -> { session.opNudge(-1, 0); feedback(p, "Décalé ←"); }
            case 24 -> { session.opNudge(1, 0); feedback(p, "Décalé →"); }
            case 25 -> { session.opCenter(); feedback(p, "Centré"); }
            case 27 -> { session.opOutline(); feedback(p, "Contour ajouté"); }
            case 28 -> { session.opAutoShade(); feedback(p, "Ombrage auto"); }
            case 29 -> { session.opBrightness(1.15); feedback(p, "Éclairci"); }
            case 30 -> { session.opBrightness(0.87); feedback(p, "Assombri"); }
            case 31 -> { session.opCleanup(); feedback(p, "Nettoyé"); }
            case 32 -> { session.opPosterize(6); feedback(p, "Palette réduite"); }
            case 33 -> session.opEnhance();
            case 45 -> {
                p.closeInventory();
                var c = session.chat();
                if (c != null) c.request(p, "<yellow>Décris la texture à générer (ex <white>épée de feu</white>) :",
                        in -> session.generateFromAi(in));
            }
            case 49 -> PaintSettingsMenu.open(session);
            case 53 -> p.closeInventory();
            default -> { }
        }
    }

    private void selectShape(Player p, PaintSession.Tool tool, boolean filled, String label) {
        session.setTool(tool, filled);
        p.closeInventory();
        p.sendActionBar(Text.mm("<aqua>" + label + " sélectionné — clique sur la toile (2 clics)"));
    }

    private void feedback(Player p, String msg) { p.sendActionBar(Text.mm("<green>" + msg)); }

    private void rebuild() { build(); }

    private static ItemStack swatch(int rgb, String name, String... lore) {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) {
            lm.setColor(org.bukkit.Color.fromRGB(rgb & 0xFFFFFF));
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
