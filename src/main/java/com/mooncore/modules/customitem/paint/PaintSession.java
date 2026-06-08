package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import com.mooncore.util.ImageUtil;
import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Session d'édition d'un joueur : map + item frame fantôme comme toile, outils en
 * hotbar, dessin au regard. Source de vérité = {@link PixelCanvas}. Sauvegarde le PNG
 * et reconstruit le pack uniquement sur action explicite.
 */
public final class PaintSession {

    public enum Tool { PENCIL, ERASER, FILL, EYEDROPPER, LINE, NONE }

    private final MoonCore plugin;
    private final PaintTarget target;
    private final PaintManager manager;
    private final UUID owner;
    private final PixelCanvas canvas;

    private MapView mapView;
    private ItemFrame frame;
    private ItemStack[] savedInv;
    private BukkitTask cursorTask;

    private Tool currentTool = Tool.PENCIL;
    private int currentColor = 0xFF000000; // noir opaque
    private int brushSize = 1;
    private PixelCanvas.Symmetry symmetry = PixelCanvas.Symmetry.NONE;
    private int cursorX = -1, cursorY = -1;
    private int[] lineAnchor = null;
    private int lastDrawX = -1, lastDrawY = -1;   // pour le trait continu (drag)
    private long lastDrawMs = 0;
    private boolean flipU = false;
    private double sensitivity = 1.0;      // gain regard→toile (réglable dans le livre)
    private volatile boolean dirty = true; // déclenche un renvoi de la map
    private boolean worldPick = false;     // pipette « monde » : viser un bloc autour
    private Runnable onClose; // exécuté à la fermeture (ex. rouvrir le menu d'édition)

    public PaintSession(MoonCore plugin, PaintTarget target, PaintManager manager,
                        Player owner, int size) {
        this(plugin, target, manager, owner, size, null);
    }

    /** {@code sourceTexture} : texture de départ à importer (ex. copier un item/bloc existant). */
    public PaintSession(MoonCore plugin, PaintTarget target, PaintManager manager,
                        Player owner, int size, java.io.File sourceTexture) {
        this.plugin = plugin;
        this.target = target;
        this.manager = manager;
        this.owner = owner.getUniqueId();
        this.canvas = new PixelCanvas(size);
        // Sensibilité : valeur retenue pour ce joueur, sinon défaut config du module.
        this.sensitivity = clampSensitivity(manager.sensitivity(this.owner, defaultSensitivity()));
        // Priorité à la texture importée si fournie, sinon la texture propre de la cible.
        java.io.File base = (sourceTexture != null && sourceTexture.isFile()) ? sourceTexture : target.textureFile();
        loadFrom(base);
    }

