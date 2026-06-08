package com.mooncore.modules.ai;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Journalisation/audit des actions IA (plage de migration 1300). Chaque appel IA est
 * tracé : admin, date, commande, requête, résultat résumé, statut de validation.
 * Consultable via {@code /moon ai history}.
 */
public final class AiAuditStore {

    public record Entry(long ts, String admin, String command, String prompt, String result, String status) {}

    private final Database db;

    public AiAuditStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V1300AiAudit());
    }

    static final class V1300AiAudit implements Migration {
        @Override public int version() { return 1300; }
        @Override public String description() { return "AI : table mooncore_ai_audit"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS mooncore_ai_audit (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            ts INTEGER NOT NULL,
                            admin TEXT NOT NULL,
                            command TEXT NOT NULL,
                            prompt TEXT NOT NULL,
                            result TEXT NOT NULL,
                            status TEXT NOT NULL
                        )
                        """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ai_audit_ts ON mooncore_ai_audit(ts)");
            }
        }
    }

    public CompletableFuture<Void> record(String admin, String command, String prompt,
                                          String result, String status, long nowMs) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO mooncore_ai_audit (ts, admin, command, prompt, result, status) VALUES (?,?,?,?,?,?)")) {
                ps.setLong(1, nowMs);
                ps.setString(2, admin);
                ps.setString(3, command);
                ps.setString(4, truncate(prompt, 1000));
                ps.setString(5, truncate(result, 2000));
                ps.setString(6, status);
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<List<Entry>> recent(int limit) {
        return db.queryAsync(c -> {
            List<Entry> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ts, admin, command, prompt, result, status FROM mooncore_ai_audit "
                    + "ORDER BY ts DESC LIMIT ?")) {
                ps.setInt(1, Math.max(1, Math.min(100, limit)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new Entry(rs.getLong("ts"), rs.getString("admin"), rs.getString("command"),
                                rs.getString("prompt"), rs.getString("result"), rs.getString("status")));
                    }
                }
            }
            return out;
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
