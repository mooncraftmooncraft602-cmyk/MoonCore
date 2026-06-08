package com.mooncore.modules.customitem.paint;

import com.mooncore.modules.customitem.paint.PixelCanvas.Symmetry;

/**
 * Modèles de texture procéduraux (aucune IA) : <b>bases d'objet</b> (épée, pioche, hache,
 * pelle, lingot, gemme, potion, anneau, minerai, bouclier) dessinées dans la couleur
 * choisie, et <b>tampons de formes</b> (cercle, anneau, losange, cœur, croix, cadre,
 * triangle, étoile). Tout est échelonné à la taille de la toile, puis cerné/ombré.
 */
public final class TextureTemplates {

    private TextureTemplates() {}

    private static final int OUTLINE = 0xFF1B1B1B;
    private static final int BROWN = 0xFF6B4A2B;
    private static final int GOLD = 0xFFE6B53C;
    private static final int GLASS = 0xFFBFE4FF;
    private static final int STONE = 0xFF8B8B8B;

    public static final String[] BASES = {
            "sword", "pickaxe", "axe", "shovel", "ingot", "gem", "potion", "ring", "ore", "shield"};
    public static final String[] STAMPS = {
            "circle", "ring", "diamond", "heart", "cross", "frame", "triangle", "star"};

    public static String label(String id) {
        return switch (id) {
            case "sword" -> "Épée"; case "pickaxe" -> "Pioche"; case "axe" -> "Hache";
            case "shovel" -> "Pelle"; case "ingot" -> "Lingot"; case "gem" -> "Gemme";
            case "potion" -> "Potion"; case "ring" -> "Anneau"; case "ore" -> "Minerai";
            case "shield" -> "Bouclier"; case "circle" -> "Cercle"; case "ring2" -> "Anneau";
            case "diamond" -> "Losange"; case "heart" -> "Cœur"; case "cross" -> "Croix";
            case "frame" -> "Cadre"; case "triangle" -> "Triangle"; case "star" -> "Étoile";
            default -> id;
        };
    }