    private void loadFrom(java.io.File png) {
        try {
            if (png != null && png.isFile()) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png);
                if (img != null) {
                    int n = canvas.size();
                    int[][] grid = new int[n][n];
                    for (int y = 0; y < n; y++)
                        for (int x = 0; x < n; x++) {
                            int sx = x * img.getWidth() / n, sy = y * img.getHeight() / n;
                            grid[y][x] = img.getRGB(Math.min(sx, img.getWidth() - 1), Math.min(sy, img.getHeight() - 1));
                        }
                    canvas.load(grid);
                }
            }
        } catch (Exception ignored) { }
    }

    // ---- Cycle de vie ----

    public boolean start() {
        Player p = player();
        if (p == null) return false;

        this.mapView = plugin.getServer().createMap(p.getWorld());
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        try { mapView.setLocked(true); } catch (Throwable ignored) { }
        mapView.addRenderer(new MapCanvasRenderer(this));

        if (!spawnFrame(p)) return false;

        // Sauvegarde + remplacement de l'inventaire par les outils.
        this.savedInv = p.getInventory().getContents();
        p.getInventory().clear();
        giveTools(p);
        p.getInventory().setHeldItemSlot(0);

        // Boucle de rendu : suit le regard et renvoie la map (rafraîchissement rapide).
        this.cursorTask = plugin.schedulers().syncTimer(this::tickRender, 1L, 1L);
        p.sendMessage(Text.mm("<green>Éditeur ouvert.</green> <gray>Vise la toile, <white>clic gauche</white> = dessiner ; "
                + "pipette : <white>clic droit</white> = prendre la couleur d'un bloc autour ; "
                + "<white>clic droit sur la palette</white> = couleurs/réglages."));
        return true;
    }

    /** (Re)pose l'item frame fantôme (toile) devant le joueur. */
    private boolean spawnFrame(Player p) {
        BlockFace playerFace = yawToFace(p.getLocation().getYaw());
        BlockFace frameFacing = playerFace.getOppositeFace();
        Location base = p.getEyeLocation().getBlock().getLocation()
                .add(playerFace.getModX() * 2.0, 0, playerFace.getModZ() * 2.0).add(0.5, 0, 0.5);

        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta mm = (MapMeta) mapItem.getItemMeta();
        mm.setMapView(mapView);
        mapItem.setItemMeta(mm);
        try {
            this.frame = p.getWorld().spawn(base, ItemFrame.class, f -> {
                f.setFacingDirection(frameFacing, true);
                f.setVisible(false);
                f.setFixed(true);
                f.setItem(mapItem);
            });
        } catch (Throwable t) {
            return false;
        }
        dirty = true;
        return frame != null && frame.isValid();
    }

    public void setOnClose(Runnable r) { this.onClose = r; }

    public void close() {
        if (cursorTask != null) cursorTask.cancel();
        Player p = player();
        if (frame != null && frame.isValid()) frame.remove();
        if (mapView != null) {
            // Casse la chaîne renderer→session→canvas (40 snapshots) pour permettre le GC.
            mapView.getRenderers().forEach(mapView::removeRenderer);
            mapView = null;
        }
        if (p != null && savedInv != null) {
            p.getInventory().setContents(savedInv);
            p.updateInventory();
        }
        savedInv = null;
        if (onClose != null) {
            Runnable r = onClose; onClose = null;
            try { r.run(); } catch (Exception ignored) { }
        }
    }

    // ---- Outils / hotbar ----

    private void giveTools(Player p) {
        var inv = p.getInventory();
        inv.setItem(0, tool(Material.FEATHER, "<yellow>Crayon"));
        inv.setItem(1, tool(Material.BONE, "<yellow>Gomme"));
        inv.setItem(2, tool(Material.BUCKET, "<yellow>Pot de peinture"));
        inv.setItem(3, tool(Material.GLASS_BOTTLE, "<yellow>Pipette <gray>(G: toile · D: bloc du monde)"));
        inv.setItem(4, tool(Material.STICK, "<yellow>Ligne <gray>(2 clics)"));
        inv.setItem(5, colorItem());
        inv.setItem(6, tool(Material.RED_DYE, "<yellow>Annuler <gray>(clic droit)"));
        inv.setItem(7, tool(Material.LIME_DYE, "<yellow>Refaire <gray>(clic droit)"));
        inv.setItem(8, tool(Material.WRITABLE_BOOK, "<gold>Réglages / Sauver <gray>(clic droit)"));
    }

    public void refreshColorItem() {
        Player p = player();
        if (p != null) p.getInventory().setItem(5, colorItem());
    }

    private ItemStack colorItem() {
        ItemStack it = new ItemStack(Material.LEATHER_HELMET);
        if (it.getItemMeta() instanceof LeatherArmorMeta lm) {
            lm.setColor(org.bukkit.Color.fromRGB(currentColor & 0xFFFFFF));
            lm.displayName(Text.mm("<aqua>Couleur <gray>#" + String.format("%06X", currentColor & 0xFFFFFF)
                    + " <dark_gray>(clic droit = palette)").decoration(TextDecoration.ITALIC, false));
            lm.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(lm);
        }
        return it;
    }

    private static ItemStack tool(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            it.setItemMeta(meta);
        }
        return it;
    }

    public void onSlotChange(int slot) {
        currentTool = switch (slot) {
            case 0 -> Tool.PENCIL;
            case 1 -> Tool.ERASER;
            case 2 -> Tool.FILL;
            case 3 -> Tool.EYEDROPPER;
            case 4 -> Tool.LINE;
            default -> Tool.NONE;
        };
        if (slot != 4) lineAnchor = null;
    }

    /** Clic gauche (swing) = applique l'outil ; en mode pipette-monde, prélève la couleur du bloc visé. */
    public void onSwing() {
        Player p = player();
        if (p == null) return;
        if (worldPick) { pickFromWorld(p); return; }
        int[] t = aim(p);
        if (t == null) {
            // Pas de visée valide (regard franchement ailleurs) → on peint quand même le
            // dernier pixel survolé, pour qu'un clic ne soit jamais « perdu ».
            if (cursorX < 0 || cursorY < 0) return;
            t = new int[]{cursorX, cursorY};
        }
        cursorX = t[0]; cursorY = t[1];
        int x = t[0], y = t[1];
        switch (currentTool) {
            case PENCIL -> drawStroke(x, y, currentColor);
            case ERASER -> drawStroke(x, y, 0);
            case FILL -> { canvas.pushHistory(); canvas.fill(x, y, currentColor); }
            case EYEDROPPER -> { int c = canvas.get(x, y); if ((c >>> 24) != 0) { currentColor = c; refreshColorItem(); } }
            case LINE -> {
                if (lineAnchor == null) {
                    lineAnchor = new int[]{x, y};
                    p.sendActionBar(Text.mm("<yellow>Point de départ posé — clique le point d'arrivée"));
                } else {
                    canvas.pushHistory();
                    canvas.line(lineAnchor[0], lineAnchor[1], x, y, currentColor, brushSize, symmetry);
                    lineAnchor = null;
                }
            }
            default -> { }
        }
        dirty = true; // feedback immédiat
    }

    /** Clic droit = action selon le slot tenu (palette, undo, redo, réglages). */
    public void onRightClick(int slot) {
        Player p = player();
        if (p == null) return;
        switch (slot) {
            case 3 -> enterWorldPick(); // pipette : clic droit = prélever sur un bloc du monde
            case 5 -> PaintSettingsMenu.open(this);
            case 6 -> { if (canvas.undo()) p.sendActionBar(Text.mm("<gray>Annulé")); }
            case 7 -> { if (canvas.redo()) p.sendActionBar(Text.mm("<gray>Refait")); }
            case 8 -> PaintSettingsMenu.open(this);
            default -> { }
        }
    }

    /**
     * Pose un point ; si le dernier point du même trait est récent, relie par une ligne
     * (drag continu sans trous). Un snapshot d'historique est pris au DÉBUT de chaque trait.
     */
    private void drawStroke(int x, int y, int color) {
        long now = System.currentTimeMillis();
        boolean continuation = (now - lastDrawMs <= 300) && lastDrawX >= 0;
        if (!continuation) canvas.pushHistory(); // nouveau trait
        if (continuation) canvas.line(lastDrawX, lastDrawY, x, y, color, brushSize, symmetry);
        else canvas.brush(x, y, color, brushSize, symmetry);
        lastDrawX = x; lastDrawY = y; lastDrawMs = now;
    }

    private void tickRender() {
        Player p = player();
        if (p == null) return;
        int[] t = aim(p);
        if (t != null && (t[0] != cursorX || t[1] != cursorY)) {
            cursorX = t[0]; cursorY = t[1];
            dirty = true;
        }
        if (dirty) {
            try { p.sendMap(mapView); } catch (Throwable ignored) { }
            dirty = false;
        }
    }

    public void markDirty() { dirty = true; }

    private int[] aim(Player p) {
        if (frame == null || !frame.isValid()) return null;
        return PaintRaytracer.texel(p, frame, canvas.size(), flipU, sensitivity);
    }

    // ---- Sensibilité du curseur (réglable dans le livre) ----

    public double sensitivity() { return sensitivity; }

    /** Règle la sensibilité (gain regard→toile) et la retient pour ce joueur. */
    public void setSensitivity(double v) {
        this.sensitivity = clampSensitivity(v);
        manager.rememberSensitivity(owner, this.sensitivity);
    }

    private static double clampSensitivity(double v) {
        return Math.max(0.3, Math.min(4.0, v)); // bas = curseur lent/précis, haut = rapide
    }

    private double defaultSensitivity() {
        var m = plugin.moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
        return m != null ? m.paintCursorSensitivity() : 1.0;
    }

    // ---- Sauvegarde ----

    public void save() {
        Player p = player();
        try {
            byte[] png = ImageUtil.fromArgbGrid(canvas.export());
            java.io.File out = target.textureFile();
            out.getParentFile().mkdirs();
            java.nio.file.Files.write(out.toPath(), png);
            target.onSaved(plugin, p);
        } catch (Exception e) {
            if (p != null) p.sendMessage(Text.mm("<red>Échec sauvegarde : " + e.getMessage()));
        }
    }

    // ---- Pipette monde / import / recolorisation ----

    public boolean worldPick() { return worldPick; }

    /** Quitte la toile pour viser un bloc du monde (clic gauche = prendre sa couleur). */
    public void enterWorldPick() {
        if (worldPick) return;
        worldPick = true;
        if (frame != null && frame.isValid()) { frame.remove(); frame = null; }
        Player p = player();
        if (p != null) p.sendMessage(Text.mm("<aqua>Pipette monde :</aqua> <gray>vise un bloc et <white>clic gauche</white> pour prendre sa couleur (<white>shift</white> = annuler)."));
    }

    public void exitWorldPick() {
        if (!worldPick) return;
        worldPick = false;
        Player p = player();
        if (p != null) spawnFrame(p); // repose la toile devant le joueur
    }

    private void pickFromWorld(Player p) {
        // Raytrace précis : on échantillonne LE pixel exact visé sur la face du bloc,
        // pas la couleur moyenne (permet de choisir précisément la teinte voulue).
        org.bukkit.util.RayTraceResult rt = p.rayTraceBlocks(8);
        org.bukkit.block.Block b = rt == null ? null : rt.getHitBlock();
        if (rt == null || b == null || b.getType().isAir()) { p.sendActionBar(Text.mm("<red>Aucun bloc visé.")); exitWorldPick(); return; }
        int rgb = pixelOfBlock(b, rt.getHitBlockFace(), rt.getHitPosition());
        if (rgb == 0) rgb = colorOfBlock(b); // repli : couleur moyenne si l'échantillon échoue
        if (rgb != 0) {
            currentColor = 0xFF000000 | (rgb & 0xFFFFFF);
            p.sendMessage(Text.mm("<green>Pixel pris sur <white>" + b.getType().name()
                    + "</white> → <white>#" + String.format("%06X", rgb & 0xFFFFFF)));
        } else {
            p.sendMessage(Text.mm("<yellow>Pas de texture pour ce bloc — couleur indisponible."));
        }
        exitWorldPick();
        refreshColorItem();
    }

    /** Couleur du pixel exact visé sur une face d'un bloc (0 si introuvable/transparent). */
    private int pixelOfBlock(org.bukkit.block.Block b, org.bukkit.block.BlockFace face, org.bukkit.util.Vector hit) {
        if (face == null || hit == null) return 0;
        String n = b.getType().name().toLowerCase(java.util.Locale.ROOT);
        String[] cand = switch (face) {
            case UP -> new String[]{n + "_top", n, n + "_side"};
            case DOWN -> new String[]{n + "_bottom", n + "_top", n};
            default -> new String[]{n + "_side", n + "_front", n, n + "_0"};
        };
        java.io.File tex = null;
        for (String c : cand) { tex = PaintManager.resolveTexture(plugin, c); if (tex != null) break; }
        if (tex == null) return 0;
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(tex);
            if (img == null) return 0;
            double lx = hit.getX() - b.getX(), ly = hit.getY() - b.getY(), lz = hit.getZ() - b.getZ();
            double u, v;
            switch (face) {
                case UP, DOWN -> { u = lx; v = lz; }
                case NORTH -> { u = 1 - lx; v = 1 - ly; }
                case SOUTH -> { u = lx; v = 1 - ly; }
                case WEST -> { u = lz; v = 1 - ly; }
                case EAST -> { u = 1 - lz; v = 1 - ly; }
                default -> { u = lx; v = lz; }
            }
            int px = (int) (clamp01(u) * (img.getWidth() - 1));
            int py = (int) (clamp01(v) * (img.getHeight() - 1));
            int argb = img.getRGB(px, py);
            if ((argb >>> 24) < 16) return 0; // pixel transparent → repli
            return argb & 0xFFFFFF;
        } catch (Exception e) {
            return 0;
        }
    }

    private static double clamp01(double v) { return Math.max(0.0, Math.min(0.9999, v)); }

    /** Couleur moyenne de la texture vanilla/custom d'un bloc (0 si introuvable). */
    private int colorOfBlock(org.bukkit.block.Block b) {
        String n = b.getType().name().toLowerCase(java.util.Locale.ROOT);
        String[] cand = {n, n + "_top", n + "_side", n + "_front", n + "_0"};
        for (String c : cand) {
            java.io.File f = PaintManager.resolveTexture(plugin, c);
            if (f != null) {
                try { return ImageUtil.averageColor(f); } catch (Exception ignored) { }
            }
        }
        return 0;
    }

    /** Charge une texture existante (item/bloc/vanilla) dans la toile comme base. */
    public boolean importBase(String name) {
        java.io.File f = PaintManager.resolveTexture(plugin, name);
        Player p = player();
        if (f == null) { if (p != null) p.sendMessage(Text.mm("<red>Texture introuvable : " + name)); return false; }
        loadFrom(f);
        dirty = true;
        if (p != null) p.sendMessage(Text.mm("<green>Base importée : <white>" + name));
        return true;
    }

    /** Recolorise tous les pixels existants vers la teinte de la couleur courante (garde la forme + l'ombrage). */
    public void recolorToCurrent() {
        canvas.pushHistory();
        canvas.recolorToHue(currentColor);
        dirty = true;
        Player p = player();
        if (p != null) p.sendActionBar(Text.mm("<green>Recolorisé vers #" + String.format("%06X", currentColor & 0xFFFFFF)));
    }

    public com.mooncore.util.ChatInput chat() {
        var m = plugin.moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
        return m == null ? null : m.chatInput();
    }

    // ---- Réglages (depuis le menu) ----

    public void setColor(int argb) { this.currentColor = argb; refreshColorItem(); }
    public int color() { return currentColor; }
    public void setBrush(int b) { this.brushSize = Math.max(1, Math.min(4, b)); }
    public int brush() { return brushSize; }
    public void cycleSymmetry() {
        var v = PixelCanvas.Symmetry.values();
        symmetry = v[(symmetry.ordinal() + 1) % v.length];
    }
    public PixelCanvas.Symmetry symmetry() { return symmetry; }
    public void toggleFlip() { flipU = !flipU; }
    public boolean flip() { return flipU; }
    public void clearCanvas() { canvas.pushHistory(); canvas.clear(); dirty = true; }
    public boolean undo() { boolean ok = canvas.undo(); dirty = true; return ok; }
    public boolean redo() { boolean ok = canvas.redo(); dirty = true; return ok; }
    /** Supprime partout la couleur courante (la rend transparente). */
    public void deleteCurrentColor() {
        canvas.pushHistory();
        canvas.replaceColor(currentColor, 0);
        dirty = true;
    }
    public void exit() { manager.close(owner); }

    // ---- Accès ----

    public PixelCanvas canvas() { return canvas; }
    public int cursorX() { return cursorX; }
    public int cursorY() { return cursorY; }
    public UUID owner() { return owner; }
    public String itemId() { return target.id(); }
    public MoonCore plugin() { return plugin; }
    public Player player() { return plugin.getServer().getPlayer(owner); }

    private static BlockFace yawToFace(float yaw) {
        float y = yaw % 360; if (y < 0) y += 360;
        if (y >= 315 || y < 45) return BlockFace.SOUTH;
        if (y < 135) return BlockFace.WEST;
        if (y < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }
}
