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

        // Curseur (encadré du texel visé).
        int cx = session.cursorX(), cy = session.cursorY();
        if (cx >= 0 && cy >= 0 && cx < size && cy < size) {
            int x0 = cx * scale, y0 = cy * scale;
            int hi = 0xFFFFFF00; // jaune
            for (int i = 0; i < scale; i++) {
                safe(img, x0 + i, y0, hi);
                safe(img, x0 + i, y0 + scale - 1, hi);
                safe(img, x0, y0 + i, hi);
                safe(img, x0 + scale - 1, y0 + i, hi);
            }
        }

        canvas.drawImage(0, 0, img);
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
