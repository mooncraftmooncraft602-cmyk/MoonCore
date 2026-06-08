package com.mooncore.modules.stats;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistance des profils et statistiques. Réutilise les tables du noyau
 * ({@code mooncore_player_profile}, {@code mooncore_statistics}) et ajoute
 * {@code mooncore_stat_history} (audit). Plage de migration réservée : 400–499.
 */
public final class StatisticsStore {

    /** Une ligne d'historique de modification de stat. */
    public record HistoryRow(UUID uuid, String statKey, long delta, String reason) {}

    private final Database db;

    public StatisticsStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V400StatHistory());
    }

    static final class V400StatHistory implements Migration {
        @Override public int version() { return 400; }
        @Override public String description() { return "Statistics : table mooncore_stat_history"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_stat_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        stat_key TEXT NOT NULL,
                        delta INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        ts TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hist_uuid ON mooncore_stat_history(uuid)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_hist_ts ON mooncore_stat_history(ts)");
            }
        }
    }

    /** Charge le profil + ses stats (saison courante), ou le crée s'il n'existe pas. Appel async. */
    public PlayerProfile loadOrCreate(UUID uuid, String name, String seasonId, long nowMs) throws SQLException {
        PlayerProfile existing = db.query(c -> load(c, uuid, name, seasonId));
        if (existing != null) {
            return existing;
        }
        PlayerProfile created = new PlayerProfile(uuid, name, nowMs, nowMs, 0, seasonId);
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO mooncore_player_profile "
                            + "(uuid, name, first_join, last_seen, playtime_seconds, season_id) VALUES (?,?,?,?,0,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, nowMs);
                ps.setLong(4, nowMs);
                ps.setString(5, seasonId);
                ps.executeUpdate();
            }
        });
        return created;
    }

    private PlayerProfile load(Connection c, UUID uuid, String name, String seasonId) throws SQLException {
        PlayerProfile profile = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name, first_join AS fj, last_seen AS ls, "
                        + "playtime_seconds, season_id FROM mooncore_player_profile WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    profile = new PlayerProfile(uuid,
                            name != null ? name : rs.getString("name"),
                            rs.getLong("fj"),
                            rs.getLong("ls"),
                            rs.getLong("playtime_seconds"),
                            rs.getString("season_id"));
                }
            }
        }
        if (profile == null) return null;
        for (Map.Entry<String, Long> e : loadStats(c, uuid, seasonId).entrySet()) {
            profile.set(e.getKey(), e.getValue());
        }
        profile.clearDirty();
        return profile;
    }

    public Map<String, Long> loadStats(UUID uuid, String seasonId) throws SQLException {
        return db.query(c -> loadStats(c, uuid, seasonId));
    }

    private Map<String, Long> loadStats(Connection c, UUID uuid, String seasonId) throws SQLException {
        Map<String, Long> map = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT stat_key, value FROM mooncore_statistics WHERE uuid=? AND season_id=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getString(1), rs.getLong(2));
            }
        }
        return map;
    }

    /** Sauvegarde le profil (identité + temps de jeu) et toutes ses stats. Appel async. */
    public void saveProfile(PlayerProfile p) throws SQLException {
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_player_profile (uuid, name, first_join, last_seen, playtime_seconds, season_id)
                    VALUES (?,?,?,?,?,?)
                    ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,
                        playtime_seconds=excluded.playtime_seconds, last_seen=excluded.last_seen
                    """)) {
                long now = System.currentTimeMillis();
                ps.setString(1, p.uuid().toString());
                ps.setString(2, p.name());
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.setLong(5, p.playtimeSeconds());
                ps.setString(6, p.seasonId());
                ps.executeUpdate();
            }
            if (!p.stats().isEmpty()) {
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO mooncore_statistics (uuid, season_id, stat_key, value)
                        VALUES (?,?,?,?)
                        ON CONFLICT(uuid, season_id, stat_key) DO UPDATE SET value=excluded.value
                        """)) {
                    for (Map.Entry<String, Long> e : p.stats().entrySet()) {
                        ps.setString(1, p.uuid().toString());
                        ps.setString(2, p.seasonId());
                        ps.setString(3, e.getKey());
                        ps.setLong(4, e.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
        });
    }

    /** Incrément direct en base pour un joueur hors-ligne. */
    public void incrementOffline(UUID uuid, String seasonId, String key, long amount) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_statistics (uuid, season_id, stat_key, value)
                    VALUES (?,?,?,?)
                    ON CONFLICT(uuid, season_id, stat_key) DO UPDATE SET value=value+excluded.value
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, seasonId);
                ps.setString(3, key);
                ps.setLong(4, amount);
                ps.executeUpdate();
            }
        });
    }

    /** Fixe une stat en base pour un joueur hors-ligne. */
    public void setOffline(UUID uuid, String seasonId, String key, long value) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_statistics (uuid, season_id, stat_key, value)
                    VALUES (?,?,?,?)
                    ON CONFLICT(uuid, season_id, stat_key) DO UPDATE SET value=excluded.value
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, seasonId);
                ps.setString(3, key);
                ps.setLong(4, value);
                ps.executeUpdate();
            }
        });
    }

    /** Chargement asynchrone des stats (pour affichage de joueurs hors-ligne). */
    public java.util.concurrent.CompletableFuture<Map<String, Long>> loadStatsAsync(UUID uuid, String seasonId) {
        return db.queryAsync(c -> loadStats(c, uuid, seasonId));
    }

    /** Insère un lot d'historique. Appel async. */
    public void flushHistory(List<HistoryRow> rows) throws SQLException {
        if (rows.isEmpty()) return;
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO mooncore_stat_history (uuid, stat_key, delta, reason) VALUES (?,?,?,?)")) {
                for (HistoryRow r : rows) {
                    ps.setString(1, r.uuid().toString());
                    ps.setString(2, r.statKey());
                    ps.setLong(3, r.delta());
                    ps.setString(4, r.reason().length() > 64 ? r.reason().substring(0, 64) : r.reason());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });
    }
}
