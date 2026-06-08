package com.mooncore.modules.home;

import com.mooncore.api.zone.ZoneFlag;
import com.mooncore.api.zone.ZoneService;
import com.mooncore.command.sub.HomeSubCommand;
import com.mooncore.command.sub.SpawnSubCommand;
import com.mooncore.command.sub.TpaSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HomeManager : homes, spawn et tpa natifs (remplace l'existant). Respecte les flags Zone
 * ({@code nohome}, {@code notpa}) et bénéficie automatiquement des frais de téléportation
 * d'EconomyBalancer (via PlayerTeleportEvent). Données persistées (homes) + config (spawn).
 */
@ModuleInfo(id = "home", name = "HomeManager", softDepends = {"zone", "economy-balancer"})
public final class HomeManagerModule extends AbstractModule {

    public enum SetResult { OK, INVALID_NAME, LIMIT_REACHED }

    private record TpaRequest(UUID requester, long expiryMs) {}

    private final Map<UUID, Map<String, Home>> homes = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequest> tpaRequests = new ConcurrentHashMap<>();
    private HomeStore store;
    private int maxHomes;
    private long tpaTtlMs;
    private org.bukkit.scheduler.BukkitTask tpaCleanupTask;

    @Override
    protected void onEnable() throws Exception {
        this.store = new HomeStore(data().database());
        data().applyMigrations(HomeStore.migrations());
        loadConfig();

        registerListener(new HomeListener(this));
        plugin().rootCommand().register(new HomeSubCommand(this));
        plugin().rootCommand().register(new SpawnSubCommand(this));
        plugin().rootCommand().register(new TpaSubCommand(this));

        Bukkit.getOnlinePlayers().forEach(p -> load(p.getUniqueId()));

        // Purge périodique des requêtes TPA expirées (évite l'accumulation mémoire).
        this.tpaCleanupTask = schedulers().syncTimer(this::purgeExpiredTpa, 1200L, 1200L);
    }

    @Override
    protected void onDisable() {
        if (tpaCleanupTask != null) tpaCleanupTask.cancel();
        homes.clear();
        tpaRequests.clear();
    }

    private void purgeExpiredTpa() {
        long now = System.currentTimeMillis();
        tpaRequests.values().removeIf(r -> r.expiryMs() < now);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
    }

    private void loadConfig() {
        this.maxHomes = moduleConfig().getInt("max-homes", 3);
        this.tpaTtlMs = moduleConfig().getLong("tpa-ttl-seconds", 60) * 1000L;
    }

    // ---- Chargement ----

    public void load(UUID uuid) {
        schedulers().async(() -> {
            try {
                Map<String, Home> loaded = new ConcurrentHashMap<>();
                for (Home h : store.load(uuid)) loaded.put(h.name(), h);
                // Retour sur le main thread : ignorer si le joueur s'est déconnecté pendant le chargement,
                // et fusionner avec les homes éventuellement posés entre-temps (au lieu d'écraser).
                schedulers().sync(() -> {
                    if (plugin().getServer().getPlayer(uuid) == null) return;
                    homes.merge(uuid, loaded, (current, fresh) -> {
                        fresh.putAll(current); // un home posé en mémoire pendant le load prime sur la DB
                        return fresh;
                    });
                });
            } catch (Exception e) {
                log().error("Échec chargement homes " + uuid, e);
            }
        });
    }

    public void unload(UUID uuid) {
        homes.remove(uuid);
        tpaRequests.remove(uuid);                                  // requêtes où il est la cible
        tpaRequests.values().removeIf(r -> r.requester().equals(uuid)); // où il est le demandeur
    }

    // ---- Homes ----

    public Map<String, Home> homesOf(UUID uuid) {
        return homes.getOrDefault(uuid, Collections.emptyMap());
    }

    public int maxHomes() { return maxHomes; }

    public SetResult setHome(Player p, String name) {
        if (!HomesLogic.isValidName(name)) return SetResult.INVALID_NAME;
        String key = HomesLogic.normalize(name);
        Map<String, Home> map = homes.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
        boolean exists = map.containsKey(key);
        if (!exists && !HomesLogic.canCreate(map.size(), maxHomes)) return SetResult.LIMIT_REACHED;
        Home home = Home.of(key, p.getLocation());
        map.put(key, home);
        store.save(p.getUniqueId(), home);
        return SetResult.OK;
    }

    public boolean delHome(UUID uuid, String name) {
        String key = HomesLogic.normalize(name);
        Map<String, Home> map = homes.get(uuid);
        if (map == null || map.remove(key) == null) return false;
        store.delete(uuid, key);
        return true;
    }

    public Home getHome(UUID uuid, String name) {
        Map<String, Home> map = homes.get(uuid);
        return map == null ? null : map.get(HomesLogic.normalize(name));
    }

    /** Téléporte au home si autorisé. Retourne un code de message. */
    public String teleportHome(Player p, String name) {
        Home home = getHome(p.getUniqueId(), name);
        if (home == null) return "home-unknown";
        if (zoneFlag(p.getLocation(), ZoneFlag.NO_HOME)) return "home-zone-denied";
        Location loc = home.toLocation();
        if (loc == null) return "home-world-missing";
        p.teleport(loc); // déclenche les frais EconomyBalancer (cause PLUGIN)
        return "home-teleported";
    }

    // ---- Spawn ----

    public Location getSpawn() {
        var c = moduleConfig();
        String world = c.getString("spawn.world", null);
        if (world == null || Bukkit.getWorld(world) == null) return null;
        return new Location(Bukkit.getWorld(world),
                c.getDouble("spawn.x"), c.getDouble("spawn.y"), c.getDouble("spawn.z"),
                (float) c.getDouble("spawn.yaw"), (float) c.getDouble("spawn.pitch"));
    }

    public void setSpawn(Location loc) {
        var c = moduleConfig();
        c.set("spawn.world", loc.getWorld().getName());
        c.set("spawn.x", loc.getX());
        c.set("spawn.y", loc.getY());
        c.set("spawn.z", loc.getZ());
        c.set("spawn.yaw", (double) loc.getYaw());
        c.set("spawn.pitch", (double) loc.getPitch());
        plugin().configManager().saveModuleConfig(id());
    }

    // ---- TPA ----

    public boolean requestTpa(Player requester, Player target) {
        if (zoneFlag(requester.getLocation(), ZoneFlag.NO_TPA)) return false;
        tpaRequests.put(target.getUniqueId(),
                new TpaRequest(requester.getUniqueId(), System.currentTimeMillis() + tpaTtlMs));
        return true;
    }

    /** Accepte la demande en attente : téléporte le demandeur vers la cible. */
    public Player acceptTpa(Player target) {
        TpaRequest req = tpaRequests.remove(target.getUniqueId());
        if (req == null || System.currentTimeMillis() > req.expiryMs()) return null;
        Player requester = Bukkit.getPlayer(req.requester());
        if (requester == null || !requester.isOnline()) return null;
        if (zoneFlag(requester.getLocation(), ZoneFlag.NO_TPA)) return null;
        requester.teleport(target.getLocation());
        return requester;
    }

    public boolean denyTpa(Player target) {
        return tpaRequests.remove(target.getUniqueId()) != null;
    }

    // ---- Helpers ----

    private boolean zoneFlag(Location loc, ZoneFlag flag) {
        return services().get(ZoneService.class).map(z -> z.flag(loc, flag)).orElse(false);
    }
}
