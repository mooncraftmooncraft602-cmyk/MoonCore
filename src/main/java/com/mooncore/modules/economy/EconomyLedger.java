package com.mooncore.modules.economy;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Journal d'audit économique (table {@code mooncore_economy_ledger}). Chaque mouvement
 * médié par EconomyBalancer y est inscrit en asynchrone.
 * <p>Plage de versions de migration réservée à l'économie : 300–399.
 */
public final class EconomyLedger {

    public enum Type { TAX, SINK, FEE, GAIN, TRANSFER }

    private final Database db;

    public EconomyLedger(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V300Ledger());
    }

    static final class V300Ledger implements Migration {
        @Override public int version() { return 300; }
        @Override public String description() { return "Économie : table mooncore_economy_ledger"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_economy_ledger (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        amount REAL NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        ts TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ledger_uuid ON mooncore_economy_ledger(uuid)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ledger_ts ON mooncore_economy_ledger(ts)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ledger_type ON mooncore_economy_ledger(type)");
            }
        }
    }

    /** Inscrit un mouvement (asynchrone, fire-and-forget). */
    public void log(UUID player, double amount, Type type, String reason) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO mooncore_economy_ledger (uuid, amount, type, reason) VALUES (?,?,?,?)")) {
                ps.setString(1, player.toString());
                ps.setDouble(2, amount);
                ps.setString(3, type.name());
                ps.setString(4, reason.length() > 128 ? reason.substring(0, 128) : reason);
                ps.executeUpdate();
            }
        }).exceptionally(ex -> {
            // L'audit ne doit jamais casser une transaction de jeu.
            return null;
        });
    }
}
