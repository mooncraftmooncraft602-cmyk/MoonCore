package com.mooncore.modules.leaderboard;

import com.mooncore.api.leaderboard.LeaderboardEntry;
import com.mooncore.data.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Requêtes de classement (asynchrones, lecture seule). Aucune table dédiée : s'appuie sur
 * {@code mooncore_statistics} et {@code mooncore_player_profile}. Les snapshots sont mis en
 * cache par le module.
 */
public final class LeaderboardStore {

    private final Database db;

    public LeaderboardStore(Database db) {
        this.db = db;
    }

    public CompletableFuture<List<LeaderboardEntry>> topAsync(LeaderboardDefinition def, String seasonId) {
        return def.isPlaytime() ? topPlaytime(seasonId, def.size()) : topStat(def.source(), seasonId, def.size());
    }

    private CompletableFuture<List<LeaderboardEntry>> topStat(String statKey, String seasonId, int size) {
        return db.queryAsync(c -> {
            List<Leaderboards.RawRow> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT s.uuid AS uuid, p.name AS name, s.value AS value
                    FROM mooncore_statistics s
                    LEFT JOIN mooncore_player_profile p ON p.uuid = s.uuid
                    WHERE s.season_id=? AND s.stat_key=?
                    ORDER BY s.value DESC
                    LIMIT ?
                    """)) {
                ps.setString(1, seasonId);
                ps.setString(2, statKey);
                ps.setInt(3, size);
                collect(ps, rows);
            }
            return Leaderboards.rank(rows);
        });
    }

    private CompletableFuture<List<LeaderboardEntry>> topPlaytime(String seasonId, int size) {
        return db.queryAsync(c -> {
            List<Leaderboards.RawRow> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT uuid, name, playtime_seconds AS value
                    FROM mooncore_player_profile
                    WHERE season_id=?
                    ORDER BY playtime_seconds DESC
                    LIMIT ?
                    """)) {
                ps.setString(1, seasonId);
                ps.setInt(2, size);
                collect(ps, rows);
            }
            return Leaderboards.rank(rows);
        });
    }

    private void collect(PreparedStatement ps, List<Leaderboards.RawRow> rows) throws java.sql.SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuidStr = rs.getString("uuid");
                if (uuidStr == null) continue;
                rows.add(new Leaderboards.RawRow(UUID.fromString(uuidStr),
                        rs.getString("name"), rs.getLong("value")));
            }
        }
    }
}
