package com.mooncore.modules.customitem.paint;

/**
 * Génère une <b>bande d'animation</b> (frames empilées verticalement) à partir d'une seule
 * image de base, par transformations procédurales (pulsation, arc-en-ciel, scintillement,
 * défilement, lueur, secousse). Le résultat suit le format Minecraft : PNG de
 * {@code W × (H·frames)} accompagné d'un {@code .png.mcmeta}. Logique pure (testable).
 */
public final class AnimationBuilder {

    private AnimationBuilder() {}

    public static final String[] STYLES = {"pulse", "rainbow", "flicker", "scroll", "glow", "shake"};

    public static String label(String style) {
        return switch (style) {
            case "pulse" -> "Pulsation"; case "rainbow" -> "Arc-en-ciel"; case "flicker" -> "Scintillement";
            case "scroll" -> "Défilement"; case "glow" -> "Lueur"; case "shake" -> "Secousse";
            default -> style;
        };
    }

    /** @return une grille [H·frames][W] (frames empilées de haut en bas). */
    public static int[][] strip(int[][] base, String style, int frames) {
        int h = base.length, w = base[0].length;
        int n = Math.max(2, Math.min(32, frames));
        int[][] out = new int[h * n][w];
        for (int f = 0; f < n; f++) {
            int[][] fr = frame(base, style, f, n);
            for (int y = 0; y < h; y++) System.arraycopy(fr[y], 0, out[f * h + y], 0, w);
        }
        return out;
    }

    private static int[][] frame(int[][] base, String style, int f, int n) {
        int w = base[0].length;
        double t = (double) f / n;                 // 0..1
        return switch (style) {
            case "pulse" -> recolor(base, 1.0 + 0.45 * Math.sin(2 * Math.PI * t));
            case "glow" -> recolor(base, 0.6 + 0.8 * (t < 0.5 ? t * 2 : (1 - t) * 2));
            case "rainbow" -> hueShift(base, 360.0 * t);
            case "flicker" -> recolor(base, 0.7 + 0.6 * frac(Math.sin(f * 12.9898) * 43758.5453));
            case "scroll" -> shift(base, (int) Math.round((double) f * w / n), 0);
            case "shake" -> shift(base, SHAKE[f % SHAKE.length][0], SHAKE[f % SHAKE.length][1]);
            default -> copy(base);
        };
    }

    private static final int[][] SHAKE = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, -1}, {0, 0}};

    private static int[][] recolor(int[][] base, double factor) {
        int h = base.length, w = base[0].length;
        int[][] o = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = base[y][x];
                o[y][x] = (p >>> 24) == 0 ? 0 : scale(p, factor);
            }
        return o;
    }

    private static int[][] hueShift(int[][] base, double deg) {
        int h = base.length, w = base[0].length;
        float d = (float) (deg / 360.0);
        int[][] o = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int p = base[y][x], a = p >>> 24;
                if (a == 0) { o[y][x] = 0; continue; }
                float[] hsb = java.awt.Color.RGBtoHSB((p >> 16) & 0xFF, (p >> 8) & 0xFF, p & 0xFF, null);
                o[y][x] = (a << 24) | (java.awt.Color.HSBtoRGB((hsb[0] + d) % 1f, hsb[1], hsb[2]) & 0xFFFFFF);
            }
        return o;
    }

    private static int[][] shift(int[][] base, int dx, int dy) {
        int h = base.length, w = base[0].length;
        int[][] o = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                o[y][x] = base[((y - dy) % h + h) % h][((x - dx) % w + w) % w]; // défilement bouclé
        return o;
    }

    private static int[][] copy(int[][] base) {
        int h = base.length;
        int[][] o = new int[h][];
        for (int y = 0; y < h; y++) o[y] = base[y].clone();
        return o;
    }

    private static int scale(int argb, double f) {
        int a = argb >>> 24;
        return (a << 24) | (clamp(((argb >> 16) & 0xFF) * f) << 16)
                | (clamp(((argb >> 8) & 0xFF) * f) << 8) | clamp((argb & 0xFF) * f);
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static double frac(double v) { return v - Math.floor(v); }
}
