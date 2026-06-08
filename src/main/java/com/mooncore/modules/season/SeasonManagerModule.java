package com.mooncore.modules.season;

import com.mooncore.api.season.SeasonInfo;
import com.mooncore.api.season.SeasonService;
import com.mooncore.command.sub.SeasonSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;

import java.util.List;

/**
 * SeasonManager : suit la saison active, son calendrier (début/fin), et permet de basculer
 * vers une nouvelle saison. Les données étant partitionnées par {@code seasonId}, changer de
 * saison repart à zéro tout en conservant (archivant) l'ancienne en base.
 */
@ModuleInfo(id = "season", name = "SeasonManager")
public final class SeasonManagerModule extends AbstractModule implements SeasonService {

    private SeasonStore store;
    private int lengthDays;
    private volatile SeasonInfo current;

    @Override
    protected void onEnable() throws Exception {
        this.store = new SeasonStore(data().database());
        data().applyMigrations(SeasonStore.migrations());

        this.lengthDays = moduleConfig().getInt("length-days", 30);
        String seasonId = plugin().getConfig().getString("core.season-id", "season-1");
        this.current = store.ensure(seasonId, lengthDays);

        services().register(SeasonService.class, this);
        plugin().rootCommand().register(new SeasonSubCommand(this));
        log().info("SeasonManager : saison active '" + seasonId + "'.");
    }

    @Override
    protected void onDisable() {
        services().unregister(SeasonService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        this.lengthDays = moduleConfig().getInt("length-days", 30);
    }

    // ---- SeasonService ----

    @Override
    public SeasonInfo current() {
        return current;
    }

    @Override
    public long daysRemaining() {
        return current == null ? -1 : Seasons.daysRemaining(System.currentTimeMillis(), current.endsAtMs());
    }

    // ---- Gestion ----

    public List<SeasonInfo> all() throws Exception {
        return store.all();
    }

    public int lengthDays() {
        return lengthDays;
    }

    /**
     * Bascule vers une nouvelle saison : active la saison en base et met à jour la config.
     * Un redémarrage est recommandé pour que tous les modules rechargent leur seasonId.
     */
    public void switchTo(String newSeasonId) throws Exception {
        store.activate(newSeasonId, lengthDays);
        plugin().getConfig().set("core.season-id", newSeasonId);
        plugin().saveConfig();
        this.current = store.load(newSeasonId);
    }
}
