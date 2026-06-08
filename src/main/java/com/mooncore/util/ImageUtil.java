package com.mooncore.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Utilitaires image purs (hors thread principal). Transforme une image générée par
 * l'IA (JPEG/PNG, grand format) en <b>texture pixel-art</b> Minecraft nette : recadrage
 * carré → downscale progressif → passe <b>nearest-neighbor</b> (bords francs, pas de
 * flou) → posterize/dithering optionnels → suppression de fond (icônes d'item).
 */
public final class ImageUtil {

    private ImageUtil() {}

    /**
     * Construit un PNG à partir d'une grille de pixels ARGB (0 = transparent).
     * Sert à l'éditeur pixel-art en jeu. {@code grid[y][x]}.
     */
    public static byte[] fromArgbGrid(int[][] grid) throws IOException {
        int h = grid.length;
        int w = h == 0 ? 0 : grid[0].length;
        if (w == 0 || h == 0) throw new IOException("Grille vide.");
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                img.setRGB(x, y, grid[y][x]);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    // ---- API publique ----

    /** Compat : icône carrée PNG avec suppression du fond. */
    public static byte[] toSquarePng(byte[] input, int size) throws IOException {
        return toItemIcon(input, size, 0, false);
    }

    public static byte[] toItemIcon(byte[] input, int size) throws IOException {
        return toItemIcon(input, size, 0, false);
    }

    public static byte[] toSquareOpaque(byte[] input, int size) throws IOException {
        return toSquareOpaque(input, size, 0, false);
    }

    /** Icône d'item : downscale net + posterize/dither optionnels + fond transparent. */
    public static byte[] toItemIcon(byte[] input, int size, int paletteLevels, boolean dither) throws IOException {
        BufferedImage out = pixelDownscale(read(input), size);
        if (paletteLevels >= 2) posterize(out, size, paletteLevels, dither);
        removeBackground(out, size);
        return png(out);
    }

    /** Texture pleine (opaque) — pour les blocs : pas de suppression de fond. */
    public static byte[] toSquareOpaque(byte[] input, int size, int paletteLevels, boolean dither) throws IOException {
        BufferedImage out = pixelDownscale(read(input), size);
        if (paletteLevels >= 2) posterize(out, size, paletteLevels, dither);
        return png(out);
    }

    /** Couleur moyenne (RGB) des pixels non transparents d'un PNG. 0 si vide/illisible. */
    public static int averageColor(java.io.File png) throws IOException {
        BufferedImage img = ImageIO.read(png);
        if (img == null) return 0;
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) < 16) continue; // quasi transparent → ignoré
                r += (argb >> 16) & 0xFF; g += (argb >> 8) & 0xFF; b += argb & 0xFF; n++;
            }
        }
        if (n == 0) return 0;
        return (int) ((r / n) << 16 | (g / n) << 8 | (b / n));
    }

    // ---- Pipeline pixel-art ----

    private static BufferedImage read(byte[] input) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(input));
        if (src == null) throw new IOException("Image illisible (format non supporté).");
        return src;
    }

    /**
     * Recadre au carré puis réduit à {@code size} : halving bilinéaire (anti-alias) jusqu'à
     * ~2×size, puis dernière passe NEAREST (pixels francs). Évite à la fois l'aliasing brutal
     * d'un nearest direct depuis 1024px et le flou d'un seul bilinéaire.
     */
    private static BufferedImage pixelDownscale(BufferedImage src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int sx = (src.getWidth() - side) / 2, sy = (src.getHeight() - side) / 2;
        BufferedImage cur = toArgb(src.getSubimage(sx, sy, side, side));

        int target2 = size * 2;
        while (cur.getWidth() / 2 >= target2) {
            int nw = cur.getWidth() / 2;
            BufferedImage half = new BufferedImage(nw, nw, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(cur, 0, 0, nw, nw, null);
            g.dispose();
            cur = half;
        }
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(cur, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toArgb(BufferedImage in) {
        if (in.getType() == BufferedImage.TYPE_INT_ARGB) return in;
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(in, 0, 0, null);
        g.dispose();
        return out;
    }

    /**
     * Réduit la profondeur de couleur à {@code levels} niveaux/canal (palette « rétro »),
     * avec dithering Floyd–Steinberg optionnel. Conserve l'alpha.
     */
    private static void posterize(BufferedImage img, int size, int levels, boolean dither) {
        int n = Math.max(2, Math.min(64, levels));
        float step = 255f / (n - 1);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                int nr = quant(r, step), ng = quant(g, step), nb = quant(b, step);
                img.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                if (dither) {
                    spread(img, size, x, y, r - nr, g - ng, b - nb);
                }
            }
        }
    }

    private static int quant(int v, float step) {
        return Math.max(0, Math.min(255, Math.round(Math.round(v / step) * step)));
    }

    /** Diffusion d'erreur Floyd–Steinberg sur les voisins non encore traités. */
    private static void spread(BufferedImage img, int size, int x, int y, int er, int eg, int eb) {
        addError(img, size, x + 1, y, er, eg, eb, 7 / 16f);
        addError(img, size, x - 1, y + 1, er, eg, eb, 3 / 16f);
        addError(img, size, x, y + 1, er, eg, eb, 5 / 16f);
        addError(img, size, x + 1, y + 1, er, eg, eb, 1 / 16f);
    }

    private static void addError(BufferedImage img, int size, int x, int y, int er, int eg, int eb, float f) {
        if (x < 0 || y < 0 || x >= size || y >= size) return;
        int argb = img.getRGB(x, y);
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) return;
        int r = clamp(((argb >> 16) & 0xFF) + Math.round(er * f));
        int g = clamp(((argb >> 8) & 0xFF) + Math.round(eg * f));
        int b = clamp((argb & 0xFF) + Math.round(eb * f));
        img.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    // ---- Suppression de fond (icônes) ----

    /**
     * Rend transparent le fond uni : flood-fill depuis les bords tant que la couleur reste
     * proche du fond échantillonné. Garde-fou : si les 4 coins ne sont PAS homogènes (image
     * sans fond uni), on n'efface rien pour ne pas trouer l'objet.
     */
    private static void removeBackground(BufferedImage img, int size) {
        int[] corners = {
                img.getRGB(0, 0), img.getRGB(size - 1, 0),
                img.getRGB(0, size - 1), img.getRGB(size - 1, size - 1)
        };
        int bg = average(corners);
        // Coins hétérogènes → pas de fond uni détectable, on s'abstient.
        for (int c : corners) if (colorDist(c, bg) > 45) return;

        int tol = 70;
        boolean[] visited = new boolean[size * size];
        Deque<int[]> queue = new ArrayDeque<>();
        for (int i = 0; i < size; i++) {
            enqueue(queue, visited, i, 0, size);
            enqueue(queue, visited, i, size - 1, size);
            enqueue(queue, visited, 0, i, size);
            enqueue(queue, visited, size - 1, i, size);
        }
        while (!queue.isEmpty()) {
            int[] px = queue.poll();
            int cx = px[0], cy = px[1];
            int rgb = img.getRGB(cx, cy);
            if (colorDist(rgb, bg) > tol) continue;
            img.setRGB(cx, cy, rgb & 0x00FFFFFF); // alpha = 0
            enqueue(queue, visited, cx + 1, cy, size);
            enqueue(queue, visited, cx - 1, cy, size);
            enqueue(queue, visited, cx, cy + 1, size);
            enqueue(queue, visited, cx, cy - 1, size);
        }
    }

    private static void enqueue(Deque<int[]> q, boolean[] v, int x, int y, int size) {
        if (x < 0 || y < 0 || x >= size || y >= size) return;
        int idx = y * size + x;
        if (v[idx]) return;
        v[idx] = true;
        q.add(new int[]{x, y});
    }

    private static int average(int[] colors) {
        int r = 0, g = 0, b = 0;
        for (int rgb : colors) { r += (rgb >> 16) & 0xFF; g += (rgb >> 8) & 0xFF; b += rgb & 0xFF; }
        int n = colors.length;
        return ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    private static int colorDist(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }
}
