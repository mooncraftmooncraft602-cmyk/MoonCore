package com.mooncore.modules.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Taxe progressive par tranches marginales (style impôt sur le revenu). Pour une richesse
 * {@code W}, chaque tranche {@code [from_i, from_{i+1})} est taxée à son taux ; la dernière
 * tranche s'étend à l'infini. Logique pure et testable.
 */
public final class ProgressiveTax {

    /** Une tranche : à partir de {@code from}, le taux marginal {@code rate} (0..1) s'applique. */
    public record Bracket(double from, double rate) {}

    private final List<Bracket> brackets;

    public ProgressiveTax(List<Bracket> brackets) {
        List<Bracket> sorted = new ArrayList<>(brackets);
        sorted.sort(Comparator.comparingDouble(Bracket::from));
        // Garantit une première tranche à 0 si absente (taux 0 par défaut sous le premier seuil).
        if (sorted.isEmpty() || sorted.get(0).from() > 0) {
            sorted.add(0, new Bracket(0, 0));
        }
        this.brackets = List.copyOf(sorted);
    }

    /** Montant total de taxe pour une richesse donnée. */
    public double computeTax(double wealth) {
        if (wealth <= 0) return 0;
        double tax = 0;
        for (int i = 0; i < brackets.size(); i++) {
            double from = brackets.get(i).from();
            if (wealth <= from) break;
            double to = (i + 1 < brackets.size()) ? brackets.get(i + 1).from() : Double.MAX_VALUE;
            double portion = Math.min(wealth, to) - from;
            if (portion > 0) tax += portion * brackets.get(i).rate();
        }
        return tax;
    }

    /** Taux effectif moyen (taxe / richesse). */
    public double effectiveRate(double wealth) {
        return wealth <= 0 ? 0 : computeTax(wealth) / wealth;
    }

    public List<Bracket> brackets() {
        return brackets;
    }
}
