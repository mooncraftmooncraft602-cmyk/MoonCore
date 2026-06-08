package com.mooncore.modules.missions;

import com.mooncore.api.mission.Mission;
import com.mooncore.api.mission.MissionScope;
import com.mooncore.api.mission.MissionService;
import com.mooncore.api.mission.ObjectiveType;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.reward.RewardService;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.sub.MissionsSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Système de missions journalières / hebdomadaires / saisonnières (moteur unifié couvrant
 * DailyMissionManager + WeeklyMissionManager). Progression par événements, réinitialisation
 * par période, récompenses via RewardManager et XP de progression. Expose {@link MissionService}.
 */
@ModuleInfo(id = "missions", name = "MissionManager", softDepends = {"reward", "progression", "statistics"})
public final class MissionModule extends AbstractModule implements MissionService {

    private final Map<String, Mission> byId = new ConcurrentHashMap<>();
    private final Map<MissionScope, List<Mission>> byScope = new ConcurrentHashMap<>();
    private final Map<ObjectiveType, List<Mission>> byType = new EnumMap<>(ObjectiveType.class);
    private final Map<UUID, MissionProgress> cache = new ConcurrentHashMap<>();

    private MissionStore store;
    private String seasonId;
    private BukkitTask autoSaveTask;

    @Override
    protected void onEnable() throws Exception {
        this.store = new MissionStore(data().database());
        data().applyMigrations(MissionStore.migrations());
        this.seasonId = plugin().getConfig().getString("core.season-id", "season-1");

        loadMissions();

        services().register(MissionService.class, this);
        registerListener(new MissionListener(this));
        plugin().rootCommand().register(new MissionsSubCommand(this));

        Bukkit.getOnlinePlayers().forEach(p -> load(p.getUniqueId()));

        long ticks = plugin().getConfig().getLong("persistence.auto-save-interval-seconds", 300) * 20L;
        autoSaveTask = schedulers().asyncTimer(this::flushAll, ticks, ticks);
    }

    @Override
    protected void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        flushAll();
        cache.clear();
        services().unregister(MissionService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadMissions();
    }

