package com.mooncore.modules.stats;

import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.sub.StatsSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * StatisticsManager : profils joueurs (préchargés au join, sauvegardés en write-behind),
 * compteurs de stats (modèle EAV) et historique d'audit. Expose {@link StatisticsService}.
 */
@ModuleInfo(id = "statistics", name = "StatisticsManager")
public final class StatisticsModule extends AbstractModule implements StatisticsService {

    private final Map<UUID, PlayerProfile> online = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<StatisticsStore.HistoryRow> historyBuffer = new ConcurrentLinkedQueue<>();

    private StatisticsStore store;
    private String seasonId;
    private boolean historyEnabled;
    private int historyBatchSize;

    private BukkitTask autoSaveTask;
    private BukkitTask playtimeTask;

    @Override
    protected void onEnable() throws Exception {
        this.store = new StatisticsStore(data().database());
        data().applyMigrations(StatisticsStore.migrations());

        this.seasonId = plugin().getConfig().getString("core.season-id", "season-1");
        this.historyEnabled = moduleConfig().getBoolean("history.enabled", true);
        this.historyBatchSize = plugin().getConfig().getInt("persistence.batch-size", 200);

        services().register(StatisticsService.class, this);
        StatsListener listener = new StatsListener(this);
        registerListener(listener);
        plugin().rootCommand().register(new StatsSubCommand(this));

        // Abonnements EventBus (consommation découplée des autres modules).
        eventBus().subscribe(com.mooncore.modules.antiafk.PlayerAfkChangeEvent.class, listener::onAfkChange);
        eventBus().subscribe(com.mooncore.api.economy.AbnormalGainEvent.class, listener::onAbnormalGain);

        // Recharge les joueurs déjà connectés (reload à chaud).
        Bukkit.getOnlinePlayers().forEach(p -> loadProfile(p.getUniqueId(), p.getName()));

        long saveTicks = plugin().getConfig().getLong("persistence.auto-save-interval-seconds", 300) * 20L;
        autoSaveTask = schedulers().asyncTimer(this::flushAll, saveTicks, saveTicks);
        playtimeTask = schedulers().syncTimer(this::tickPlaytime, 1200L, 1200L); // toutes les 60 s
    }

    @Override
    protected void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (playtimeTask != null) playtimeTask.cancel();
        flushAll();              // dernier flush (best-effort)
        online.clear();
        services().unregister(StatisticsService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        this.historyEnabled = moduleConfig().getBoolean("history.enabled", true);
    }

    // ---- Connexion / déconnexion (appelé par StatsListener) ----

    public void loadProfile(UUID uuid, String name) {
        schedulers().async(() -> {
            try {
                PlayerProfile profile = store.loadOrCreate(uuid, name, seasonId, System.currentTimeMillis());
                // Re-synchronise pour éviter la course avec unloadProfile : si le joueur
                // s'est déconnecté pendant le chargement, on n'insère pas (profil mort/fuite).
                schedulers().sync(() -> {
                    if (Bukkit.getPlayer(uuid) != null) {
                        online.put(uuid, profile);
                        log().debug(() -> "Profil chargé : " + name);
                    }
                });
            } catch (Exception e) {
                log().error("Échec du chargement du profil de " + name, e);
            }
        });
    }

    public void unloadProfile(UUID uuid) {
        PlayerProfile profile = online.remove(uuid);
        if (profile == null) return;
        schedulers().async(() -> saveQuietly(profile));
        flushHistory();
    }

    private void tickPlaytime() {
        for (UUID id : online.keySet()) {
            PlayerProfile p = online.get(id);
            if (p != null) p.addPlaytimeSeconds(60);
        }
    }

    private void flushAll() {
        for (PlayerProfile p : online.values()) {
            if (p.isDirty()) {
                p.clearDirty();
                saveQuietly(p);
            }
        }
        flushHistory();
    }

    private void saveQuietly(PlayerProfile p) {
        try {
            store.saveProfile(p);
        } catch (Exception e) {
            log().error("Échec de sauvegarde du profil " + p.name(), e);
            p.markDirty(); // re-tentera au prochain flush
        }
    }

    private void flushHistory() {
        if (!historyEnabled || historyBuffer.isEmpty()) return;
        java.util.List<StatisticsStore.HistoryRow> batch = new java.util.ArrayList<>();
        StatisticsStore.HistoryRow row;
        while (batch.size() < historyBatchSize && (row = historyBuffer.poll()) != null) {
            batch.add(row);
        }
        if (batch.isEmpty()) return;
        schedulers().async(() -> {
            try {
                store.flushHistory(batch);
            } catch (Exception e) {
                log().error("Échec d'écriture de l'historique des stats", e);
            }
        });
    }

    // ---- StatisticsService ----

    @Override
    public long get(UUID player, String statKey) {
        PlayerProfile p = online.get(player);
        return p != null ? p.get(statKey) : 0L;
    }

    @Override
    public void increment(UUID player, String statKey, long amount, String reason) {
        if (amount == 0) return;
        PlayerProfile p = online.get(player);
        if (p != null) {
            p.add(statKey, amount);
        } else {
            store.incrementOffline(player, seasonId, statKey, amount);
        }
        if (historyEnabled) {
            historyBuffer.add(new StatisticsStore.HistoryRow(player, statKey, amount, reason));
        }
    }

    @Override
    public void set(UUID player, String statKey, long value, String reason) {
        PlayerProfile p = online.get(player);
        if (p != null) {
            p.set(statKey, value);
        } else {
            store.setOffline(player, seasonId, statKey, value);
        }
        if (historyEnabled) {
            historyBuffer.add(new StatisticsStore.HistoryRow(player, statKey, value, reason + ":set"));
        }
    }

    @Override
    public boolean isLoaded(UUID player) {
        return online.containsKey(player);
    }

    @Override
    public Map<String, Long> snapshot(UUID player) {
        PlayerProfile p = online.get(player);
        return p != null ? new HashMap<>(p.stats()) : Map.of();
    }

    @Override
    public CompletableFuture<Map<String, Long>> loadAsync(UUID player) {
        PlayerProfile p = online.get(player);
        if (p != null) return CompletableFuture.completedFuture(new HashMap<>(p.stats()));
        return store.loadStatsAsync(player, seasonId);
    }

    public PlayerProfile profile(UUID uuid) { return online.get(uuid); }
}
