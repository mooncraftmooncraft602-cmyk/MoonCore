package com.mooncore.modules.customitem;

import com.mooncore.util.ImageUtil;
import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Éditeur de texture pixel-art en jeu. Toile 16×16, palette de 16 couleurs (teintures),
 * affichée dans une fenêtre coffre 9×5 avec défilement. À la sauvegarde, génère un PNG,
 * l'assigne à l'objet ciblé et reconstruit/repousse le resource pack forcé.
 * Compatible Bedrock (coffre traduit par Geyser).
 */
public final class PixelEditor implements InventoryHolder {

    private static final int CANVAS = 16;       // 16×16
    private static final int VIEW_W = 9, VIEW_H = 5;
    private static final int MAX_OFF_X = CANVAS - VIEW_W; // 7
    private static final int MAX_OFF_Y = CANVAS - VIEW_H; // 11
    private static final DyeColor[] PALETTE = DyeColor.values(); // 16 couleurs

    private final CustomItemManagerModule module;
    private final String itemId;
    private final int[][] canvas = new int[CANVAS][CANVAS]; // -1 = transparent, sinon ordinal DyeColor
    private int offsetX = 0, offsetY = 0;
    private int currentColor = 0; // index palette, -1 = gomme
    private Inventory inventory;

    private PixelEditor(CustomItemManagerModule module, String itemId) {
        this.module = module;
        this.itemId = itemId;
        for (int[] row : canvas) java.util.Arrays.fill(row, -1);
    }

    public static void open(CustomItemManagerModule module, Player p, String itemId) {
        PixelEditor ed = new PixelEditor(module, itemId);
        ed.inventory = Bukkit.createInventory(ed, 54,
                Text.mm("<gradient:#8a2be2:#c77dff>Éditeur texture</gradient> <dark_gray>» <white>" + itemId));
        ed.render();
        p.openInventory(ed.inventory);
    }

    private void render() {
        // Viewport (toile).
        for (int i = 0; i < VIEW_W * VIEW_H; i++) {
            int col = i % VIEW_W, row = i / VIEW_W;
            int cx = offsetX + col, cy = offsetY + row;
            int v = canvas[cy][cx];
            inventory.setItem(i, cell(v, cx, cy));
        }
        // Barre d'outils (ligne 45..53).
        inventory.setItem(45, tool(Material.SPECTRAL_ARROW, "<yellow>◀ Gauche"));
        inventory.setItem(46, tool(Material.SPECTRAL_ARROW, "<yellow>▲ Haut"));
        inventory.setItem(47, tool(Material.SPECTRAL_ARROW, "<yellow>▼ Bas"));
        inventory.setItem(48, tool(Material.SPECTRAL_ARROW, "<yellow>▶ Droite"));
        inventory.setItem(49, currentColor >= 0
                ? named(pane(PALETTE[currentColor]), "<white>Couleur : " + PALETTE[currentColor].name() + " <gray>(clic = suivante)")
                : named(new ItemStack(Material.BARRIER), "<white>Gomme active <gray>(clic = couleur)"));
        inventory.setItem(50, tool(Material.BARRIER, "<red>Gomme (transparent)"));
        inventory.setItem(51, tool(Material.BUCKET, "<red>Tout effacer"));
        inventory.setItem(52, tool(Material.LIME_CONCRETE, "<green>SAUVEGARDER → applique la texture"));
        inventory.setItem(53, tool(Material.RED_CONCRETE, "<red>Fermer"));
    }

    /** Gère un clic (renvoie true si le menu doit rester ouvert). */
    public void onClick(Player p, int rawSlot, boolean rightClick) {
        if (rawSlot < 0 || rawSlot >= 54) return;
        if (rawSlot < VIEW_W * VIEW_H) {
            int col = rawSlot % VIEW_W, row = rawSlot / VIEW_W;
            int cx = offsetX + col, cy = offsetY + row;
            canvas[cy][cx] = rightClick ? -1 : currentColor;
            inventory.setItem(rawSlot, cell(canvas[cy][cx], cx, cy));
            return;
        }
        switch (rawSlot) {
            case 45 -> { offsetX = Math.max(0, offsetX - 1); render(); }
            case 46 -> { offsetY = Math.max(0, offsetY - 1); render(); }
            case 47 -> { offsetY = Math.min(MAX_OFF_Y, offsetY + 1); render(); }
            case 48 -> { offsetX = Math.min(MAX_OFF_X, offsetX + 1); render(); }
            case 49 -> { currentColor = (currentColor + 1) % PALETTE.length; render(); }
            case 50 -> { currentColor = -1; render(); }
            case 51 -> { for (int[] r : canvas) java.util.Arrays.fill(r, -1); render(); }
            case 52 -> save(p);
            case 53 -> p.closeInventory();
            default -> { }
        }
    }

    private void save(Player p) {
        CustomItemDef def = module.rawDef(itemId);
        if (def == null) { p.sendMessage(Text.mm("<red>Objet introuvable : " + itemId)); p.closeInventory(); return; }
        int[][] argb = new int[CANVAS][CANVAS];
        for (int y = 0; y < CANVAS; y++) {
            for (int x = 0; x < CANVAS; x++) {
                int v = canvas[y][x];
                argb[y][x] = (v < 0) ? 0 : (0xFF000000 | PALETTE[v].getColor().asRGB());
            }
        }
        try {
            byte[] png = ImageUtil.fromArgbGrid(argb);
            java.io.File out = new java.io.File(module.texturesFolder(), itemId + ".png");
            java.nio.file.Files.write(out.toPath(), png);
            def.setModelKey(itemId);
            if (def.customModelData() <= 0) def.setCustomModelData(module.nextCustomModelData());
            module.put(def);
            module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class)
                    .ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
            p.sendMessage(Text.mm("<green>✔ Texture enregistrée et appliquée à <white>" + itemId
                    + "<green> (cmd " + def.customModelData() + "). Pack mis à jour."));
        } catch (Exception e) {
            p.sendMessage(Text.mm("<red>Échec sauvegarde : " + e.getMessage()));
        }
        p.closeInventory();
    }

    private ItemStack cell(int v, int cx, int cy) {
        ItemStack it = (v < 0) ? new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE) : pane(PALETTE[v]);
        return named(it, "<dark_gray>(" + cx + "," + cy + ") " + (v < 0 ? "<gray>vide" : "<white>" + PALETTE[v].name()));
    }

    private static ItemStack pane(DyeColor c) {
        Material m = Material.matchMaterial(c.name() + "_STAINED_GLASS_PANE");
        return new ItemStack(m != null ? m : Material.WHITE_STAINED_GLASS_PANE);
    }

    private static ItemStack tool(Material m, String name) {
        return named(new ItemStack(m), name);
    }

    private static ItemStack named(ItemStack it, String name) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
