package com.mooncore.modules.economy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyLogicTest {

    @Test
    void progressiveTaxIsMarginal() {
        ProgressiveTax tax = new ProgressiveTax(List.of(
                new ProgressiveTax.Bracket(0, 0.0),
                new ProgressiveTax.Bracket(1000, 0.10),
                new ProgressiveTax.Bracket(5000, 0.20)));

        // Sous la première tranche taxée : 0.
        assertEquals(0, tax.computeTax(1000), 1e-9);
        // 1000→5000 à 10% = 400 sur une richesse de 5000.
        assertEquals(400, tax.computeTax(5000), 1e-9);
        // + (10000-5000) à 20% = 1000 → total 1400.
        assertEquals(1400, tax.computeTax(10000), 1e-9);
    }

    @Test
    void progressiveTaxHandlesZeroAndNegative() {
        ProgressiveTax tax = new ProgressiveTax(List.of(new ProgressiveTax.Bracket(0, 0.5)));
        assertEquals(0, tax.computeTax(0), 1e-9);
        assertEquals(0, tax.computeTax(-100), 1e-9);
    }

    @Test
    void abnormalGainFiresOnceOnThreshold() {
        AbnormalGainDetector d = new AbnormalGainDetector(60_000, 1000);
        UUID p = UUID.randomUUID();

        assertFalse(d.record(p, 500, 0));     // total 500 < 1000
        assertTrue(d.record(p, 600, 1000));   // total 1100 ≥ 1000 → transition
        assertFalse(d.record(p, 100, 2000));  // déjà signalé, pas de nouvelle transition
    }

    @Test
    void abnormalGainWindowExpires() {
        AbnormalGainDetector d = new AbnormalGainDetector(1000, 1000);
        UUID p = UUID.randomUUID();
        d.record(p, 900, 0);
        // Bien après la fenêtre, l'ancien gain ne compte plus.
        assertEquals(0, d.windowTotal(p, 5000), 1e-9);
    }

    @Test
    void abnormalDisabledWhenThresholdZero() {
        AbnormalGainDetector d = new AbnormalGainDetector(60_000, 0);
        UUID p = UUID.randomUUID();
        assertFalse(d.record(p, 999_999, 0));
    }
}
