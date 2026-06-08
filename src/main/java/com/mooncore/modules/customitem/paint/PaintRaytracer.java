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

    private static final double MAX_DIST = 8.0;
    // Zone de capture ÉNORME : tant qu'on regarde grosso modo vers la toile, on prend le
    // pixel le plus proche (clamp aux bords). Viser hors du cadre peint donc quand même le
    // bord/coin — plus besoin de viser pile dedans, et un clic « à côté » fonctionne. On ne
    // renvoie null QUE si le joueur regarde franchement ailleurs (au-delà de REJECT).
    private static final double REJECT = 4.0;
    // La map d'un item frame est rendue sur la FACE du bloc (≈0,5 vers le joueur), PAS au
    // centre du bloc. On décale donc le plan d'intersection le long de la normale pour qu'il
    // coïncide avec la toile VISIBLE — sinon parallaxe : on doit viser hors du cadre pour
    // atteindre les bords (bug remonté). +0,5 = face avant du bloc, côté joueur.
    private static final double SURFACE_OFFSET = 0.5;

    private PaintRaytracer() {}

    /**
     * @param sensitivity gain appliqué au mappage regard→toile : >1 = la toile « répond »
     *                    plus (bords plus faciles avec moins de mouvement), 1 = neutre.
     * @return {x,y} dans [0,size) ou null si le joueur ne vise pas la toile.
     */
    public static int[] texel(Player p, ItemFrame frame, int size, boolean flipU, double sensitivity) {
        Location eye = p.getEyeLocation();
        Vector origin = eye.toVector();
        Vector dir = eye.getDirection();

        Vector normal = frame.getFacing().getDirection();      // face vers le joueur
        Vector center = frame.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5).toVector()
                .add(normal.clone().multiply(SURFACE_OFFSET));  // plan = surface visible de la map

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
        double gain = sensitivity <= 0 ? 1.0 : sensitivity;
        double u = rel.dot(uAxis) * gain;   // attendu dans [-0.5, 0.5] (×gain)
        double v = rel.getY() * gain;       // vAxis = +Y
        if (Math.abs(u) > 0.5 + REJECT || Math.abs(v) > 0.5 + REJECT) return null; // regarde ailleurs

        // Clamp systématique → viser un peu hors du cadre sélectionne le pixel de bord le plus
        // proche (les bords/coins deviennent triviaux, indépendamment de la sensibilité).
        int x = clamp((int) Math.floor((u + 0.5) * size), size);
        int y = clamp((int) Math.floor((0.5 - v) * size), size);
        return new int[]{x, y};
    }

    private static int clamp(int v, int size) {
        return Math.max(0, Math.min(size - 1, v));
    }
}
