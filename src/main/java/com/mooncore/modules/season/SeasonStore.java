package com.mooncore.modules.season;

import com.mooncore.api.season.SeasonInfo;
import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistance des saisons (table {@code mooncore_season}). Les dates sont stockées en
 * millisecondes epoch (INTEGER) ; {@code ends_at = 0} signifie « pas de fin ». Plage 800–899.
 */
public final class SeasonStore {

    private static final long DAY_MS = 86_400_000L;

    private final Database db;

    public SeasonStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V800Season());
    }

    static final class V800Season implements Migration {
        @Override public int version() { return 800; }
        @Override public String description() { return "Season : table mooncore_season"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_season (
                        season_id TEXT NOT NULL PRIMARY KEY,
                        started_at INTEGER NOT NULL DEFAULT 0,
                        ends_at INTEGER NOT NULL DEFAULT 0,
                        active INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            }
        }
    }

    private static long endsAt(long nowMs, int lengthDays) {
        return lengthDays > 0 ? nowMs + lengthDays * DAY_MS : 0L;
    }

    /** Crée la saison si absente (avec une fin éventuelle), puis renvoie ses infos. Appel async. */
    public SeasonInfo ensure(String seasonId, int lengthDays) throws SQLException {
        long now = System.currentTimeMillis();
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO mooncore_season (season_id, started_at, ends_at, active) VALUES (?,?,?,1)")) {
                ps.setString(1, seasonId);
                ps.setLong(2, now);
                ps.setLong(3, endsAt(now, lengthDays));
                ps.executeUpdate();
            }
        });
        return load(seasonId);
    }

    public SeasonInfo load(String seasonId) throws SQLException {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT season_id, started_at s, ends_at e, active FROM mooncore_season WHERE season_id=?")) {
                ps.setString(1, seasonId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return read(rs);
                }
            }
            return null;
        });
    }

    public List<SeasonInfo> all() throws SQLException {
        return db.query(c -> {
            List<SeasonInfo> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT season_id, started_at s, ends_at e, active FROM mooncore_season ORDER BY started_at DESC")) {
                while (rs.next()) out.add(read(rs));
            }
            return out;
        });
    }

    /** Active une nouvelle saison (désactive les autres) ; idempotent. Appel async. */
    public void activate(String seasonId, int lengthDays) throws SQLException {
        long now = System.currentTimeMillis();
        db.execute(c -> {
            try (PreparedStatement off = c.prepareStatement("UPDATE mooncore_season SET active=0")) {
                off.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_season (season_id, started_at, ends_at, active) VALUES (?,?,?,1)
                    ON CONFLICT(season_id) DO UPDATE SET active=1
                    """)) {
                ps.setString(1, seasonId);
                ps.setLong(2, now);
                ps.setLong(3, endsAt(now, lengthDays));
                ps.executeUpdate();
            }
        });
    }

    private SeasonInfo read(ResultSet rs) throws SQLException {
        return new SeasonInfo(rs.getString("season_id"),
                rs.getLong("s"), rs.getLong("e"), rs.getBoolean("active"));
    }
}
