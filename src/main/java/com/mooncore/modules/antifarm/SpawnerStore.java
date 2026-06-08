package com.mooncore.modules.antifarm;

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
import java.util.concurrent.CompletableFuture;

/**
 * Persistance du registre de spawners (table {@code mooncore_spawner}).
 * <p>Plage de versions de migration réservée à AntiFarm : 200–299.
 */
public final class SpawnerStore {

    private final Database db;

    public SpawnerStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V200SpawnerTable());
    }

    static final class V200SpawnerTable implements Migration {
        @Override public int version() { return 200; }
        @Override public String description() { return "AntiFarm : table mooncore_spawner"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_spawner (
                        loc_key TEXT NOT NULL PRIMARY KEY,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL,
                        chunk_key TEXT NOT NULL,
                        owner TEXT NOT NULL,
                        team TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_spawner_chunk ON mooncore_spawner(chunk_key)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_spawner_owner ON mooncore_spawner(owner)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_spawner_team ON mooncore_spawner(team)");
            }
        }
    }

    /** Chargement synchrone (démarrage uniquement). */
    public List<SpawnerRegistry.Entry> loadAll() throws SQLException {
        return db.query(c -> {
            List<SpawnerRegistry.Entry> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT world,x,y,z,owner,team FROM mooncore_spawner")) {
                while (rs.next()) {
                    out.add(new SpawnerRegistry.Entry(
                            rs.getString("world"),
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                            UUID.fromString(rs.getString("owner")),
                            rs.getString("team")));
                }
            }
            return out;
        });
    }

    public CompletableFuture<Void> save(SpawnerRegistry.Entry e) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_spawner (loc_key, world, x, y, z, chunk_key, owner, team)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON CONFLICT(loc_key) DO UPDATE SET owner=excluded.owner, team=excluded.team
                    """)) {
                ps.setString(1, e.locKey());
                ps.setString(2, e.world());
                ps.setInt(3, e.x()); ps.setInt(4, e.y()); ps.setInt(5, e.z());
                ps.setString(6, e.chunkKey());
                ps.setString(7, e.owner().toString());
                ps.setString(8, e.team());
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(String locKey) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mooncore_spawner WHERE loc_key=?")) {
                ps.setString(1, locKey);
                ps.executeUpdate();
            }
        });
    }
}
