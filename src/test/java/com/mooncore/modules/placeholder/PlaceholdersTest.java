package com.mooncore.modules.placeholder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceholdersTest {

    @Test
    void parsesSimpleLeaderboard() {
        var r = Placeholders.parseLeaderboard("leaderboard_kills_1_name");
        assertEquals("kills", r.board());
        assertEquals(1, r.rank());
        assertEquals("name", r.field());
    }

    @Test
    void boardWithUnderscoreRejoined() {
        var r = Placeholders.parseLeaderboard("leaderboard_mob_kills_3_value");
        assertEquals("mob_kills", r.board());
        assertEquals(3, r.rank());
        assertEquals("value", r.field());
    }

    @Test
    void invalidFormsReturnNull() {
        assertNull(Placeholders.parseLeaderboard("tier"));
        assertNull(Placeholders.parseLeaderboard("leaderboard_kills_name"));   // pas de rang
        assertNull(Placeholders.parseLeaderboard("leaderboard_kills_1_xp"));   // champ invalide
        assertNull(Placeholders.parseLeaderboard("leaderboard_kills_0_name")); // rang < 1
    }
}
