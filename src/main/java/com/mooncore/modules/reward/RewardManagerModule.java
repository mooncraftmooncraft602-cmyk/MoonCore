package com.mooncore.modules.reward;

import com.mooncore.api.economy.EconomyService;
import com.mooncore.api.reward.ItemSpec;
import com.mooncore.api.reward.Reward;
import com.mooncore.api.reward.RewardAction;
import com.mooncore.api.reward.RewardService;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.sub.RewardSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RewardManager : moteur de récompenses data-driven. Charge les récompenses depuis
 * {@code content/rewards/*.yml} et les octroie (items, argent, XP, commandes, stats,
 * messages). Anti double-claim via {@link RewardClaimStore}. Expose {@link RewardService}.
 */
@ModuleInfo(id = "reward", name = "RewardManager", softDepends = {"economy-balancer", "statistics"})
public final class RewardManagerModule extends AbstractModule implements RewardService {

    private final Map<String, Reward> registry = new ConcurrentHashMap<>();
    private RewardClaimStore claimStore;

    @Override
    protected void onEnable() throws Exception {
        this.claimStore = new RewardClaimStore(data().database());
        data().applyMigrations(RewardClaimStore.migrations());

        loadRewards();

        services().register(RewardService.class, this);
        plugin().rootCommand().register(new RewardSubCommand(this));
    }

    @Override
    protected void onDisable() {
        services().unregister(RewardService.class);
        registry.clear();
    }

    @Override
    protected void onReload() {
        registry.clear();
        loadRewards();
    }

    private void loadRewards() {
        File dir = new File(plugin().getDataFolder(), "content/rewards");
        if (!dir.exists()) {
            dir.mkdirs();
            if (plugin().getResource("content/rewards/example.yml") != null) {
                plugin().saveResource("content/rewards/example.yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        int count = 0;
        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection rewards = yml.getConfigurationSection("rewards");
            if (rewards == null) continue;
            for (String id : rewards.getKeys(false)) {
                List<Map<?, ?>> actions = yml.getMapList("rewards." + id + ".actions");
                registry.put(id, RewardParser.parse(id, actions));
                count++;
            }
        }
        log().info("RewardManager : " + count + " récompense(s) chargée(s).");
    }

    // ---- RewardService ----

    @Override
    public void give(Player player, Reward reward) {
        if (reward == null) return;
        for (RewardAction action : reward.actions()) {
            applyAction(player, reward.id(), action);
        }
    }

    @Override
    public boolean give(Player player, String rewardId) {
        Reward r = registry.get(rewardId);
        if (r == null) return false;
        give(player, r);
        return true;
    }

    @Override
    public CompletableFuture<Boolean> claimOnce(Player player, String rewardId, String source) {
        Reward r = registry.get(rewardId);
        if (r == null) return CompletableFuture.completedFuture(false);
        return claimStore.tryClaim(player.getUniqueId(), rewardId, source).thenApply(claimed -> {
            if (claimed) {
                schedulers().sync(() -> {
                    if (player.isOnline()) give(player, r);
                });
            }
            return claimed;
        });
    }

    @Override
    public Reward reward(String id) {
        return registry.get(id);
    }

    public java.util.Collection<String> rewardIds() {
        return registry.keySet();
    }

    // ---- Exécution des actions (thread principal) ----

    private void applyAction(Player player, String rewardId, RewardAction action) {
        switch (action.type()) {
            case ITEM -> giveItem(player, action.item());
            case MONEY -> services().get(EconomyService.class)
                    .ifPresent(e -> e.deposit(player.getUniqueId(), action.amount(), "reward:" + rewardId));
            case XP -> player.giveExp((int) Math.round(action.amount()));
            case COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    action.text().replace("%player%", player.getName()));
            case MESSAGE -> player.sendMessage(Text.mm(action.text()));
            case BROADCAST -> Bukkit.broadcast(Text.mm(action.text()));
            case STAT -> services().get(StatisticsService.class)
                    .ifPresent(s -> s.increment(player.getUniqueId(), action.text(),
                            (long) action.amount(), "reward:" + rewardId));
        }
    }

    private void giveItem(Player player, ItemSpec spec) {
        if (spec == null) return;
        Material mat = Material.matchMaterial(spec.material());
        if (mat == null) {
            log().warn("Matériau de récompense inconnu : " + spec.material());
            return;
        }
        ItemStack stack = new ItemStack(mat, spec.amount());
        if (spec.name() != null || !spec.lore().isEmpty()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                // Matériau sans métadonnées (AIR/CAVE_AIR/VOID_AIR…) : name/lore inapplicables.
                log().warn("Récompense « " + spec.material() + " » : matériau sans métadonnées, name/lore ignorés.");
            } else {
                if (spec.name() != null) meta.displayName(Text.mm(spec.name()));
                if (!spec.lore().isEmpty()) {
                    List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                    spec.lore().forEach(line -> lore.add(Text.mm(line)));
                    meta.lore(lore);
                }
                stack.setItemMeta(meta);
            }
        }
        // Dépose dans l'inventaire ; le surplus tombe au sol.
        player.getInventory().addItem(stack).values()
                .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
    }
}
