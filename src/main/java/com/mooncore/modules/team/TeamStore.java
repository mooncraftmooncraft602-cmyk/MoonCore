package com.mooncore.modules.team;

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
 * Persistance des équipes (tables {@code mooncore_team} et {@code mooncore_team_member}).
 * Plage de migration réservée : 1000–1099.
 */
public final class TeamStore {

    private final Database db;

    public TeamStore(Database db) {
        this.db = db;
    }

    public static List<Migration> migrations() {
        return List.of(new V1000Teams());
    }

    static final class V1000Teams implements Migration {
        @Override public int version() { return 1000; }
        @Override public String description() { return "Teams : tables mooncore_team(_member)"; }
        @Override public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_team (
                        team_id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        owner TEXT NOT NULL,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        season_id TEXT NOT NULL
                    )
                    """);
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_team_member (
                        team_id TEXT NOT NULL,
                        uuid TEXT NOT NULL,
                        role TEXT NOT NULL,
                        PRIMARY KEY (team_id, uuid)
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_team_member_uuid ON mooncore_team_member(uuid)");
            }
        }
    }

    /** Charge toutes les équipes avec leurs membres (au démarrage). */
    public Map<String, Team> loadAll() throws SQLException {
        return db.query(c -> {
            Map<String, Team> teams = new HashMap<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT team_id, name, owner, created_at ts, season_id FROM mooncore_team")) {
                while (rs.next()) {
                    teams.put(rs.getString("team_id"), new Team(
                            rs.getString("team_id"), rs.getString("name"),
                            UUID.fromString(rs.getString("owner")),
                            rs.getLong("ts"), rs.getString("season_id")));
                }
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT team_id, uuid FROM mooncore_team_member")) {
                while (rs.next()) {
                    Team t = teams.get(rs.getString("team_id"));
                    if (t != null) t.addMember(UUID.fromString(rs.getString("uuid")));
                }
            }
            return teams;
        });
    }

    public void saveTeam(Team t) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO mooncore_team (team_id, name, owner, created_at, season_id) VALUES (?,?,?,?,?)
                    ON CONFLICT(team_id) DO UPDATE SET name=excluded.name, owner=excluded.owner
                    """)) {
                ps.setString(1, t.id());
                ps.setString(2, t.name());
                ps.setString(3, t.owner().toString());
                ps.setLong(4, t.createdAt());
                ps.setString(5, t.seasonId());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO mooncore_team_member (team_id, uuid, role) VALUES (?,?,?)")) {
                ps.setString(1, t.id());
                ps.setString(2, t.owner().toString());
                ps.setString(3, Team.ROLE_OWNER);
                ps.executeUpdate();
            }
        });
    }

    public void addMember(String teamId, UUID uuid, String role) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO mooncore_team_member (team_id, uuid, role) VALUES (?,?,?)")) {
                ps.setString(1, teamId);
                ps.setString(2, uuid.toString());
                ps.setString(3, role);
                ps.executeUpdate();
            }
        });
    }

    public void removeMember(String teamId, UUID uuid) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM mooncore_team_member WHERE team_id=? AND uuid=?")) {
                ps.setString(1, teamId);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    public void deleteTeam(String teamId) {
        db.fireAndForget(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mooncore_team_member WHERE team_id=?")) {
                ps.setString(1, teamId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mooncore_team WHERE team_id=?")) {
                ps.setString(1, teamId);
                ps.executeUpdate();
            }
        });
    }
}
