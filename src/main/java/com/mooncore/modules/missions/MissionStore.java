package com.mooncore.modules.missions;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistance de la progression des missions (table {@code mooncore_mission_progress}).
 * Plage de migration réservée : 700–799.
 */
public final class MissionStore {

    public record Row(String missionId, int count, boolean claimed) {}

    private final Database db;

    public MissionStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V700Missions());
    }

    static final class V700Missions implements Migration {
        @Override public int version() { return 700; }
        @Override public String description() { return "Missions : table mooncore_mission_progress"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_mission_progress (
                        uuid TEXT NOT NULL,
                        mission_id TEXT NOT NULL,
                        period_key TEXT NOT NULL,
                        count INTEGER NOT NULL DEFAULT 0,
                        claimed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, mission_id, period_key)
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_mission_uuid ON mooncore_mission_progress(uuid)");
            }
        }
    }

    /** Charge les lignes du joueur correspondant aux périodes courantes. Appel async. */
    public List<Row> load(UUID uuid, Collection<String> currentPeriodKeys) throws SQLException {
        if (currentPeriodKeys.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(currentPeriodKeys.size(), "?"));
        return db.query(c -> {
            List<Row> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT mission_id, count, claimed FROM mooncore_mission_progress "
                            + "WHERE uuid=? AND period_key IN (" + placeholders + ")")) {
                ps.setString(1, uuid.toString());
                int i = 2;
                for (String key : currentPeriodKeys) ps.setString(i++, key);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Row(rs.getString(1), rs.getInt(2), rs.getBoolean(3)));
                    }
                }
            }
            return rows;
        });
    }

    public void save(UUID uuid, String missionId, String periodKey, int count, boolean claimed) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_mission_progress (uuid, mission_id, period_key, count, claimed)
                    VALUES (?,?,?,?,?)
                    ON CONFLICT(uuid, mission_id, period_key) DO UPDATE SET count=excluded.count, claimed=excluded.claimed
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, missionId);
                ps.setString(3, periodKey);
                ps.setInt(4, count);
                ps.setBoolean(5, claimed);
                ps.executeUpdate();
            }
        });
    }
}
