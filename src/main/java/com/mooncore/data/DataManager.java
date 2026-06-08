package com.mooncore.data;

import com.mooncore.MoonCore;
import com.mooncore.data.cache.CacheService;
import com.mooncore.data.migration.CoreMigrations;
import com.mooncore.data.migration.Migration;
import com.mooncore.data.migration.MigrationRunner;

import java.sql.SQLException;
import java.util.List;

/**
 * Service noyau d'accès aux données : initialise le pool {@link Database}, le
 * {@link CacheService} et exécute les migrations. Initialisé par {@link MoonCore}
 * avant l'activation des modules métier (beaucoup en dépendent).
 */
public final class DataManager {

    private final MoonCore plugin;
    private Database database;
    private CacheService cache;
    private MigrationRunner migrationRunner;

    public DataManager(MoonCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Connecte la base, prépare le cache et applique les migrations du noyau.
     *
     * @throws SQLException si la connexion ou les migrations échouent
     */
    public void init() throws SQLException {
        this.cache = new CacheService(plugin.getConfig().getConfigurationSection("cache"));

        var dbSection = plugin.getConfig().getConfigurationSection("database");
        if (dbSection == null) {
            throw new SQLException("Section 'database' absente de config.yml");
        }
        this.database = Database.create(dbSection, plugin.getDataFolder(), plugin.logger());

        // Vérifie la connexion immédiatement (fail-fast clair).
        database.execute(c -> {
            try (var st = c.createStatement()) {
                st.execute("SELECT 1");
            }
        });

        this.migrationRunner = new MigrationRunner(database, plugin.logger());
        migrationRunner.run(CoreMigrations.all());

        plugin.logger().info("DataManager prêt (HikariCP + cache).");
    }

    /**
     * Applique des migrations supplémentaires (appelé par un module dans son onEnable).
     * Les versions doivent être globalement uniques (réserver une plage par module).
     */
    public void applyMigrations(List<Migration> migrations) throws SQLException {
        migrationRunner.run(migrations);
    }

    public void shutdown() {
        if (database != null) {
            database.close();
        }
    }

    public Database database() { return database; }
    public CacheService cache() { return cache; }
    public boolean isReady() { return database != null && database.isHealthy(); }
}
