package com.mooncore.modules.customitem.paint;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Modèle pur d'une toile pixel-art (ARGB, 0 = transparent). Source de vérité de
 * l'éditeur ; la map et le GUI n'en sont que des vues. Outils : crayon, pot de
 * peinture (flood fill), ligne (Bresenham), symétrie, undo/redo (snapshots).
 * Aucune dépendance Bukkit → testable.
 */
public final class PixelCanvas {

    public enum Symmetry { NONE, HORIZONTAL, VERTICAL, BOTH }

    private final int size;
    private int[][] argb; // [y][x]
    private final Deque<int[][]> undo = new ArrayDeque<>();
    private final Deque<int[][]> redo = new ArrayDeque<>();
    private static final int MAX_HISTORY = 40;

    public PixelCanvas(int size) {
        this.size = size;
        this.argb = new int[size][size];
    }

    public int size() { return size; }
    public int get(int x, int y) { return in(x, y) ? argb[y][x] : 0; }
    public int[][] raw() { return argb; }

    private boolean in(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size; }

    // ---- Historique ----

    /** À appeler AVANT une action (trait, fill, clear, import). */
    public void pushHistory() {
        undo.push(copy(argb));
        if (undo.size() > MAX_HISTORY) undo.removeLast();
        redo.clear();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(copy(argb));
        argb = undo.pop();
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(copy(argb));
        argb = redo.pop();
        return true;
    }

    private int[][] copy(int[][] src) {
        int[][] c = new int[size][size];
        for (int y = 0; y < size; y++) System.arraycopy(src[y], 0, c[y], 0, size);
        return c;
    }

    // ---- Outils ----

    public void set(int x, int y, int color, Symmetry sym) {
        plot(x, y, color);
        switch (sym) {
            case HORIZONTAL -> plot(size - 1 - x, y, color);
            case VERTICAL -> plot(x, size - 1 - y, color);
            case BOTH -> {
                plot(size - 1 - x, y, color);
                plot(x, size - 1 - y, color);
                plot(size - 1 - x, size - 1 - y, color);
            }
            default -> { }
        }
    }

    /** Brosse carrée de rayon r (1 = 1px). */
    public void brush(int x, int y, int color, int radius, Symmetry sym) {
        int r = Math.max(1, radius) - 1;
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                set(x + dx, y + dy, color, sym);
    }

    private void plot(int x, int y, int color) {
        if (in(x, y)) argb[y][x] = color;
    }

