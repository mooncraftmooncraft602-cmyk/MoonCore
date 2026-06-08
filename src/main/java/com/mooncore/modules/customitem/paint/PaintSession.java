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

    public enum Tool { PENCIL, ERASER, FILL, EYEDROPPER, LINE, RECT, ELLIPSE, GRADIENT, NONE }

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
    private int secondaryColor = 0xFFFFFFFF; // 2e couleur (dégradé)
    private boolean shapeFilled = true;      // formes pleines vs contour
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
    private boolean cursorPinned = false;  // curseur posé manuellement (coordonnées / menu précision)
    private int zoom = 1;                  // 1 = toile entière, 2/4/8 = loupe sur une zone
    private int viewCenterX, viewCenterY;  // centre de la zone affichée quand zoom > 1
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
        this.viewCenterX = size / 2;
        this.viewCenterY = size / 2;
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
                if (img != null) loadFromImage(img);
            }
        } catch (Exception ignored) { }
    }

    /** Échantillonne une image (de n'importe quelle taille) vers la grille de la toile. */
    private void loadFromImage(java.awt.image.BufferedImage img) {
        int n = canvas.size();
        int[][] grid = new int[n][n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int sx = x * img.getWidth() / n, sy = y * img.getHeight() / n;
                grid[y][x] = img.getRGB(Math.min(sx, img.getWidth() - 1), Math.min(sy, img.getHeight() - 1));
            }
        canvas.load(grid);
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
        int[] t = cursorPinned ? new int[]{cursorX, cursorY} : aim(p);
        if (t == null) {
            // Pas de visée valide (regard franchement ailleurs) → on peint quand même le
            // dernier pixel survolé, pour qu'un clic ne soit jamais « perdu ».
            if (cursorX < 0 || cursorY < 0) return;
            t = new int[]{cursorX, cursorY};
        }
        cursorX = t[0]; cursorY = t[1];
        applyToolAt(t[0], t[1]);
        dirty = true; // feedback immédiat
    }

    public void applyToolAtCursor() {
        if (cursorX < 0 || cursorY < 0) pinCursor(canvas.size() / 2, canvas.size() / 2);
        applyToolAt(cursorX, cursorY);
        dirty = true;
    }

    private void applyToolAt(int x, int y) {
        Player p = player();
        if (p == null) return;
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
            case RECT, ELLIPSE, GRADIENT -> {
                if (lineAnchor == null) {
                    lineAnchor = new int[]{x, y};
                    p.sendActionBar(Text.mm("<yellow>Premier coin posé — clique le coin opposé"));
                } else {
                    canvas.pushHistory();
                    switch (currentTool) {
                        case RECT -> canvas.rect(lineAnchor[0], lineAnchor[1], x, y, currentColor, shapeFilled, symmetry);
                        case ELLIPSE -> canvas.ellipse(lineAnchor[0], lineAnchor[1], x, y, currentColor, shapeFilled, symmetry);
                        case GRADIENT -> canvas.gradientFill(lineAnchor[0], lineAnchor[1], x, y, currentColor, secondaryColor);
                        default -> { }
                    }
                    lineAnchor = null;
                }
            }
            default -> { }
        }
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
        if (!cursorPinned) {
            int[] t = aim(p);
            if (t != null && (t[0] != cursorX || t[1] != cursorY)) {
                cursorX = t[0]; cursorY = t[1];
                keepCursorVisible();
                showCursorFeedback(p);
                dirty = true;
            }
        }
        if (dirty) {
            try { p.sendMap(mapView); } catch (Throwable ignored) { }
            dirty = false;
        }
    }

    public void markDirty() { dirty = true; }

    private int[] aim(Player p) {
        if (frame == null || !frame.isValid()) return null;
        double gain = p.isSneaking() ? sensitivity * 0.35 : sensitivity;
        int[] local = PaintRaytracer.texel(p, frame, viewSize(), flipU, gain);
        if (local == null) return null;
        return new int[]{viewOriginX() + local[0], viewOriginY() + local[1]};
    }

    private void showCursorFeedback(Player p) {
        int sampled = canvas.get(cursorX, cursorY);
        String hex = (sampled >>> 24) == 0 ? "transparent" : "#" + String.format("%06X", sampled & 0xFFFFFF);
        p.sendActionBar(Text.mm("<gray>Pixel <white>" + cursorX + "," + cursorY + "</white> · " + hex
                + " · zoom x" + zoom + (cursorPinned ? " · verrouillé" : "")));
    }

    // ---- Loupe / curseur précis ----

    public int zoom() { return zoom; }

    public int viewSize() {
        return Math.max(1, canvas.size() / zoom);
    }

    public int viewOriginX() {
        return viewOrigin(viewCenterX);
    }

    public int viewOriginY() {
        return viewOrigin(viewCenterY);
    }

    private int viewOrigin(int center) {
        int visible = viewSize();
        return clamp(center - visible / 2, 0, canvas.size() - visible);
    }

    public void cycleZoom(boolean backwards) {
        int[] values = {1, 2, 4, 8};
        int idx = 0;
        for (int i = 0; i < values.length; i++) if (values[i] == zoom) { idx = i; break; }
        zoom = values[(idx + (backwards ? values.length - 1 : 1)) % values.length];
        if (canvas.size() / zoom < 2) zoom = 1;
        focusViewOnCursor();
        dirty = true;
    }

    public void resetZoom() {
        zoom = 1;
        focusViewOnCursor();
        dirty = true;
    }

    public void focusViewOnCursor() {
        if (cursorX >= 0 && cursorY >= 0) {
            viewCenterX = cursorX;
            viewCenterY = cursorY;
        } else {
            viewCenterX = canvas.size() / 2;
            viewCenterY = canvas.size() / 2;
        }
        dirty = true;
    }

    public void panView(int dx, int dy) {
        viewCenterX = clamp(viewCenterX + dx, 0, canvas.size() - 1);
        viewCenterY = clamp(viewCenterY + dy, 0, canvas.size() - 1);
        dirty = true;
    }

    public void pinCursor(int x, int y) {
        cursorX = clamp(x, 0, canvas.size() - 1);
        cursorY = clamp(y, 0, canvas.size() - 1);
        cursorPinned = true;
        keepCursorVisible();
        Player p = player();
        if (p != null) showCursorFeedback(p);
        dirty = true;
    }

    public void nudgeCursor(int dx, int dy) {
        if (cursorX < 0 || cursorY < 0) pinCursor(canvas.size() / 2, canvas.size() / 2);
        else pinCursor(cursorX + dx, cursorY + dy);
    }

    public void unpinCursor() {
        cursorPinned = false;
        dirty = true;
        Player p = player();
        if (p != null) p.sendActionBar(Text.mm("<gray>Curseur libre"));
    }

    public boolean cursorPinned() { return cursorPinned; }

    private void keepCursorVisible() {
        if (zoom <= 1 || cursorX < 0 || cursorY < 0) return;
        int ox = viewOriginX(), oy = viewOriginY(), visible = viewSize();
        if (cursorX < ox || cursorX >= ox + visible || cursorY < oy || cursorY >= oy + visible) {
            viewCenterX = cursorX;
            viewCenterY = cursorY;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
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
            // Sauvegarde NORMALE → retire une éventuelle animation (.mcmeta) restante.
            java.io.File mcmeta = new java.io.File(out.getParentFile(), out.getName() + ".mcmeta");
            if (mcmeta.isFile()) mcmeta.delete();
            target.onSaved(plugin, p);
        } catch (Exception e) {
            if (p != null) p.sendMessage(Text.mm("<red>Échec sauvegarde : " + e.getMessage()));
        }
    }

    /** Génère une ANIMATION (bande de frames + .png.mcmeta) depuis la toile actuelle. */
    public void applyAnimation(String style, int frames, int frametime) {
        Player p = player();
        try {
            int[][] strip = AnimationBuilder.strip(canvas.export(), style, frames);
            int h = strip.length, w = strip[0].length;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, strip[y][x]);
            java.io.File out = target.textureFile();
            out.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", out);
            java.io.File mcmeta = new java.io.File(out.getParentFile(), out.getName() + ".mcmeta");
            java.nio.file.Files.writeString(mcmeta.toPath(),
                    "{\n  \"animation\": {\n    \"frametime\": " + Math.max(1, frametime) + ",\n    \"interpolate\": false\n  }\n}");
            target.onSaved(plugin, p);
            if (p != null) p.sendMessage(Text.mm("<green>🎞 Animation « " + AnimationBuilder.label(style) + " » appliquée ("
                    + Math.max(2, frames) + " images · " + Math.max(1, frametime) + " ticks/img). Pack mis à jour."));
        } catch (Exception e) {
            if (p != null) p.sendMessage(Text.mm("<red>Échec animation : " + e.getMessage()));
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

    // ---- Assistant avancé : outils formes + opérations 1-clic + IA ----

    public void setTool(Tool t, boolean filled) { this.currentTool = t; this.shapeFilled = filled; lineAnchor = null; }
    public boolean shapeFilled() { return shapeFilled; }
    public int secondaryColor() { return secondaryColor; }
    public void setSecondaryColor(int argb) { this.secondaryColor = argb; }

    private void op(Runnable r) { canvas.pushHistory(); r.run(); dirty = true; }
    public void opFlipH() { op(canvas::flipHorizontal); }
    public void opFlipV() { op(canvas::flipVertical); }
    public void opRotate() { op(canvas::rotate90); }
    public void opNudge(int dx, int dy) { op(() -> canvas.shift(dx, dy)); }
    public void opOutline() { op(() -> canvas.outline(currentColor)); }
    public void opAutoShade() { op(canvas::autoShade); }
    public void opBrightness(double f) { op(() -> canvas.adjustBrightness(f)); }
    public void opCleanup() { op(canvas::removeStray); }
    public void opPosterize(int levels) { op(() -> canvas.posterize(levels)); }
    public void opCenter() { op(canvas::centerContent); }
    public void opInvert() { op(canvas::invert); }
    public void opHueShift(double deg) { op(() -> canvas.shiftHue(deg)); }
    public void opSaturation(double f) { op(() -> canvas.adjustSaturation(f)); }
    public void opNoise() { op(() -> canvas.addNoise(12)); }
    public void opSymmetrize(boolean horizontal) { op(() -> canvas.symmetrize(horizontal)); }
    public void opFillBackground() { op(() -> canvas.fillBackground(currentColor)); }

    /** Dessine une base d'objet (épée, pioche, gemme…) dans la couleur courante (efface la toile). */
    public void applyBase(String id) {
        op(() -> com.mooncore.modules.customitem.paint.TextureTemplates.base(canvas, id, currentColor, secondaryColor));
        Player p = player();
        if (p != null) p.sendActionBar(Text.mm("<green>Base « " + TextureTemplates.label(id) + " » dessinée — recolorise/détaille à ta guise"));
    }

    /** Dépose un tampon de forme (cercle, cœur, étoile…) dans la couleur courante (sans effacer). */
    public void applyStamp(String id) {
        op(() -> com.mooncore.modules.customitem.paint.TextureTemplates.stamp(canvas, id, currentColor));
        Player p = player();
        if (p != null) p.sendActionBar(Text.mm("<green>Tampon « " + TextureTemplates.label(id) + " » appliqué"));
    }

    /** « Auto-amélioration » : recadre, nettoie, ombrage biseau et contour foncé. */
    public void opEnhance() {
        op(() -> {
            canvas.removeStray();
            canvas.centerContent();
            canvas.autoShade();
            canvas.outline(0xFF1A1A1A);
        });
        Player p = player();
        if (p != null) p.sendActionBar(Text.mm("<green>Texture améliorée (nettoyage + ombrage + contour)"));
    }

    /** L'IA génère une texture depuis une description et la charge dans la toile (retouchable). */
    public void generateFromAi(String desc) {
        Player p = player();
        var ai = plugin.moduleManager().get(com.mooncore.modules.ai.AiAdminModule.class);
        if (ai == null || !ai.client().config().hasApiKey()) {
            if (p != null) p.sendMessage(Text.mm("<red>IA non configurée (modules/ai-assistant.yml → api-key)."));
            return;
        }
        if (p != null) p.sendMessage(Text.mm("<gray>🎨 L'IA dessine « <white>" + desc + "</white> »…"));
        ai.client().generateTexture(aiPrompt(desc), true).whenComplete((png, err) -> plugin.schedulers().sync(() -> {
            Player pl = player();
            if (png == null) {
                Throwable c = err == null ? null : (err.getCause() != null ? err.getCause() : err);
                if (pl != null) pl.sendMessage(Text.mm("<yellow>⚠ Texture IA non générée" + (c != null ? " : " + c.getMessage() : "") + "."));
                return;
            }
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
                if (img == null) { if (pl != null) pl.sendMessage(Text.mm("<yellow>⚠ Image IA illisible.")); return; }
                canvas.pushHistory();
                loadFromImage(img);
                dirty = true;
                if (pl != null) pl.sendMessage(Text.mm("<green>Texture IA chargée dans la toile — retouche-la puis sauve."));
            } catch (Exception ex) {
                if (pl != null) pl.sendMessage(Text.mm("<red>Erreur chargement IA : " + ex.getMessage()));
            }
        }));
    }

    private static String aiPrompt(String desc) {
        return "true 16-bit PIXEL ART game item icon of " + com.mooncore.util.Text.strip(desc) + ". "
                + "Hard pixel edges, limited color palette, NO anti-aliasing, NO gradients, NO blur. "
                + "Single centered object, flat front view, thick readable silhouette, "
                + "pure solid white background (#FFFFFF), no shadow, no text, no border. Minecraft style.";
    }

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
