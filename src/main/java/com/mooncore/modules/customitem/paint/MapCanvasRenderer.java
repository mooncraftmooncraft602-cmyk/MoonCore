package com.mooncore.modules.customitem.paint;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

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
        int scale = MAP / size;
        BufferedImage img = new BufferedImage(MAP, MAP, BufferedImage.TYPE_INT_ARGB);

        for (int my = 0; my < MAP; my++) {
            int ty = my / scale;
            for (int mx = 0; mx < MAP; mx++) {
                int tx = mx / scale;
                int argb = c.get(tx, ty);
                int rgb;
                if ((argb >>> 24) == 0) {
                    // Damier pour le vide.
                    boolean dark = ((tx + ty) & 1) == 0;
                    rgb = 0xFF000000 | (dark ? 0x3A3A3A : 0x505050);
                } else {
                    rgb = argb;
                }
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
        if (cx >= 0 && cy >= 0 && cx < size && cy < size) {
            int x0 = cx * scale, y0 = cy * scale;
            int gx = x0 + scale / 2, gy = y0 + scale / 2;
            for (int k = 0; k < MAP; k++) { guide(img, gx, k); guide(img, k, gy); }
            rect(img, x0 - 1, y0 - 1, scale + 2, 0xFF000000); // anneau extérieur (contraste)
            rect(img, x0, y0, scale, 0xFFFFFF00);             // anneau jaune
        }

        canvas.drawImage(0, 0, img);
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