    /** Remplissage contigu (flood fill 4-voies). */
    public void fill(int x, int y, int color) {
        if (!in(x, y)) return;
        int target = argb[y][x];
        if (target == color) return;
        Deque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int px = p[0], py = p[1];
            if (!in(px, py) || argb[py][px] != target) continue;
            argb[py][px] = color;
            q.add(new int[]{px + 1, py});
            q.add(new int[]{px - 1, py});
            q.add(new int[]{px, py + 1});
            q.add(new int[]{px, py - 1});
        }
    }

    /** Ligne de Bresenham (utilisée pour le drag et l'outil ligne). */
    public void line(int x0, int y0, int x1, int y1, int color, int radius, Symmetry sym) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            brush(x0, y0, color, radius, sym);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public void clear() {
        for (int[] row : argb) java.util.Arrays.fill(row, 0);
    }

    /**
     * Recolorise tous les pixels opaques vers la TEINTE/saturation d'une couleur cible,
     * en conservant la luminosité de chaque pixel (donc la forme et l'ombrage du pixel-art).
     * Idéal pour « prendre une épée diamant et la passer en rouge/feu » sans toucher aux pixels.
     */
    public void recolorToHue(int targetArgb) {
        float[] t = java.awt.Color.RGBtoHSB((targetArgb >> 16) & 0xFF, (targetArgb >> 8) & 0xFF, targetArgb & 0xFF, null);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int p = argb[y][x];
                int a = p >>> 24;
                if (a == 0) continue;
                float[] hsb = java.awt.Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                int rgb = java.awt.Color.HSBtoRGB(t[0], t[1], hsb[2]) & 0xFFFFFF; // teinte+sat cible, luminosité d'origine
                argb[y][x] = (a << 24) | rgb;
            }
        }
    }

    /** Remplace TOUTES les occurrences d'une couleur (non contigu). to=0 → supprime. */
    public void replaceColor(int from, int to) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (argb[y][x] == from) argb[y][x] = to;
    }

    // ============================================================
    //  Outils avancés (assistant)
    // ============================================================

    private static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Rectangle plein ou contour entre deux coins. */
    public void rect(int x0, int y0, int x1, int y1, int color, boolean filled, Symmetry sym) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        for (int y = ay; y <= by; y++)
            for (int x = ax; x <= bx; x++)
                if (filled || x == ax || x == bx || y == ay || y == by) set(x, y, color, sym);
    }

    /** Ellipse pleine ou contour inscrite dans la boîte des deux points. */
    public void ellipse(int x0, int y0, int x1, int y1, int color, boolean filled, Symmetry sym) {
        double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0;
        double rx = Math.max(0.5, Math.abs(x1 - x0) / 2.0), ry = Math.max(0.5, Math.abs(y1 - y0) / 2.0);
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        for (int y = ay; y <= by; y++)
            for (int x = ax; x <= bx; x++) {
                double nx = (x - cx) / rx, ny = (y - cy) / ry;
                if (nx * nx + ny * ny > 1.0) continue;
                if (filled) { set(x, y, color, sym); continue; }
                boolean border = false;
                for (int[] o : N4) {
                    double mx = (x + o[0] - cx) / rx, my = (y + o[1] - cy) / ry;
                    if (mx * mx + my * my > 1.0) { border = true; break; }
                }
                if (border) set(x, y, color, sym);
            }
    }

    /** Dégradé linéaire {@code a}→{@code b} projeté sur le vecteur (x0,y0)→(x1,y1) (remplit tout). */
    public void gradientFill(int x0, int y0, int x1, int y1, int a, int b) {
        double vx = x1 - x0, vy = y1 - y0, len2 = vx * vx + vy * vy;
        if (len2 < 1e-6) len2 = 1;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double t = Math.max(0, Math.min(1, ((x - x0) * vx + (y - y0) * vy) / len2));
                argb[y][x] = lerpArgb(a, b, t);
            }
    }

    public void flipHorizontal() {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size / 2; x++) {
                int t = argb[y][x]; argb[y][x] = argb[y][size - 1 - x]; argb[y][size - 1 - x] = t;
            }
    }

    public void flipVertical() {
        for (int y = 0; y < size / 2; y++) { int[] t = argb[y]; argb[y] = argb[size - 1 - y]; argb[size - 1 - y] = t; }
    }

    public void rotate90() {
        int[][] n = new int[size][size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) n[x][size - 1 - y] = argb[y][x];
        argb = n;
    }

    /** Décale la toile de (dx,dy), les bords libérés deviennent transparents. */
    public void shift(int dx, int dy) {
        int[][] n = new int[size][size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int sx = x - dx, sy = y - dy;
                n[y][x] = in(sx, sy) ? argb[sy][sx] : 0;
            }
        argb = n;
    }

    /** Ajoute un contour de {@code color} autour des amas opaques (idéal icônes d'objet). */
    public void outline(int color) {
        if ((color >>> 24) == 0) color |= 0xFF000000;
        int[][] src = copy(argb);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if ((src[y][x] >>> 24) != 0) continue;
                for (int[] o : N4)
                    if (!isTrans(src, x + o[0], y + o[1])) { argb[y][x] = color; break; }
            }
    }

    /** Ombrage automatique « biseau » : éclaircit les bords haut/gauche, assombrit bas/droite. */
    public void autoShade() {
        int[][] src = copy(argb);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if ((src[y][x] >>> 24) == 0) continue;
                double f = 1.0;
                if (isTrans(src, x - 1, y) || isTrans(src, x, y - 1)) f += 0.20;
                if (isTrans(src, x + 1, y) || isTrans(src, x, y + 1)) f -= 0.20;
                if (f != 1.0) argb[y][x] = scaleRgb(src[y][x], f);
            }
    }

    /** Multiplie la luminosité de tous les pixels opaques (1.0 = inchangé). */
    public void adjustBrightness(double f) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if ((argb[y][x] >>> 24) != 0) argb[y][x] = scaleRgb(argb[y][x], f);
    }

    /** Supprime les pixels opaques isolés (sans voisin opaque) — nettoyage anti-bruit. */
    public void removeStray() {
        int[][] src = copy(argb);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if ((src[y][x] >>> 24) == 0) continue;
                boolean any = false;
                for (int[] o : N4) if (!isTrans(src, x + o[0], y + o[1])) { any = true; break; }
                if (!any) argb[y][x] = 0;
            }
    }

    /** Réduit chaque canal à {@code levels} paliers (palette plus « pixel-art »). */
    public void posterize(int levels) {
        int lv = Math.max(2, levels);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int p = argb[y][x], a = p >>> 24;
                if (a == 0) continue;
                argb[y][x] = (a << 24) | (post((p >> 16) & 0xFF, lv) << 16)
                        | (post((p >> 8) & 0xFF, lv) << 8) | post(p & 0xFF, lv);
            }
    }

    /** Recentre le dessin (boîte englobante des pixels opaques) au milieu de la toile. */
    public void centerContent() {
        int minX = size, minY = size, maxX = -1, maxY = -1;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if ((argb[y][x] >>> 24) != 0) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        if (maxX < 0) return;
        int dx = (size - 1 - (minX + maxX)) / 2, dy = (size - 1 - (minY + maxY)) / 2;
        if (dx != 0 || dy != 0) shift(dx, dy);
    }

    /** Écrit un pixel direct (sans symétrie) — utilisé par les modèles/tampons. */
    public void put(int x, int y, int argb) { plot(x, y, argb); }

    /** Inverse les couleurs des pixels opaques (négatif). */
    public void invert() {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int p = argb[y][x], a = p >>> 24;
                if (a != 0) argb[y][x] = (a << 24) | (~p & 0xFFFFFF);
            }
    }

    /** Décale la teinte de tous les pixels opaques (degrés). */
    public void shiftHue(double degrees) {
        float d = (float) (degrees / 360.0);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int p = argb[y][x], a = p >>> 24;
                if (a == 0) continue;
                float[] hsb = java.awt.Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                argb[y][x] = (a << 24) | (java.awt.Color.HSBtoRGB((hsb[0] + d) % 1f, hsb[1], hsb[2]) & 0xFFFFFF);
            }
    }

    /** Multiplie la saturation des pixels opaques (1.0 = inchangé, 0 = gris). */
    public void adjustSaturation(double f) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int p = argb[y][x], a = p >>> 24;
                if (a == 0) continue;
                float[] hsb = java.awt.Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                float s = (float) Math.max(0, Math.min(1, hsb[1] * f));
                argb[y][x] = (a << 24) | (java.awt.Color.HSBtoRGB(hsb[0], s, hsb[2]) & 0xFFFFFF);
            }
    }

    /** Bruit de luminosité (±amount %) sur les pixels opaques — utile pour les blocs (grain). */
    public void addNoise(int amount) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if ((argb[y][x] >>> 24) != 0)
                    argb[y][x] = scaleRgb(argb[y][x], 1.0 + (r.nextInt(2 * amount + 1) - amount) / 100.0);
    }

    /** Rend la toile symétrique en recopiant une moitié sur l'autre. */
    public void symmetrize(boolean horizontal) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                if (horizontal) { if (x < size / 2) argb[y][size - 1 - x] = argb[y][x]; }
                else { if (y < size / 2) argb[size - 1 - y][x] = argb[y][x]; }
            }
    }

    /** Remplit tous les pixels transparents (fond) avec {@code color}. */
    public void fillBackground(int color) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if ((argb[y][x] >>> 24) == 0) argb[y][x] = color;
    }

    private boolean isTrans(int[][] s, int x, int y) { return !in(x, y) || (s[y][x] >>> 24) == 0; }

    private static int scaleRgb(int argb, double f) {
        int a = argb >>> 24;
        return (a << 24) | (clampc(((argb >> 16) & 0xFF) * f) << 16)
                | (clampc(((argb >> 8) & 0xFF) * f) << 8) | clampc((argb & 0xFF) * f);
    }

    private static int clampc(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    private static int post(int v, int levels) {
        int step = 255 / (levels - 1);
        return Math.min(255, Math.round((float) v / step) * step);
    }

    private static int lerpArgb(int a, int b, double t) {
        return (lerp(a >>> 24, b >>> 24, t) << 24) | (lerp((a >> 16) & 0xFF, (b >> 16) & 0xFF, t) << 16)
                | (lerp((a >> 8) & 0xFF, (b >> 8) & 0xFF, t) << 8) | lerp(a & 0xFF, b & 0xFF, t);
    }

    private static int lerp(int a, int b, double t) { return (int) Math.round(a + (b - a) * t); }

    public void load(int[][] src) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                argb[y][x] = (y < src.length && x < src[y].length) ? src[y][x] : 0;
    }

    /** Copie pour export (ImageUtil.fromArgbGrid attend [y][x]). */
    public int[][] export() { return copy(argb); }
}
