package com.mooncore.modules.progression;

import com.mooncore.api.progression.PlayerTierUpEvent;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.reward.RewardService;
import com.mooncore.command.sub.ProgressionSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProgressionManager : tiers saisonniers 1→N, XP, déblocages (gating) et récompenses de
 * palier. Expose {@link ProgressionService} ; émet {@link PlayerTierUpEvent} à chaque montée.
 */
@ModuleInfo(id = "progression", name = "ProgressionManager", softDepends = {"reward", "statistics"})
public final class ProgressionModule extends AbstractModule implements ProgressionService {

    private final java.util.Map<UUID, ProgressionData> cache = new ConcurrentHashMap<>();
    private ProgressionStore store;
    private TierTable tierTable;
    private String seasonId;
    private int xpPerMobKill;
    private int xpPerPlayerKill;
    private BukkitTask autoSaveTask;

    @Override
    protected void onEnable() throws Exception {
        this.store = new ProgressionStore(data().database());
        data().applyMigrations(ProgressionStore.migrations());

        this.seasonId = plugin().getConfig().getString("core.season-id", "season-1");
        loadConfig();

        services().register(ProgressionService.class, this);
        registerListener(new ProgressionListener(this));
        plugin().rootCommand().register(new ProgressionSubCommand(this));

        Bukkit.getOnlinePlayers().forEach(p -> load(p.getUniqueId()));

        long ticks = plugin().getConfig().getLong("persistence.auto-save-interval-seconds", 300) * 20L;
        autoSaveTask = schedulers().asyncTimer(this::flushAll, ticks, ticks);
    }

    @Override
    protected void onDisable() {
        if (autoSaveTask != null) autoSaveTask.cancel();
        flushAll();
        cache.clear();
        services().unregister(ProgressionService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
    }

    private void loadConfig() {
        ConfigurationSection cfg = moduleConfig();
        this.xpPerMobKill = cfg.getInt("xp-sources.mob-kill", 1);
        this.xpPerPlayerKill = cfg.getInt("xp-sources.player-kill", 5);

        List<TierTable.Tier> tiers = new ArrayList<>();
        ConfigurationSection tiersSec = cfg.getConfigurationSection("tiers");
        if (tiersSec != null) {
            for (String key : tiersSec.getKeys(false)) {
                int level;
                try { level = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
                ConfigurationSection t = tiersSec.getConfigurationSection(key);
                if (t == null) continue;
                long xpReq = t.getLong("xp-required", 0);
                Set<String> unlocks = new HashSet<>(t.getStringList("unlocks"));
                String reward = t.getString("reward", "");
                tiers.add(new TierTable.Tier(level, xpReq, unlocks, reward.isBlank() ? null : reward));
            }
        }
        this.tierTable = new TierTable(tiers);
    }

    // ---- Chargement / sauvegarde ----

    public void load(UUID uuid) {
        schedulers().async(() -> {
            try {
                cache.put(uuid, store.loadOrCreate(uuid, seasonId));
            } catch (Exception e) {
                log().error("Échec chargement progression " + uuid, e);
            }
        });
    }

    public void unload(UUID uuid) {
        ProgressionData d = cache.remove(uuid);
        if (d != null && d.isDirty()) {
            schedulers().async(() -> saveQuietly(d));
        }
    }

    private void flushAll() {
        for (ProgressionData d : cache.values()) {
            if (d.isDirty()) {
                d.clearDirty();
                saveQuietly(d);
            }
        }
    }

    private void saveQuietly(ProgressionData d) {
        try {
            store.save(d, seasonId);
        } catch (Exception e) {
            log().error("Échec sauvegarde progression " + d.uuid(), e);
            d.markDirty();
        }
    }

    // ---- ProgressionService ----

    @Override
    public int tier(UUID player) {
        ProgressionData d = cache.get(player);
        return d != null ? d.tier() : tierTable.minLevel();
    }

    @Override
    public long xp(UUID player) {
        ProgressionData d = cache.get(player);
        return d != null ? d.xp() : 0;
    }

    @Override
    public long nextTierXp(UUID player) {
        return tierTable.nextTierXp(tier(player));
    }

    @Override
    public void addXp(UUID player, long amount, String reason) {
        if (amount == 0) return;
        ProgressionData d = cache.get(player);
        if (d != null) {
            applyXp(d, amount);
        } else {
            // Joueur hors-ligne : modification best-effort sans récompense ni event.
            schedulers().async(() -> {
                try {
                    ProgressionData od = store.loadOrCreate(player, seasonId);
                    od.addXp(amount);
                    od.setTier(tierTable.tierForXp(od.xp()));
                    saveQuietly(od);
                } catch (Exception e) {
                    log().error("Échec addXp offline " + player, e);
                }
            });
        }
    }

    private void applyXp(ProgressionData d, long amount) {
        int oldTier = d.tier();
        d.addXp(amount);
        int newTier = tierTable.tierForXp(d.xp());
        if (newTier != oldTier) {
            d.setTier(newTier);
            if (newTier > oldTier) handleTierUp(d.uuid(), oldTier, newTier);
        }
    }

    private void handleTierUp(UUID uuid, int oldTier, int newTier) {
        eventBus().post(new PlayerTierUpEvent(uuid, oldTier, newTier));
        Player p = Bukkit.getPlayer(uuid);
        RewardService rewards = services().get(RewardService.class).orElse(null);
        for (int level = oldTier + 1; level <= newTier; level++) {
            TierTable.Tier t = tierTable.tier(level);
            if (t != null && t.rewardId() != null && rewards != null && p != null) {
                rewards.give(p, t.rewardId());
            }
        }
        if (p != null) {
            p.sendMessage(plugin().configManager().prefixed("progression-tierup", "tier", String.valueOf(newTier)));
            p.showTitle(net.kyori.adventure.title.Title.title(
                    Text.mm("<gradient:#8a2be2:#c77dff>Tier " + newTier + "</gradient>"),
                    Text.mm("<gray>Nouveau palier débloqué !</gray>")));
        }
    }

    @Override
    public boolean isUnlocked(UUID player, String feature) {
        return tierTable.isUnlocked(tier(player), feature);
    }

    @Override
    public int maxTier() {
        return tierTable.maxLevel();
    }

    // ---- Accès commande / listener ----

    public TierTable tierTable() { return tierTable; }
    public int xpPerMobKill() { return xpPerMobKill; }
    public int xpPerPlayerKill() { return xpPerPlayerKill; }

    public void setTier(UUID uuid, int level) {
        ProgressionData d = cache.get(uuid);
        if (d == null) return;
        TierTable.Tier t = tierTable.tier(level);
        if (t != null) {
            d.setXp(Math.max(d.xp(), t.xpRequired()));
            d.setTier(level);
        }
    }

    public Set<String> unlocks(UUID uuid) {
        return tierTable.unlocksUpTo(tier(uuid));
    }
}
