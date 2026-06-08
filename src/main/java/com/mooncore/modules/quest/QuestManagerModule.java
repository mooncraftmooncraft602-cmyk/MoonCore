package com.mooncore.modules.quest;

import com.mooncore.api.mission.ObjectiveType;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.reward.RewardService;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.sub.QuestSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QuestManager : quêtes saisonnières scénarisées (étapes séquentielles), conditionnées par
 * le tier de progression, avec récompenses d'étape et finale. Progression via événements.
 */
@ModuleInfo(id = "quest", name = "QuestManager", softDepends = {"progression", "reward", "statistics"})
public final class QuestManagerModule extends AbstractModule {

    private final Map<String, Quest> registry = new LinkedHashMap<>();
    private final Map<UUID, Map<String, QuestProgress>> cache = new ConcurrentHashMap<>();
    private QuestStore store;
    private BukkitTask autoSaveTask;

    @Override
    protected void onEnable() {
        this.store = new QuestStore(data().database());
        try {
            data().applyMigrations(QuestStore.migrations());
        } catch (Exception e) {
            log().error("Échec migration quêtes", e);
        }
        loadQuests();
        registerListener(new QuestListener(this));
        plugin().rootCommand().register(new QuestSubCommand(this));
        Bukkit.getOnlinePlayers().forEach(p -> load(p.getUniqueId()));

        long ticks = plugin().getConfig().getLong("persistence.auto-save-interval-seconds", 300) * 20L;
        autoSaveTask = schedulers().asyncTimer(this::flushAll, ticks, ticks);
    }

    @Override
    protected void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        flushAll();
        cache.clear();
    }

    @Override
    protected void onReload() {
        registry.clear();
        loadQuests();
    }

    private void loadQuests() {
        File dir = new File(plugin().getDataFolder(), "content/quests");
        if (!dir.exists()) {
            dir.mkdirs();
            if (plugin().getResource("content/quests/example.yml") != null) {
                plugin().saveResource("content/quests/example.yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = yml.getConfigurationSection("quests");
            if (sec == null) continue;
            for (String id : sec.getKeys(false)) {
                ConfigurationSection q = sec.getConfigurationSection(id);
                if (q == null) continue;
                Quest quest = parseQuest(id, q);
                if (quest != null) registry.put(id, quest);
            }
        }
        log().info("QuestManager : " + registry.size() + " quête(s).");
    }

    private Quest parseQuest(String id, ConfigurationSection q) {
        List<QuestStep> steps = new ArrayList<>();
        for (Map<?, ?> m : q.getMapList("steps")) {
            Object type = m.get("type");
            if (type == null) continue;
            try {
                Object desc = m.get("description");
                Object reward = m.get("reward");
                steps.add(new QuestStep(
                        desc != null ? desc.toString() : id,
                        ObjectiveType.valueOf(type.toString().toUpperCase(Locale.ROOT)),
                        m.get("target") instanceof Number n ? n.intValue() : 1,
                        emptyToNull(reward != null ? reward.toString() : "")));
            } catch (IllegalArgumentException ignored) {}
        }
        return new Quest(id, q.getString("display-name", id), q.getInt("required-tier", 1),
                emptyToNull(q.getString("final-reward", "")), q.getLong("progression-xp", 0), steps);
    }

    // ---- Chargement ----

    public void load(UUID uuid) {
        schedulers().async(() -> {
            try {
                Map<String, QuestProgress> map = new ConcurrentHashMap<>();
                for (QuestStore.Row r : store.load(uuid)) {
                    map.put(r.questId(), new QuestProgress(r.step(), r.progress(), r.completed()));
                }
                cache.put(uuid, map);
            } catch (Exception e) {
                log().error("Échec chargement quêtes " + uuid, e);
            }
        });
    }

    public void unload(UUID uuid) {
        Map<String, QuestProgress> map = cache.remove(uuid);
        if (map != null) map.forEach((qid, qp) -> { if (qp.isDirty()) store.save(uuid, qid, qp.step(), qp.progress(), qp.completed()); });
    }

    private void flushAll() {
        cache.forEach((uuid, map) -> map.forEach((qid, qp) -> {
            if (qp.isDirty()) {
                qp.clearDirty();
                store.save(uuid, qid, qp.step(), qp.progress(), qp.completed());
            }
        }));
    }

    // ---- Suivi ----

    public void track(UUID uuid, ObjectiveType type, int amount) {
        Map<String, QuestProgress> map = cache.get(uuid);
        if (map == null) return;
        int tier = services().get(ProgressionService.class).map(p -> p.tier(uuid)).orElse(Integer.MAX_VALUE);
        Player player = Bukkit.getPlayer(uuid);

        for (Quest quest : registry.values()) {
            if (quest.steps().isEmpty()) continue;
            QuestProgress qp = map.computeIfAbsent(quest.id(), k -> QuestProgress.fresh());
            if (qp.completed() || tier < quest.requiredTier()) continue;
            if (qp.step() >= quest.steps().size()) { qp.complete(); continue; }

            QuestStep step = quest.steps().get(qp.step());
            if (step.type() != type) continue;

            if (qp.add(amount, step.target())) {
                handleStepComplete(player, quest, qp, step);
            }
        }
    }

    private void handleStepComplete(Player player, Quest quest, QuestProgress qp, QuestStep step) {
        if (player != null && step.rewardId() != null) {
            services().get(RewardService.class).ifPresent(r -> r.give(player, step.rewardId()));
        }
        if (qp.step() + 1 < quest.steps().size()) {
            qp.advance();
            if (player != null) {
                player.sendMessage(plugin().configManager().prefixed("quest-step-done",
                        "quest", quest.displayName(), "step", String.valueOf(qp.step() + 1)));
            }
        } else {
            qp.complete();
            if (player != null) {
                if (quest.finalRewardId() != null) {
                    services().get(RewardService.class).ifPresent(r -> r.give(player, quest.finalRewardId()));
                }
                if (quest.progressionXp() > 0) {
                    services().get(ProgressionService.class)
                            .ifPresent(pr -> pr.addXp(player.getUniqueId(), quest.progressionXp(), "quest:" + quest.id()));
                }
                services().get(StatisticsService.class)
                        .ifPresent(s -> s.increment(player.getUniqueId(), "quests_completed", 1, "quest"));
                player.sendMessage(plugin().configManager().prefixed("quest-complete", "quest", quest.displayName()));
                player.showTitle(net.kyori.adventure.title.Title.title(
                        Text.mm("<gold>Quête terminée</gold>"), Text.mm("<gray>" + quest.displayName() + "</gray>")));
            }
        }
    }

    // ---- Accès commande ----

    public java.util.Collection<Quest> quests() { return registry.values(); }

    public QuestProgress progressOf(UUID uuid, String questId) {
        Map<String, QuestProgress> map = cache.get(uuid);
        return map == null ? null : map.get(questId);
    }

    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
