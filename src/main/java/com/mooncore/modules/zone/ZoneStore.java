package com.mooncore.modules.zone;

import com.mooncore.api.zone.Region;
import com.mooncore.api.zone.ZoneFlag;
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
 * Persistance des régions (table {@code mooncore_zone}). Les flags sont sérialisés
 * en chaîne compacte {@code cle=0|1;…}. Lecture au démarrage (sync), écritures async.
 * <p>Plage de versions de migration réservée au module Zone : 100–199.
 */
public final class ZoneStore {

    private final Database db;

    public ZoneStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V100ZoneTable());
    }

    static final class V100ZoneTable implements Migration {
        @Override public int version() { return 100; }
        @Override public String description() { return "Zone : table mooncore_zone"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_zone (
                        name TEXT NOT NULL PRIMARY KEY,
                        world TEXT NOT NULL,
                        min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
                        max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
                        priority INTEGER NOT NULL DEFAULT 0,
                        flags TEXT NOT NULL
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_zone_world ON mooncore_zone(world)");
            }
        }
    }

    /** Chargement synchrone (démarrage uniquement). */
    public List<Region> loadAll() throws SQLException {
        return db.query(c -> {
            List<Region> out = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM mooncore_zone")) {
                while (rs.next()) {
                    Region r = new Region(
                            rs.getString("name"), rs.getString("world"),
                            rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                            rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"),
                            rs.getInt("priority"));
                    deserializeFlags(rs.getString("flags"), r);
                    out.add(r);
                }
            }
            return out;
        });
    }

    public CompletableFuture<Void> save(Region r) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_zone
                        (name, world, min_x, min_y, min_z, max_x, max_y, max_z, priority, flags)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(name) DO UPDATE SET
                        world=excluded.world, min_x=excluded.min_x, min_y=excluded.min_y, min_z=excluded.min_z,
                        max_x=excluded.max_x, max_y=excluded.max_y, max_z=excluded.max_z,
                        priority=excluded.priority, flags=excluded.flags
                    """)) {
                ps.setString(1, r.name());
                ps.setString(2, r.world());
                ps.setInt(3, r.minX()); ps.setInt(4, r.minY()); ps.setInt(5, r.minZ());
                ps.setInt(6, r.maxX()); ps.setInt(7, r.maxY()); ps.setInt(8, r.maxZ());
                ps.setInt(9, r.priority());
                ps.setString(10, serializeFlags(r));
                ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> delete(String name) {
        return db.executeAsync(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mooncore_zone WHERE name=?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
        });
    }

    static String serializeFlags(Region r) {
        StringBuilder sb = new StringBuilder();
        r.flags().forEach((flag, value) -> {
            if (sb.length() > 0) sb.append(';');
            sb.append(flag.key()).append('=').append(value ? '1' : '0');
        });
        return sb.toString();
    }

    static void deserializeFlags(String raw, Region r) {
        if (raw == null || raw.isBlank()) return;
        for (String token : raw.split(";")) {
            int eq = token.indexOf('=');
            if (eq <= 0) continue;
            ZoneFlag.byKey(token.substring(0, eq)).ifPresent(flag ->
                    r.setFlag(flag, token.charAt(token.length() - 1) == '1'));
        }
    }
}
