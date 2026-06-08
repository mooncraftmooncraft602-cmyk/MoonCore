package com.mooncore.modules.reward;

import com.mooncore.data.Database;
import com.mooncore.data.migration.Migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Anti double-claim : table {@code mooncore_reward_claim} avec clé unique (uuid, reward_id,
 * source). L'insertion atomique sert de verrou : si la ligne existe déjà, la réclamation est
 * refusée. Plage de migration réservée : 500–599.
 */
public final class RewardClaimStore {

    private final Database db;

    public RewardClaimStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V500RewardClaim());
    }

    static final class V500RewardClaim implements Migration {
        @Override public int version() { return 500; }
        @Override public String description() { return "Reward : table mooncore_reward_claim"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_reward_claim (
                        uuid TEXT NOT NULL,
                        reward_id TEXT NOT NULL,
                        source TEXT NOT NULL,
                        claimed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (uuid, reward_id, source)
                    )
                    """);
            }
        }
    }

    /**
     * Tente de réserver une réclamation. Retourne true si la ligne a été insérée (première
     * fois), false si elle existait déjà (déjà réclamée). Opération atomique.
     */
    public CompletableFuture<Boolean> tryClaim(UUID uuid, String rewardId, String source) {
        return db.queryAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO mooncore_reward_claim (uuid, reward_id, source) VALUES (?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rewardId);
                ps.setString(3, source);
                return ps.executeUpdate() > 0;
            }
        });
    }
}
