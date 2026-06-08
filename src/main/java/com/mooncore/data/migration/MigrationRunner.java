package com.mooncore.data.migration;

import com.mooncore.data.Database;
import com.mooncore.util.MoonLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applique les migrations manquantes au démarrage, dans une transaction par migration,
 * et enregistre les versions appliquées dans {@code mooncore_schema_version}.
 */
public final class MigrationRunner {

    private static final String VERSION_TABLE = "mooncore_schema_version";

    private final Database database;
    private final MoonLogger logger;

    public MigrationRunner(Database database, MoonLogger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void run(List<Migration> migrations) throws SQLException {
        database.execute(this::ensureVersionTable);

        Set<Integer> applied = database.query(this::loadAppliedVersions);

        List<Migration> pending = new ArrayList<>();
        for (Migration m : migrations) {
            if (!applied.contains(m.version())) pending.add(m);
        }
        pending.sort(Comparator.comparingInt(Migration::version));

        if (pending.isEmpty()) {
            logger.info("Schéma à jour (" + applied.size() + " migration(s) appliquée(s)).");
            return;
        }

        for (Migration m : pending) {
            applyOne(m);
        }
        logger.info(pending.size() + " migration(s) appliquée(s).");
    }

    private void applyOne(Migration m) throws SQLException {
        try (Connection c = database.rawConnection()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                m.apply(c);
                recordVersion(c, m);
                c.commit();
                logger.info("Migration v" + m.version() + " — " + m.description());
            } catch (SQLException e) {
                c.rollback();
                throw new SQLException("Échec migration v" + m.version() + " (" + m.description() + ")", e);
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    private void ensureVersionTable(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS %s (
                    version INTEGER NOT NULL PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(VERSION_TABLE));
        }
    }

    private Set<Integer> loadAppliedVersions(Connection c) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM " + VERSION_TABLE)) {
            while (rs.next()) versions.add(rs.getInt(1));
        }
        return versions;
    }

    private void recordVersion(Connection c, Migration m) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + VERSION_TABLE + " (version, description) VALUES (?, ?)")) {
            ps.setInt(1, m.version());
            ps.setString(2, m.description());
            ps.executeUpdate();
        }
    }
}
