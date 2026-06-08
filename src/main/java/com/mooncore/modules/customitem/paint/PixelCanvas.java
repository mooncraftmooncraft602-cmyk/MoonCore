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

    public void load(int[][] src) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                argb[y][x] = (y < src.length && x < src[y].length) ? src[y][x] : 0;
    }

    /** Copie pour export (ImageUtil.fromArgbGrid attend [y][x]). */
    public int[][] export() { return copy(argb); }
}
