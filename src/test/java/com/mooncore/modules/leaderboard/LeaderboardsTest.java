package com.mooncore.modules.leaderboard;

import com.mooncore.api.leaderboard.LeaderboardEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardsTest {

    @Test
    void rankAssignsSequentialRanks() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<Leaderboards.RawRow> rows = List.of(
                new Leaderboards.RawRow(a, "Alice", 100),
                new Leaderboards.RawRow(b, "Bob", 50));

        List<LeaderboardEntry> ranked = Leaderboards.rank(rows);
        assertEquals(2, ranked.size());
        assertEquals(1, ranked.get(0).rank());
        assertEquals("Alice", ranked.get(0).name());
        assertEquals(100, ranked.get(0).value());
        assertEquals(2, ranked.get(1).rank());
    }

    @Test
    void nullNameFallsBackToShortUuid() {
        UUID a = UUID.randomUUID();
        List<LeaderboardEntry> ranked = Leaderboards.rank(List.of(
                new Leaderboards.RawRow(a, null, 5)));
        assertEquals(8, ranked.get(0).name().length());
        assertTrue(a.toString().startsWith(ranked.get(0).name()));
    }

    @Test
    void playtimeDefinitionDetected() {
        var def = new LeaderboardDefinition("pt", "Temps", "playtime", 10);
        assertTrue(def.isPlaytime());
        assertEquals(false, new LeaderboardDefinition("k", "K", "mob_kills", 10).isPlaytime());
    }
}
