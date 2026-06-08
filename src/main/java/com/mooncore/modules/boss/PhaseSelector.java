package com.mooncore.modules.boss;

import java.util.List;

/**
 * Sélection de la phase active selon le pourcentage de PV. Les phases ont un seuil
 * {@code fromPercent} (ex. 100, 50, 25) ; à mesure que les PV baissent, on entre dans des
 * phases de seuil plus bas. La phase active est celle de plus petit seuil encore ≥ aux PV
 * courants. Logique pure et testable.
 */
public final class PhaseSelector {

    private PhaseSelector() {}

    public static BossPhase select(double currentPercent, List<BossPhase> phases) {
        BossPhase chosen = null;
        for (BossPhase p : phases) {
            if (p.fromPercent() >= currentPercent) {
                if (chosen == null || p.fromPercent() < chosen.fromPercent()) {
                    chosen = p;
                }
            }
        }
        // Si rien (PV au-dessus de tous les seuils), prendre le seuil le plus haut.
        if (chosen == null) {
            for (BossPhase p : phases) {
                if (chosen == null || p.fromPercent() > chosen.fromPercent()) chosen = p;
            }
        }
        return chosen;
    }
}
