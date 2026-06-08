package com.mooncore.modules.leaderboard;

import com.mooncore.api.leaderboard.LeaderboardEntry;
import com.mooncore.api.leaderboard.LeaderboardService;
import com.mooncore.command.sub.LeaderboardSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LeaderboardManager : classements configurables, calculés en asynchrone et servis depuis un
 * cache de snapshots. S'appuie sur les données de Statistics. Expose {@link LeaderboardService}.
 */
@ModuleInfo(id = "leaderboard", name = "LeaderboardManager", softDepends = {"statistics"})
public final class LeaderboardManagerModule extends AbstractModule implements LeaderboardService {

    private final Map<String, LeaderboardDefinition> boards = new LinkedHashMap<>();
    private final Map<String, List<LeaderboardEntry>> snapshots = new ConcurrentHashMap<>();

    private LeaderboardStore store;
    private String seasonId;
    private BukkitTask recomputeTask;

    @Override
    protected void onEnable() {
        this.store = new LeaderboardStore(data().database());
        this.seasonId = plugin().getConfig().getString("core.season-id", "season-1");

        loadConfig();

        services().register(LeaderboardService.class, this);
        plugin().rootCommand().register(new LeaderboardSubCommand(this));

        long interval = moduleConfig().getLong("recompute-interval-seconds", 300) * 20L;
        recomputeTask = schedulers().asyncTimer(this::recomputeAll, 100L, interval);
    }

    @Override
    protected void onDisable() {
        if (recomputeTask != null) recomputeTask.cancel();
        services().unregister(LeaderboardService.class);
        snapshots.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        boards.clear();
        loadConfig();
        snapshots.keySet().retainAll(boards.keySet()); // purge les snapshots des boards retirés de la config
        recomputeAll();
    }

    private void loadConfig() {
        ConfigurationSection sec = moduleConfig().getConfigurationSection("boards");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection b = sec.getConfigurationSection(id);
            if (b == null) continue;
            boards.put(id, new LeaderboardDefinition(
                    id,
                    b.getString("title", id),
                    b.getString("source", id),
                    Math.max(1, b.getInt("size", 10))));
        }
    }

    /** Recalcule tous les classements en asynchrone et met à jour les snapshots. */
    public void recomputeAll() {
        for (LeaderboardDefinition def : boards.values()) {
            store.topAsync(def, seasonId)
                    .thenAccept(list -> snapshots.put(def.id(), list))
                    .exceptionally(ex -> {
                        log().error("Échec du calcul du classement " + def.id(), ex);
                        return null;
                    });
        }
    }

    // ---- LeaderboardService ----

    @Override
    public Set<String> boards() {
        return boards.keySet();
    }

    @Override
    public List<LeaderboardEntry> top(String boardId) {
        return snapshots.getOrDefault(boardId, List.of());
    }

    @Override
    public String title(String boardId) {
        LeaderboardDefinition def = boards.get(boardId);
        return def != null ? def.title() : boardId;
    }
}
