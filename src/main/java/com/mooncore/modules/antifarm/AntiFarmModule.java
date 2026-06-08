package com.mooncore.modules.antifarm;

import com.mooncore.api.team.TeamService;
import com.mooncore.command.sub.AntiFarmSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AntiFarmManager : limite les fermes industrielles sans casser les farms normales.
 * <ul>
 *   <li>limites de spawners par chunk / par joueur / par équipe (compteurs incrémentaux) ;</li>
 *   <li>plafond d'entités par chunk (protection des performances) ;</li>
 *   <li>réduction progressive des rendements au-delà d'un seuil de kills ({@link YieldLimiter}) ;</li>
 *   <li>respect du flag de zone {@code nospawner} ;</li>
 *   <li>alertes admin throttlées.</li>
 * </ul>
 */
// depends ne liste que des MODULES ; data/config sont des services noyau toujours présents.
@ModuleInfo(id = "anti-farm", name = "AntiFarmManager",
        softDepends = {"zone", "anti-afk", "statistics"})
public final class AntiFarmModule extends AbstractModule {

    private final SpawnerRegistry registry = new SpawnerRegistry();
    private SpawnerStore store;
    private YieldLimiter yieldLimiter;
    private BukkitTask cleanupTask;

    // Config
    private int maxPerChunk;
    private int maxPerPlayer;
    private int maxPerTeam;
    private int entityMaxPerChunk;
    private Set<CreatureSpawnEvent.SpawnReason> countedReasons;
    private boolean yieldEnabled;
    private boolean alertEnabled;
    private long alertCooldownMs;

    private volatile long lastAlert;

    @Override
    protected void onEnable() throws Exception {
        this.store = new SpawnerStore(data().database());
        data().applyMigrations(SpawnerStore.migrations());

        loadConfig();

        var loaded = store.loadAll();
        loaded.forEach(registry::add);
        log().info("AntiFarm : " + loaded.size() + " spawner(s) suivi(s).");

        registerListener(new AntiFarmListener(plugin(), this));
        plugin().rootCommand().register(new AntiFarmSubCommand(this));

        // Purge périodique des fenêtres de kills (toutes les 5 min, async).
        cleanupTask = schedulers().asyncTimer(
                () -> yieldLimiter.cleanup(System.currentTimeMillis()),
                6000L, 6000L);
    }

    @Override
    protected void onDisable() {
        if (cleanupTask != null) cleanupTask.cancel();
        registry.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration c = moduleConfig();
        this.maxPerChunk = c.getInt("spawners.max-per-chunk", 8);
        this.maxPerPlayer = c.getInt("spawners.max-per-player", 32);
        this.maxPerTeam = c.getInt("spawners.max-per-team", 64);
        this.entityMaxPerChunk = c.getInt("entities.max-per-chunk", 64);

        this.countedReasons = parseReasons(c.getStringList("entities.count-reasons"));

        this.yieldEnabled = c.getBoolean("yield.enabled", true);
        int soft = c.getInt("yield.soft-cap-kills", 120);
        int hard = c.getInt("yield.hard-cap-kills", 400);
        long window = c.getLong("yield.window-seconds", 60) * 1000L;
        double min = c.getDouble("yield.min-factor", 0.2);
        this.yieldLimiter = new YieldLimiter(soft, hard, window, min);

        this.alertEnabled = c.getBoolean("alerts.enabled", true);
        this.alertCooldownMs = c.getLong("alerts.cooldown-seconds", 30) * 1000L;
    }

    private Set<CreatureSpawnEvent.SpawnReason> parseReasons(java.util.List<String> raw) {
        EnumSet<CreatureSpawnEvent.SpawnReason> set = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        if (raw == null || raw.isEmpty()) {
            set.add(CreatureSpawnEvent.SpawnReason.NATURAL);
            set.add(CreatureSpawnEvent.SpawnReason.SPAWNER);
            set.add(CreatureSpawnEvent.SpawnReason.BREEDING);
            return set;
        }
        for (String s : raw) {
            try {
                set.add(CreatureSpawnEvent.SpawnReason.valueOf(s.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log().warn("Raison de spawn inconnue dans anti-farm.yml : " + s);
            }
        }
        return set;
    }

    // ---- API interne (listener / commande) ----

    public SpawnerRegistry registry() { return registry; }
    public SpawnerStore store() { return store; }
    public YieldLimiter yieldLimiter() { return yieldLimiter; }

    public int maxPerChunk() { return maxPerChunk; }
    public int maxPerPlayer() { return maxPerPlayer; }
    public int maxPerTeam() { return maxPerTeam; }
    public int entityMaxPerChunk() { return entityMaxPerChunk; }
    public boolean yieldEnabled() { return yieldEnabled; }
    public Set<CreatureSpawnEvent.SpawnReason> countedReasons() { return countedReasons; }

    public boolean bypasses(Player p) {
        return p.hasPermission("mooncore.bypass.antifarm");
    }

    public Optional<String> teamOf(UUID player) {
        return services().get(TeamService.class).flatMap(ts -> ts.teamId(player));
    }

    /** Diffuse une alerte aux admins connectés, throttlée globalement. */
    public void alertAdmins(Component message) {
        if (!alertEnabled) return;
        long now = System.currentTimeMillis();
        if (now - lastAlert < alertCooldownMs) return;
        lastAlert = now;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("mooncore.admin.antifarm")) {
                p.sendMessage(message);
            }
        }
        log().info(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(message));
    }
}
