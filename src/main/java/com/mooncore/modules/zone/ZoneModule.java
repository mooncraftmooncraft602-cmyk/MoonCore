package com.mooncore.modules.zone;

import com.mooncore.api.zone.Region;
import com.mooncore.api.zone.ZoneFlag;
import com.mooncore.api.zone.ZoneService;
import com.mooncore.command.sub.ZoneSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module Zone : régions cuboïdes + flags de protection. Indexation spatiale par chunk,
 * persistance MySQL, application via {@link ZoneListener}, sélection via {@link SelectionListener}.
 * Expose {@link ZoneService} aux autres modules.
 */
// Note : data & config sont des services noyau (toujours initialisés avant les modules),
// donc ils n'apparaissent pas dans depends — depends ne référence que d'autres MODULES.
@ModuleInfo(id = "zone", name = "ZoneManager")
public final class ZoneModule extends AbstractModule implements ZoneService {

    private final ZoneIndex index = new ZoneIndex();
    private final java.util.Map<UUID, RegionSelection> selections = new ConcurrentHashMap<>();
    private ZoneStore store;

    @Override
    protected void onEnable() throws Exception {
        this.store = new ZoneStore(data().database());
        data().applyMigrations(ZoneStore.migrations());

        // Chargement initial (démarrage : lecture synchrone autorisée).
        List<Region> loaded = store.loadAll();
        loaded.forEach(index::add);
        log().info("ZoneManager : " + loaded.size() + " région(s) chargée(s).");

        services().register(ZoneService.class, this);
        registerListener(new ZoneListener(plugin(), this));
        registerListener(new SelectionListener(plugin(), this));
        plugin().rootCommand().register(new ZoneSubCommand(plugin(), this));
    }

    @Override
    protected void onDisable() {
        services().unregister(ZoneService.class);
        index.clear();
        selections.clear();
    }

    // ---- ZoneService ----

    @Override
    public List<Region> regionsAt(Location loc) {
        return index.regionsAt(loc);
    }

    @Override
    public Optional<Region> highestAt(Location loc) {
        List<Region> regions = index.regionsAt(loc);
        return regions.isEmpty() ? Optional.empty() : Optional.of(regions.get(0));
    }

    @Override
    public boolean flag(Location loc, ZoneFlag flag) {
        for (Region r : index.regionsAt(loc)) {
            Boolean v = r.flag(flag);
            if (v != null) return v;
        }
        return flag.defaultValue();
    }

    // ---- Gestion (commandes) ----

    /** Crée une région à partir d'une sélection. Retourne null si le nom existe déjà. */
    public Region createRegion(String name, RegionSelection sel, int priority) {
        if (index.byName(name) != null) return null;
        Location a = sel.pos1();
        Location b = sel.pos2();
        Region region = new Region(name, a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ(), priority);
        index.add(region);
        store.save(region);
        return region;
    }

    public boolean deleteRegion(String name) {
        Region r = index.byName(name);
        if (r == null) return false;
        index.remove(r);
        store.delete(r.name());
        return true;
    }

    /** Modifie un flag (réindexe pour rester cohérent) et persiste. */
    public boolean setFlag(String regionName, ZoneFlag flag, Boolean value) {
        Region r = index.byName(regionName);
        if (r == null) return false;
        r.setFlag(flag, value);
        store.save(r);
        return true;
    }

    /** Persiste une région déjà indexée (après modif de priorité par ex.). */
    public void saveRegion(Region region) {
        store.save(region);
    }

    public Region region(String name) { return index.byName(name); }

    public java.util.Collection<Region> regions() { return index.all(); }

    public RegionSelection selection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), k -> new RegionSelection());
    }

    /** {@code true} si le joueur contourne les protections de zone. */
    public boolean bypasses(Player player) {
        return player.hasPermission("mooncore.bypass.zone");
    }
}
