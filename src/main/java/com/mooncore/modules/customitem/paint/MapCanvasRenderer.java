package com.mooncore.modules.customitem.paint;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Rend la toile de l'éditeur sur une map 128×128 : chaque texel agrandi (scale=128/size),
 * damier sur les pixels transparents, quadrillage, et encadré du curseur visé. Comme
 * MapCanvas est reconstruit à chaque appel, on redessine tout via {@link MapCanvas#drawImage}.
 */
public final class MapCanvasRenderer extends MapRenderer {

    private static final int MAP = 128;
    private final PaintSession session;

    public MapCanvasRenderer(PaintSession session) {
        super(true); // contextual
        this.session = session;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        PixelCanvas c = session.canvas();
        int size = c.size();
        int visible = session.viewSize();
        int ox = session.viewOriginX(), oy = session.viewOriginY();
        int scale = Math.max(1, MAP / visible);
        BufferedImage img = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_ARGB);

        for (int my = 0; my < MAP; my++) {
            int ty = oy + Math.min(visible - 1, my / scale);
            for (int mx = 0; mx < MAP; mx++) {
                int tx = ox + Math.min(visible - 1, mx / scale);
                int rgb = pixel(c, tx, ty);
                // Quadrillage aux frontières de texel.
                if (scale >= 4 && (mx % scale == 0 || my % scale == 0)) {
                    rgb = darken(rgb);
                }
                img.setRGB(mx, my, rgb);
            }
        }

        // Curseur : guides plein écran (repèrent la colonne + la ligne du pixel visé) puis
        // double anneau noir/jaune bien visible sur n'importe quel fond.
        int cx = session.cursorX(), cy = session.cursorY();
        if (cx >= ox && cy >= oy && cx < ox + visible && cy < oy + visible && cx < size && cy < size) {
            int x0 = (cx - ox) * scale, y0 = (cy - oy) * scale;
            int gx = x0 + scale / 2, gy = y0 + scale / 2;
            for (int k = 0; k < MAP; k++) { guide(img, gx, k); guide(img, k, gy); }
            rect(img, x0 - 1, y0 - 1, scale + 2, 0xFF000000); // anneau extérieur (contraste)
            rect(img, x0, y0, scale, session.cursorPinned() ? 0xFFFF55FF : 0xFFFFFF00);
            if (session.zoom() == 1 && size > 16) magnifier(img, c, cx, cy);
        }
        label(img, "x" + session.zoom() + "  " + Math.max(cx, 0) + "," + Math.max(cy, 0)
                + (session.cursorPinned() ? " lock" : ""));

        canvas.drawImage(0, 0, img);
    }

    private static int pixel(PixelCanvas c, int tx, int ty) {
        int argb = c.get(tx, ty);
        if ((argb >>> 24) != 0) return argb;
        boolean dark = ((tx + ty) & 1) == 0;
        return 0xFF000000 | (dark ? 0x3A3A3A : 0x505050);
    }

    /** Mini-loupe 7x7 autour du curseur quand on regarde la toile entière. */
    private static void magnifier(BufferedImage img, PixelCanvas c, int cx, int cy) {
        int cells = 7, cell = 7, pad = 3;
        int w = cells * cell + pad * 2;
        int x0 = MAP - w - 4, y0 = MAP - w - 4;
        fill(img, x0 - 1, y0 - 1, w + 2, w + 2, 0xE0000000);
        for (int yy = 0; yy < cells; yy++) {
            for (int xx = 0; xx < cells; xx++) {
                int tx = cx + xx - cells / 2;
                int ty = cy + yy - cells / 2;
                int rgb = (tx < 0 || ty < 0 || tx >= c.size() || ty >= c.size()) ? 0xFF202020 : pixel(c, tx, ty);
                fill(img, x0 + pad + xx * cell, y0 + pad + yy * cell, cell, cell, rgb);
            }
        }
        int mid = cells / 2;
        rect(img, x0 + pad + mid * cell - 1, y0 + pad + mid * cell - 1, cell + 2, 0xFF000000);
        rect(img, x0 + pad + mid * cell, y0 + pad + mid * cell, cell, 0xFFFFFF00);
    }

    private static void fill(BufferedImage img, int x0, int y0, int w, int h, int rgb) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                safe(img, x0 + x, y0 + y, rgb);
    }

    private static void label(BufferedImage img, String text) {
        Graphics2D g = img.createGraphics();
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(2, 2, Math.min(124, text.length() * 7 + 6), 13);
        g.setColor(Color.WHITE);
        g.drawString(text, 5, 12);
        g.dispose();
    }

    private static void rect(BufferedImage img, int x0, int y0, int s, int rgb) {
        for (int i = 0; i < s; i++) {
            safe(img, x0 + i, y0, rgb);
            safe(img, x0 + i, y0 + s - 1, rgb);
            safe(img, x0, y0 + i, rgb);
            safe(img, x0 + s - 1, y0 + i, rgb);
        }
    }

    /** Ligne-guide : éclaircit le pixel vers le jaune pour rester lisible sans masquer la toile. */
    private static void guide(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= MAP || y >= MAP) return;
        int rgb = img.getRGB(x, y);
        int r = ((rgb >> 16) & 0xFF) / 2 + 128;
        int g = ((rgb >> 8) & 0xFF) / 2 + 128;
        int b = (rgb & 0xFF) / 2;
        img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private static void safe(BufferedImage img, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < MAP && y < MAP) img.setRGB(x, y, rgb);
    }

    private static int darken(int rgb) {
        int a = rgb & 0xFF000000;
        int r = (int) (((rgb >> 16) & 0xFF) * 0.7);
        int g = (int) (((rgb >> 8) & 0xFF) * 0.7);
        int b = (int) ((rgb & 0xFF) * 0.7);
        return a | (r << 16) | (g << 8) | b;
    }
}