    /** Dessine une BASE d'objet : efface la toile puis trace dans {@code base}/{@code accent}. */
    public static void base(PixelCanvas c, String id, int base, int accent) {
        c.clear();
        int N = c.size();
        int b = op(base), hi = scale(base, 1.4);
        switch (id) {
            case "sword" -> {
                c.line(p(N, .80), p(N, .10), p(N, .46), p(N, .46), b, 2, Symmetry.NONE);
                c.line(p(N, .76), p(N, .14), p(N, .46), p(N, .42), hi, 1, Symmetry.NONE);
                c.line(p(N, .30), p(N, .50), p(N, .54), p(N, .66), GOLD, 2, Symmetry.NONE);
                c.line(p(N, .44), p(N, .56), p(N, .16), p(N, .84), BROWN, 2, Symmetry.NONE);
                c.rect(p(N, .10), p(N, .80), p(N, .20), p(N, .90), GOLD, true, Symmetry.NONE);
                cerne(c);
            }
            case "pickaxe" -> {
                c.line(p(N, .52), p(N, .28), p(N, .52), p(N, .92), BROWN, 2, Symmetry.NONE);
                c.line(p(N, .14), p(N, .34), p(N, .52), p(N, .16), b, 2, Symmetry.NONE);
                c.line(p(N, .52), p(N, .16), p(N, .86), p(N, .34), b, 2, Symmetry.NONE);
                cerne(c);
            }
            case "axe" -> {
                c.line(p(N, .50), p(N, .22), p(N, .60), p(N, .92), BROWN, 2, Symmetry.NONE);
                c.ellipse(p(N, .14), p(N, .10), p(N, .58), p(N, .50), b, true, Symmetry.NONE);
                c.rect(p(N, .40), p(N, .14), p(N, .60), p(N, .48), 0, true, Symmetry.NONE); // creuse l'intérieur du tranchant
                cerne(c);
            }
            case "shovel" -> {
                c.line(p(N, .50), p(N, .10), p(N, .50), p(N, .60), BROWN, 2, Symmetry.NONE);
                c.rect(p(N, .36), p(N, .58), p(N, .64), p(N, .88), b, true, Symmetry.NONE);
                cerne(c);
            }
            case "ingot" -> {
                c.rect(p(N, .20), p(N, .42), p(N, .80), p(N, .68), b, true, Symmetry.NONE);
                c.rect(p(N, .30), p(N, .34), p(N, .70), p(N, .44), b, true, Symmetry.NONE);
                cerne(c);
            }
            case "gem" -> {
                double cx = (N - 1) / 2.0, cy = (N - 1) / 2.0, rx = .40 * N, ry = .46 * N;
                for (int y = 0; y < N; y++)
                    for (int x = 0; x < N; x++)
                        if (Math.abs(x - cx) / rx + Math.abs(y - cy) / ry <= 1.0)
                            c.put(x, y, y < cy ? hi : b); // moitié haute plus claire (facette)
                cerne(c);
            }
            case "potion" -> {
                c.ellipse(p(N, .20), p(N, .42), p(N, .80), p(N, .92), GLASS, true, Symmetry.NONE);
                c.ellipse(p(N, .28), p(N, .58), p(N, .72), p(N, .88), b, true, Symmetry.NONE);
                c.rect(p(N, .42), p(N, .28), p(N, .58), p(N, .46), GLASS, true, Symmetry.NONE);
                c.rect(p(N, .40), p(N, .18), p(N, .60), p(N, .30), BROWN, true, Symmetry.NONE);
                cerne(c);
            }
            case "ring" -> {
                c.ellipse(p(N, .22), p(N, .34), p(N, .78), p(N, .94), GOLD, true, Symmetry.NONE);
                c.ellipse(p(N, .34), p(N, .46), p(N, .66), p(N, .86), 0, true, Symmetry.NONE); // trou
                c.ellipse(p(N, .38), p(N, .10), p(N, .62), p(N, .36), b, true, Symmetry.NONE); // gemme
                cerne(c);
            }
            case "ore" -> {
                for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) c.put(x, y, STONE);
                c.ellipse(p(N, .12), p(N, .12), p(N, .40), p(N, .40), b, true, Symmetry.NONE);
                c.ellipse(p(N, .56), p(N, .18), p(N, .82), p(N, .44), b, true, Symmetry.NONE);
                c.ellipse(p(N, .22), p(N, .56), p(N, .48), p(N, .82), b, true, Symmetry.NONE);
                c.ellipse(p(N, .58), p(N, .60), p(N, .84), p(N, .84), b, true, Symmetry.NONE);
                c.addNoise(10);
            }
            case "shield" -> {
                c.rect(p(N, .22), p(N, .12), p(N, .78), p(N, .60), b, true, Symmetry.NONE);
                c.ellipse(p(N, .22), p(N, .44), p(N, .78), p(N, .96), b, true, Symmetry.NONE);
                c.line(p(N, .50), p(N, .18), p(N, .50), p(N, .82), op(accent), 1, Symmetry.NONE);
                c.line(p(N, .30), p(N, .40), p(N, .70), p(N, .40), op(accent), 1, Symmetry.NONE);
                cerne(c);
            }
            default -> { }
        }
    }

    /** Dépose un TAMPON de forme dans la couleur {@code color} SANS effacer la toile. */
    public static void stamp(PixelCanvas c, String id, int color) {
        int N = c.size(), col = op(color);
        switch (id) {
            case "circle" -> c.ellipse(p(N, .15), p(N, .15), p(N, .85), p(N, .85), col, true, Symmetry.NONE);
            case "ring" -> {
                c.ellipse(p(N, .12), p(N, .12), p(N, .88), p(N, .88), col, true, Symmetry.NONE);
                c.ellipse(p(N, .28), p(N, .28), p(N, .72), p(N, .72), 0, true, Symmetry.NONE);
            }
            case "diamond" -> {
                double cx = (N - 1) / 2.0, cy = (N - 1) / 2.0, rx = .42 * N, ry = .42 * N;
                for (int y = 0; y < N; y++)
                    for (int x = 0; x < N; x++)
                        if (Math.abs(x - cx) / rx + Math.abs(y - cy) / ry <= 1.0) c.put(x, y, col);
            }
            case "heart" -> {
                c.ellipse(p(N, .12), p(N, .16), p(N, .52), p(N, .56), col, true, Symmetry.NONE);
                c.ellipse(p(N, .48), p(N, .16), p(N, .88), p(N, .56), col, true, Symmetry.NONE);
                triangle(c, p(N, .10), p(N, .44), p(N, .90), p(N, .44), p(N, .50), p(N, .92), col);
            }
            case "cross" -> {
                c.rect(p(N, .42), p(N, .12), p(N, .58), p(N, .88), col, true, Symmetry.NONE);
                c.rect(p(N, .12), p(N, .42), p(N, .88), p(N, .58), col, true, Symmetry.NONE);
            }
            case "frame" -> {
                c.rect(0, 0, N - 1, N - 1, col, false, Symmetry.NONE);
                c.rect(1, 1, N - 2, N - 2, col, false, Symmetry.NONE);
            }
            case "triangle" -> triangle(c, p(N, .50), p(N, .12), p(N, .12), p(N, .88), p(N, .88), p(N, .88), col);
            case "star" -> {
                int cx = p(N, .5), cy = p(N, .5);
                c.line(cx, p(N, .08), cx, p(N, .92), col, 1, Symmetry.NONE);
                c.line(p(N, .08), cy, p(N, .92), cy, col, 1, Symmetry.NONE);
                c.line(p(N, .20), p(N, .20), p(N, .80), p(N, .80), col, 1, Symmetry.NONE);
                c.line(p(N, .80), p(N, .20), p(N, .20), p(N, .80), col, 1, Symmetry.NONE);
                c.ellipse(p(N, .38), p(N, .38), p(N, .62), p(N, .62), col, true, Symmetry.NONE);
            }
            default -> { }
        }
    }

    // ---- helpers ----

    private static void cerne(PixelCanvas c) { c.outline(OUTLINE); c.autoShade(); }

    /** Triangle plein (scanline) entre 3 sommets. */
    private static void triangle(PixelCanvas c, int x0, int y0, int x1, int y1, int x2, int y2, int col) {
        int minY = Math.min(y0, Math.min(y1, y2)), maxY = Math.max(y0, Math.max(y1, y2));
        for (int y = minY; y <= maxY; y++) {
            Double xa = edge(x0, y0, x1, y1, y), xb = edge(x1, y1, x2, y2, y), xc = edge(x2, y2, x0, y0, y);
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (Double xv : new Double[]{xa, xb, xc}) if (xv != null) { lo = Math.min(lo, xv); hi = Math.max(hi, xv); }
            if (lo > hi) continue;
            for (int x = (int) Math.round(lo); x <= (int) Math.round(hi); x++) c.put(x, y, col);
        }
    }

    /** Intersection x du segment avec la ligne horizontale y (null si hors segment). */
    private static Double edge(int x0, int y0, int x1, int y1, int y) {
        if (y0 == y1) return null;
        if (y < Math.min(y0, y1) || y > Math.max(y0, y1)) return null;
        return x0 + (double) (x1 - x0) * (y - y0) / (y1 - y0);
    }

    private static int p(int n, double frac) { return (int) Math.round(frac * (n - 1)); }
    private static int op(int c) { return 0xFF000000 | (c & 0xFFFFFF); }

    private static int scale(int c, double f) {
        int r = clamp(((c >> 16) & 0xFF) * f), g = clamp(((c >> 8) & 0xFF) * f), b = clamp((c & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
}
