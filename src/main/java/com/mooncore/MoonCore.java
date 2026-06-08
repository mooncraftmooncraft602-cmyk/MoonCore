package com.mooncore;

import com.mooncore.command.MoonCommand;
import com.mooncore.command.sub.HelpSubCommand;
import com.mooncore.command.sub.ModulesSubCommand;
import com.mooncore.command.sub.ReloadSubCommand;
import com.mooncore.command.sub.VersionSubCommand;
import com.mooncore.config.ConfigManager;
import com.mooncore.core.event.EventBus;
import com.mooncore.core.module.ModuleManager;
import com.mooncore.core.service.ServiceRegistry;
import com.mooncore.data.DataManager;
import com.mooncore.util.MoonLogger;
import com.mooncore.util.Schedulers;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entrée de MoonCore.
 * <p>
 * Bootstrap des services noyau (ordre strict), puis activation des modules métier
 * via le {@link ModuleManager}. Les services noyau (config, données, registry, bus,
 * scheduler, logger) sont initialisés ici avant tout module qui en dépend.
 */
public final class MoonCore extends JavaPlugin {

    private MoonLogger logger;
    private ConfigManager configManager;
    private ServiceRegistry services;
    private EventBus eventBus;
    private Schedulers schedulers;
    private DataManager dataManager;
    private ModuleManager moduleManager;
    private MoonCommand rootCommand;

    @Override
    public void onEnable() {
        // 1) Configuration + logger (le logger lit core.debug).
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.logger = new MoonLogger(getLogger(), getConfig().getBoolean("core.debug", false));
        logger.info("Démarrage de MoonCore v" + getPluginMeta().getVersion() + "…");

        // 2) Services noyau.
        this.services = new ServiceRegistry();
        this.eventBus = new EventBus(getLogger());
        this.schedulers = new Schedulers(this);
        if (schedulers.isFolia()) {
            logger.warn("Folia détecté : MoonCore utilise pour l'instant le scheduler standard.");
        }

        // 3) Base de données (fail-fast : sans DB, on désactive proprement le plugin).
        this.dataManager = new DataManager(this);
        try {
            dataManager.init();
        } catch (Exception e) {
            logger.error("Connexion à la base de données impossible — MoonCore est désactivé.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 4) Commande racine /moon AVANT l'activation des modules :
        //    chaque module enregistre ses sous-commandes via rootCommand() pendant enableAll().
        setupCommands();

        // 5) Modules métier (enregistrés au fur et à mesure de leur implémentation).
        this.moduleManager = new ModuleManager(this);
        registerModules();
        moduleManager.enableAll();

        logger.info("MoonCore activé. Modules actifs : " + moduleManager.enableOrder().size());
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) moduleManager.disableAll();
        if (dataManager != null) dataManager.shutdown();
        if (services != null) services.clear();
        if (eventBus != null) eventBus.clear();
        if (logger != null) logger.info("MoonCore désactivé.");
    }

    /**
     * Enregistre les modules métier. Vide pour l'instant : les modules
     * (ZoneManager, AntiFarm, Progression…) seront ajoutés ici phase par phase.
     */
    private void registerModules() {
        moduleManager.register(new com.mooncore.modules.season.SeasonManagerModule());
        moduleManager.register(new com.mooncore.modules.team.TeamManagerModule());
        moduleManager.register(new com.mooncore.modules.stats.StatisticsModule());
        moduleManager.register(new com.mooncore.modules.reward.RewardManagerModule());
        moduleManager.register(new com.mooncore.modules.progression.ProgressionModule());
        moduleManager.register(new com.mooncore.modules.missions.MissionModule());
        moduleManager.register(new com.mooncore.modules.quest.QuestManagerModule());
        moduleManager.register(new com.mooncore.modules.leaderboard.LeaderboardManagerModule());
        moduleManager.register(new com.mooncore.modules.boss.BossManagerModule());
        moduleManager.register(new com.mooncore.modules.enchant.EnchantManagerModule());
        moduleManager.register(new com.mooncore.modules.enditems.EndgameItemsModule());
        moduleManager.register(new com.mooncore.modules.event.EventManagerModule());
        moduleManager.register(new com.mooncore.modules.zone.ZoneModule());
        moduleManager.register(new com.mooncore.modules.antiafk.AntiAfkModule());
        moduleManager.register(new com.mooncore.modules.economy.EconomyBalancerModule());
        moduleManager.register(new com.mooncore.modules.antifarm.AntiFarmModule());
        moduleManager.register(new com.mooncore.modules.audio.AudioManagerModule());
        moduleManager.register(new com.mooncore.modules.home.HomeManagerModule());
        moduleManager.register(new com.mooncore.modules.customitem.CustomItemManagerModule());
        moduleManager.register(new com.mooncore.modules.customblock.CustomBlockManagerModule());
        moduleManager.register(new com.mooncore.modules.ai.AiAdminModule());
        moduleManager.register(new com.mooncore.modules.resourcepack.ResourcePackModule());
        moduleManager.register(new com.mooncore.modules.placeholder.PlaceholderModule());
        moduleManager.register(new com.mooncore.modules.admin.AdminToolsModule());
        moduleManager.register(new com.mooncore.modules.update.UpdateModule());
    }

    private void setupCommands() {
        this.rootCommand = new MoonCommand(this);
        rootCommand.register(new HelpSubCommand(rootCommand));
        rootCommand.register(new VersionSubCommand());
        rootCommand.register(new ModulesSubCommand());
        rootCommand.register(new ReloadSubCommand());

        PluginCommand cmd = getCommand("moon");
        if (cmd != null) {
            cmd.setExecutor(rootCommand);
            cmd.setTabCompleter(rootCommand);
        } else {
            logger.severe("Commande /moon introuvable dans plugin.yml !");
        }
    }

    // ---- Accès aux services noyau ----

    public MoonLogger logger() { return logger; }
    public ConfigManager configManager() { return configManager; }
    public ServiceRegistry services() { return services; }
    public EventBus eventBus() { return eventBus; }
    public Schedulers schedulers() { return schedulers; }
    public DataManager dataManager() { return dataManager; }
    public ModuleManager moduleManager() { return moduleManager; }
    public MoonCommand rootCommand() { return rootCommand; }

    /** Fichier .jar du plugin en cours (pour l'auto-update vers le dossier update/). */
    public java.io.File jarFile() { return getFile(); }
}
