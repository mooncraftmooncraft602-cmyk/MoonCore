package com.mooncore.modules.progression;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Persistance de la progression (table {@code mooncore_progression}, par saison).
 * Plage de migration réservée : 600–699.
 */
public final class ProgressionStore {

    private final Database db;

    public ProgressionStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V600Progression());
    }

    static final class V600Progression implements Migration {
        @Override public int version() { return 600; }
        @Override public String description() { return "Progression : table mooncore_progression"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_progression (
                        uuid TEXT NOT NULL,
                        season_id TEXT NOT NULL,
                        xp INTEGER NOT NULL DEFAULT 0,
                        tier INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY (uuid, season_id)
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_prog_tier ON mooncore_progression(season_id, tier)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_prog_xp ON mooncore_progression(season_id, xp)");
            }
        }
    }

    /** Charge la progression (saison) ou crée une entrée par défaut (xp 0, tier 1). Appel async. */
    public ProgressionData loadOrCreate(UUID uuid, String seasonId) throws SQLException {
        ProgressionData data = db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT xp, tier FROM mooncore_progression WHERE uuid=? AND season_id=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, seasonId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new ProgressionData(uuid, rs.getLong("xp"), rs.getInt("tier"));
                    }
                }
            }
            return null;
        });
        if (data != null) {
            data.clearDirty();
            return data;
        }
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO mooncore_progression (uuid, season_id, xp, tier) VALUES (?,?,0,1)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, seasonId);
                ps.executeUpdate();
            }
        });
        return new ProgressionData(uuid, 0, 1);
    }

    public void save(ProgressionData d, String seasonId) throws SQLException {
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_progression (uuid, season_id, xp, tier) VALUES (?,?,?,?)
                    ON CONFLICT(uuid, season_id) DO UPDATE SET xp=excluded.xp, tier=excluded.tier
                    """)) {
                ps.setString(1, d.uuid().toString());
                ps.setString(2, seasonId);
                ps.setLong(3, d.xp());
                ps.setInt(4, d.tier());
                ps.executeUpdate();
            }
        });
    }
}
