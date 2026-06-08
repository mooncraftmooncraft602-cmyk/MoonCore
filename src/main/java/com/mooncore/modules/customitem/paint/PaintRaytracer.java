package com.mooncore.modules.customitem.paint;

import org.bukkit.Location;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Convertit le regard du joueur en coordonnée de texel sur la toile affichée par un
 * item frame. Intersection géométrique rayon/plan (précise et fluide, indépendante de
 * la hitbox du frame), puis projection sur les axes du plan → (x,y) dans [0,size).
 */
public final class PaintRaytracer {

    private static final double MAX_DIST = 6.0;
    // Marge TRÈS généreuse : viser au-delà de la toile peint quand même le pixel de
    // bord/coin le plus proche (clamp). N'affecte QUE les texels extérieurs (la précision
    // au centre reste intacte) → rend les coins/bords faciles à atteindre.
    private static final double MARGIN = 1.0;

    private PaintRaytracer() {}

    /** @return {x,y} dans [0,size) ou null si le joueur ne vise pas la toile. */
    public static int[] texel(Player p, ItemFrame frame, int size, boolean flipU) {
        Location eye = p.getEyeLocation();
        Vector origin = eye.toVector();
        Vector dir = eye.getDirection();

        Vector normal = frame.getFacing().getDirection();      // face vers le joueur
        Vector center = frame.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5).toVector();

        double denom = normal.dot(dir);
        if (Math.abs(denom) < 1e-6) return null;                // rayon parallèle au plan
        double t = center.clone().subtract(origin).dot(normal) / denom;
        if (t < 0 || t > MAX_DIST) return null;                 // derrière le joueur ou trop loin

        Vector hit = origin.clone().add(dir.clone().multiply(t));
        Vector rel = hit.subtract(center);

        // Axe horizontal écran (gauche→droite vu de face) = up × normale. Correct pour
        // toutes les faces, et cohérent avec le sens de dessin de la map.
        Vector uAxis = new Vector(0, 1, 0).crossProduct(normal).normalize();
        if (flipU) uAxis = uAxis.clone().multiply(-1);
        double u = rel.dot(uAxis);      // attendu dans [-0.5, 0.5]
        double v = rel.getY();          // vAxis = +Y
        if (Math.abs(u) > 0.5 + MARGIN || Math.abs(v) > 0.5 + MARGIN) return null; // hors toile

        int x = clamp((int) Math.floor((u + 0.5) * size), size);
        int y = clamp((int) Math.floor((0.5 - v) * size), size);
        return new int[]{x, y};
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }
}