    private void loadMissions() {
        byId.clear();
        byScope.clear();
        byType.clear();

        File dir = new File(plugin().getDataFolder(), "content/missions");
        if (!dir.exists()) {
            dir.mkdirs();
            if (plugin().getResource("content/missions/example.yml") != null) {
                plugin().saveResource("content/missions/example.yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = yml.getConfigurationSection("missions");
            if (sec == null) continue;
            for (String id : sec.getKeys(false)) {
                ConfigurationSection m = sec.getConfigurationSection(id);
                if (m == null) continue;
                Mission mission = parseMission(id, m);
                if (mission == null) continue;
                byId.put(id, mission);
                byScope.computeIfAbsent(mission.scope(), k -> new ArrayList<>()).add(mission);
                byType.computeIfAbsent(mission.type(), k -> new ArrayList<>()).add(mission);
            }
        }
        log().info("MissionManager : " + byId.size() + " mission(s) chargée(s).");
    }

    private Mission parseMission(String id, ConfigurationSection m) {
        try {
            MissionScope scope = MissionScope.valueOf(m.getString("scope", "DAILY").toUpperCase(Locale.ROOT));
            ObjectiveType type = ObjectiveType.valueOf(m.getString("type", "MINE_BLOCK").toUpperCase(Locale.ROOT));
            int target = m.getInt("target", 1);
            String reward = m.getString("reward", "");
            String desc = m.getString("description", id);
            long xp = m.getLong("progression-xp", 0);
            return new Mission(id, scope, type, target, reward.isBlank() ? null : reward, desc, xp);
        } catch (IllegalArgumentException e) {
            log().warn("Mission invalide ignorée : " + id + " (" + e.getMessage() + ")");
            return null;
        }
    }

    // ---- Chargement / sauvegarde ----

    public void load(UUID uuid) {
        Set<String> keys = currentPeriodKeys();
        schedulers().async(() -> {
            try {
                List<MissionStore.Row> rows = store.load(uuid, keys);
                MissionProgress mp = new MissionProgress();
                for (MissionStore.Row r : rows) {
                    mp.set(r.missionId(), r.count());
                    if (r.claimed()) mp.setClaimed(r.missionId());
                }
                long now = System.currentTimeMillis();
                for (MissionScope s : MissionScope.values()) mp.setPeriodKey(s, MissionPeriod.key(s, now, seasonId));
                mp.clearDirty();
                cache.put(uuid, mp);
            } catch (Exception e) {
                log().error("Échec chargement missions " + uuid, e);
            }
        });
    }

    public void unload(UUID uuid) {
        MissionProgress mp = cache.remove(uuid);
        if (mp != null && mp.isDirty()) saveProgress(uuid, mp);
    }

    private void flushAll() {
        cache.forEach((uuid, mp) -> {
            if (mp.isDirty()) {
                mp.clearDirty();
                saveProgress(uuid, mp);
            }
        });
    }

    private void saveProgress(UUID uuid, MissionProgress mp) {
        ensureCurrentPeriod(mp); // ne jamais persister la progression d'une période sous la clé d'une autre
        long now = System.currentTimeMillis();
        Set<String> ids = new HashSet<>(mp.counts().keySet());
        ids.addAll(mp.claimedSet());
        for (String id : ids) {
            Mission mission = byId.get(id);
            if (mission == null) continue;
            String key = MissionPeriod.key(mission.scope(), now, seasonId);
            store.save(uuid, id, key, mp.count(id), mp.isClaimed(id));
        }
    }

    private Set<String> currentPeriodKeys() {
        long now = System.currentTimeMillis();
        Set<String> keys = new HashSet<>();
        for (MissionScope s : MissionScope.values()) keys.add(MissionPeriod.key(s, now, seasonId));
        return keys;
    }

    /** Progression en cache, après réinitialisation des missions dont la période a roulé (rollover en ligne). */
    private MissionProgress current(UUID uuid) {
        MissionProgress mp = cache.get(uuid);
        if (mp != null) ensureCurrentPeriod(mp);
        return mp;
    }

    /**
     * Réinitialise en mémoire les missions dont la clé de période a changé depuis le chargement
     * (un joueur resté connecté au passage de minuit/semaine UTC) : sinon la progression de l'ancienne
     * période serait reportée et sauvée sous la clé de la nouvelle.
     */
    private void ensureCurrentPeriod(MissionProgress mp) {
        long now = System.currentTimeMillis();
        for (MissionScope scope : MissionScope.values()) {
            String cur = MissionPeriod.key(scope, now, seasonId);
            String loaded = mp.periodKey(scope);
            if (loaded == null) { mp.setPeriodKey(scope, cur); continue; }
            if (!loaded.equals(cur)) {
                for (Mission m : byScope.getOrDefault(scope, List.of())) mp.reset(m.id());
                mp.setPeriodKey(scope, cur);
                mp.markDirty();
            }
        }
    }

    // ---- Suivi (appelé par le listener) ----

    public void track(UUID uuid, ObjectiveType type, int amount) {
        MissionProgress mp = current(uuid);
        if (mp == null) return;
        List<Mission> missions = byType.get(type);
        if (missions == null) return;
        for (Mission m : missions) {
            if (!mp.isClaimed(m.id())) {
                mp.add(m.id(), amount, m.target());
            }
        }
    }

    // ---- MissionService ----

    @Override
    public List<Mission> missions(MissionScope scope) {
        return byScope.getOrDefault(scope, List.of());
    }

    @Override
    public int progress(UUID player, String missionId) {
        MissionProgress mp = current(player);
        return mp != null ? mp.count(missionId) : 0;
    }

    @Override
    public boolean isComplete(UUID player, String missionId) {
        Mission m = byId.get(missionId);
        return m != null && progress(player, missionId) >= m.target();
    }

    @Override
    public boolean isClaimed(UUID player, String missionId) {
        MissionProgress mp = current(player);
        return mp != null && mp.isClaimed(missionId);
    }

    @Override
    public CompletableFuture<Boolean> claim(Player player, String missionId) {
        Mission m = byId.get(missionId);
        MissionProgress mp = current(player.getUniqueId());
        if (m == null || mp == null) return CompletableFuture.completedFuture(false);
        if (mp.isClaimed(missionId) || mp.count(missionId) < m.target()) {
            return CompletableFuture.completedFuture(false);
        }
        mp.setClaimed(missionId);
        saveProgress(player.getUniqueId(), mp);

        // Récompense + XP de progression + stat.
        if (m.rewardId() != null) {
            services().get(RewardService.class).ifPresent(r -> r.give(player, m.rewardId()));
        }
        if (m.progressionXp() > 0) {
            services().get(ProgressionService.class)
                    .ifPresent(pr -> pr.addXp(player.getUniqueId(), m.progressionXp(), "mission:" + missionId));
        }
        services().get(StatisticsService.class)
                .ifPresent(s -> s.increment(player.getUniqueId(), StatKeys.MISSIONS_COMPLETED, 1, "mission"));
        return CompletableFuture.completedFuture(true);
    }

    public Mission mission(String id) { return byId.get(id); }
}
