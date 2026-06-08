package com.mooncore.data;

import com.mooncore.data.sql.SqlConsumer;
import com.mooncore.data.sql.SqlFunction;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Couche d'accès base de données : pool HikariCP + exécuteur asynchrone dédié.
 * <p>
 * Aucune requête ne doit s'exécuter sur le thread principal : utiliser
 * {@link #executeAsync(SqlConsumer)} / {@link #queryAsync(SqlFunction)}. Les variantes
 * synchrones ({@link #execute}/{@link #query}) sont réservées au démarrage (migrations).
 */
public final class Database {

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final com.mooncore.util.MoonLogger logger;

    private Database(HikariDataSource dataSource, ExecutorService executor,
                     com.mooncore.util.MoonLogger logger) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.logger = logger;
    }

    /**
     * Construit le pool depuis la section {@code database}. Backend par défaut : SQLite
     * (fichier autonome, aucun serveur requis). {@code type: mysql} reste possible.
     *
     * @param dataFolder dossier du plugin (pour situer le fichier SQLite)
     */
    public static Database create(ConfigurationSection cfg, File dataFolder,
                                  com.mooncore.util.MoonLogger logger) {
        String type = cfg.getString("type", "sqlite").toLowerCase(java.util.Locale.ROOT);
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("MoonCore-Hikari");

        int maxPool;
        if (type.equals("mysql") || type.equals("mariadb")) {
            StringBuilder url = new StringBuilder("jdbc:mariadb://")
                    .append(cfg.getString("host", "127.0.0.1")).append(':')
                    .append(cfg.getInt("port", 3306)).append('/')
                    .append(cfg.getString("database", "mooncore"));
            ConfigurationSection props = cfg.getConfigurationSection("properties");
            if (props != null && !props.getKeys(false).isEmpty()) {
                url.append('?');
                boolean first = true;
                for (String key : props.getKeys(false)) {
                    if (!first) url.append('&');
                    url.append(key).append('=').append(props.getString(key));
                    first = false;
                }
            }
            hc.setJdbcUrl(url.toString());
            hc.setUsername(cfg.getString("username", "root"));
            hc.setPassword(cfg.getString("password", ""));
            ConfigurationSection pool = cfg.getConfigurationSection("pool");
            maxPool = pool != null ? pool.getInt("maximum-pool-size", 10) : 10;
            hc.setMaximumPoolSize(maxPool);
            hc.setMinimumIdle(pool != null ? pool.getInt("minimum-idle", 2) : 2);
            hc.setMaxLifetime(pool != null ? pool.getLong("max-lifetime-ms", 1_800_000) : 1_800_000);
        } else {
            // SQLite : un seul fichier, un seul écrivain → pool de taille 1 (pas de "database is locked").
            if (!dataFolder.exists()) dataFolder.mkdirs();
            File dbFile = new File(dataFolder, cfg.getString("sqlite-file", "data.db"));
            hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath().replace('\\', '/'));
            hc.setConnectionInitSql("PRAGMA busy_timeout=5000");
            maxPool = 1;
            hc.setMaximumPoolSize(1);
            hc.setMinimumIdle(1);
        }
        hc.setConnectionTimeout(10_000);

        HikariDataSource ds = new HikariDataSource(hc);
        ExecutorService exec = Executors.newFixedThreadPool(maxPool, namedFactory());
        return new Database(ds, exec, logger);
    }

    private static ThreadFactory namedFactory() {
        AtomicInteger counter = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, "MoonCore-DB-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }

    // ---- Synchrone (démarrage / migrations uniquement) ----

    public void execute(SqlConsumer action) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            action.accept(c);
        }
    }

    public <T> T query(SqlFunction<T> function) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return function.apply(c);
        }
    }

    // ---- Asynchrone (chemin normal) ----

    public CompletableFuture<Void> executeAsync(SqlConsumer action) {
        return CompletableFuture.runAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                action.accept(c);
            } catch (SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    /**
     * Écriture asynchrone « fire-and-forget » : toute erreur SQL est <b>journalisée</b>
     * au lieu d'être silencieusement perdue. À utiliser quand l'appelant n'attend ni
     * ne gère le résultat (cache write-behind, incréments offline, etc.).
     */
    public CompletableFuture<Void> fireAndForget(SqlConsumer action) {
        return executeAsync(action).exceptionally(t -> {
            if (logger != null) logger.error("Écriture asynchrone (fire-and-forget) échouée", t);
            else t.printStackTrace();
            return null;
        });
    }

    public <T> CompletableFuture<T> queryAsync(SqlFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = dataSource.getConnection()) {
                return function.apply(c);
            } catch (SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, executor);
    }

    public Connection rawConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean isHealthy() {
        return dataSource.isRunning() && !dataSource.isClosed();
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
