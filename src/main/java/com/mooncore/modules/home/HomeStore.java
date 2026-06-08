package com.mooncore.modules.home;

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
 * Persistance des homes (table {@code mooncore_home}). Plage de migration : 1100–1199.
 */
public final class HomeStore {

    private final Database db;

    public HomeStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V1100Home());
    }

    static final class V1100Home implements Migration {
        @Override public int version() { return 1100; }
        @Override public String description() { return "Home : table mooncore_home"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_home (
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
                        yaw REAL NOT NULL, pitch REAL NOT NULL,
                        PRIMARY KEY (uuid, name)
                    )
                    """);
            }
        }
    }

    public List<Home> load(UUID uuid) throws SQLException {
        return db.query(c -> {
            List<Home> homes = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT name, world, x, y, z, yaw, pitch FROM mooncore_home WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        homes.add(new Home(rs.getString("name"), rs.getString("world"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch")));
                    }
                }
            }
            return homes;
        });
    }

    public void save(UUID uuid, Home h) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_home (uuid, name, world, x, y, z, yaw, pitch)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON CONFLICT(uuid, name) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                        yaw=excluded.yaw, pitch=excluded.pitch
                    """)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, h.name());
                ps.setString(3, h.world());
                ps.setDouble(4, h.x()); ps.setDouble(5, h.y()); ps.setDouble(6, h.z());
                ps.setFloat(7, h.yaw()); ps.setFloat(8, h.pitch());
                ps.executeUpdate();
            }
        });
    }

    public void delete(UUID uuid, String name) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM mooncore_home WHERE uuid=? AND name=?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.executeUpdate();
            }
        });
    }
}
