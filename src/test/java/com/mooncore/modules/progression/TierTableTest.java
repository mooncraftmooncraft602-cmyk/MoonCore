package com.mooncore.modules.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierTableTest {

    private TierTable table() {
        return new TierTable(List.of(
                new TierTable.Tier(1, 0, Set.of(), null),
                new TierTable.Tier(2, 2000, Set.of("enchant_t2"), "r2"),
                new TierTable.Tier(3, 8000, Set.of("boss_1"), "r3")));
    }

    @Test
    void tierForXpPicksHighestReached() {
        TierTable t = table();
        assertEquals(1, t.tierForXp(0));
        assertEquals(1, t.tierForXp(1999));
        assertEquals(2, t.tierForXp(2000));
        assertEquals(2, t.tierForXp(7999));
        assertEquals(3, t.tierForXp(8000));
        assertEquals(3, t.tierForXp(1_000_000));
    }

    @Test
    void nextTierXp() {
        TierTable t = table();
        assertEquals(2000, t.nextTierXp(1));
        assertEquals(8000, t.nextTierXp(2));
        assertEquals(-1, t.nextTierXp(3));
    }

    @Test
    void unlocksAreCumulative() {
        TierTable t = table();
        assertFalse(t.isUnlocked(1, "enchant_t2"));
        assertTrue(t.isUnlocked(2, "enchant_t2"));
        assertTrue(t.isUnlocked(3, "enchant_t2")); // hérité des tiers inférieurs
        assertTrue(t.isUnlocked(3, "boss_1"));
        assertEquals(2, t.unlocksUpTo(3).size());
    }

    @Test
    void emptyConfigYieldsTier1() {
        TierTable t = new TierTable(List.of());
        assertEquals(1, t.maxLevel());
        assertEquals(1, t.tierForXp(999999));
    }
}
