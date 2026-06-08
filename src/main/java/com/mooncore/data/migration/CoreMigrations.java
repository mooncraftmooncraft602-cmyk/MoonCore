package com.mooncore.data.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Migrations du noyau (tables transverses). Chaque module ajoutera ses propres
 * migrations via {@code DataManager.registerMigrations(...)} avant le run.
 */
public final class CoreMigrations {

    private CoreMigrations() {}

    public static List<Migration> all() {
        return List.of(new V1InitialSchema());
    }

    /** v1 : profil joueur + statistiques (modèle EAV). */
    static final class V1InitialSchema implements Migration {
        @Override public int version() { return 1; }
        @Override public String description() { return "Schéma initial : player_profile, statistics"; }

        @Override
        public void apply(Connection c) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_player_profile (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        first_join INTEGER NOT NULL DEFAULT 0,
                        last_seen INTEGER NOT NULL DEFAULT 0,
                        playtime_seconds INTEGER NOT NULL DEFAULT 0,
                        season_id TEXT NOT NULL
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_profile_name ON mooncore_player_profile(name)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_profile_season ON mooncore_player_profile(season_id)");

                st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mooncore_statistics (
                        uuid TEXT NOT NULL,
                        season_id TEXT NOT NULL,
                        stat_key TEXT NOT NULL,
                        value INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, season_id, stat_key)
                    )
                    """);
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stat_key ON mooncore_statistics(season_id, stat_key)");
            }
        }
    }
}
