package com.mooncore.modules.quest;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persistance de l'avancement des quêtes (table {@code mooncore_quest_state}).
 * Plage de migration réservée : 900–999.
 */
public final class QuestStore {

    public record Row(String questId, int step, int progress, boolean completed) {}

    private final Database db;

    public QuestStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V900Quest());
    }

    static final class V900Quest implements Migration {
        @Override public int version() { return 900; }
        @Override public String description() { return "Quest : table mooncore_quest_state"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_quest_state (
                        uuid TEXT NOT NULL,
                        quest_id TEXT NOT NULL,
                        step INTEGER NOT NULL DEFAULT 0,
                        progress INTEGER NOT NULL DEFAULT 0,
                        completed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, quest_id)
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_quest_uuid ON mooncore_quest_state(uuid)");
            }
        }
    }

    public List<Row> load(UUID uuid) throws SQLException {
        return db.query(c -> {
            List<Row> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT quest_id, step, progress, completed FROM mooncore_quest_state WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new Row(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getBoolean(4)));
                    }
                }
            }
            return rows;
        });
    }

    public void save(UUID uuid, String questId, int step, int progress, boolean completed) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_quest_state (uuid, quest_id, step, progress, completed)
                    VALUES (?,?,?,?,?)
                    ON CONFLICT(uuid, quest_id) DO UPDATE SET step=excluded.step, progress=excluded.progress, completed=excluded.completed
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, questId);
                ps.setInt(3, step);
                ps.setInt(4, progress);
                ps.setBoolean(5, completed);
                ps.executeUpdate();
            }
        });
    }
}
