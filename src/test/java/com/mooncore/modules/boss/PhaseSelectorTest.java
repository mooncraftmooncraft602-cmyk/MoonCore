package com.mooncore.modules.boss;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhaseSelectorTest {

    private List<BossPhase> phases() {
        return List.of(
                new BossPhase("p1", 100, List.of()),
                new BossPhase("p2", 50, List.of()),
                new BossPhase("p3", 25, List.of()));
    }

    @Test
    void picksByHealthThreshold() {
        List<BossPhase> p = phases();
        assertEquals("p1", PhaseSelector.select(100, p).name());
        assertEquals("p1", PhaseSelector.select(60, p).name());
        assertEquals("p2", PhaseSelector.select(50, p).name());
        assertEquals("p2", PhaseSelector.select(30, p).name());
        assertEquals("p3", PhaseSelector.select(25, p).name());
        assertEquals("p3", PhaseSelector.select(1, p).name());
    }

    @Test
    void aboveAllThresholdsPicksHighest() {
        // Si aucune phase n'a un seuil >= aux PV (cas théorique > 100), on prend le plus haut.
        List<BossPhase> p = List.of(new BossPhase("a", 80, List.of()), new BossPhase("b", 40, List.of()));
        assertEquals("a", PhaseSelector.select(100, p).name());
    }
}
