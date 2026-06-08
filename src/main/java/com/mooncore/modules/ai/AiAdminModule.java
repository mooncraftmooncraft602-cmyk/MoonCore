package com.mooncore.modules.ai;

import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.ai.command.AiSubCommand;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.ability.AbilityRegistry;

/**
 * Assistant IA réservé aux administrateurs. L'IA n'exécute jamais de commande : elle
 * ne produit que des données structurées, validées par {@link AiActionValidator} puis
 * appliquées par les systèmes internes de MoonCore (CustomItemManager). La clé API
 * reste strictement côté serveur. Toutes les actions sont auditées.
 */
@ModuleInfo(
        id = "ai-assistant",
        name = "AIAdminAssistant",
        softDepends = {"custom-item", "boss", "event", "reward", "progression"}
)
public final class AiAdminModule extends AbstractModule {

    private AiClient client;
    private AiActionValidator validator;
    private AiPrompts prompts;
    private AiAuditStore audit;
    private final com.mooncore.modules.ai.script.ScriptEngine scriptEngine =
            new com.mooncore.modules.ai.script.ScriptEngine();

    @Override
    protected void onEnable() throws Exception {
        AiConfig config = AiConfig.from(moduleConfig());
        this.client = new AiClient(config);

        // Registre de capacités partagé avec CustomItemManager (sinon repli autonome).
        CustomItemManagerModule ci = plugin().moduleManager().get(CustomItemManagerModule.class);
        AbilityRegistry abilities = ci != null ? ci.abilities() : new AbilityRegistry(plugin());

        double maxStat = moduleConfig().getDouble("limits.max-stat-value", 100.0);
        int maxLevel = moduleConfig().getInt("limits.max-ability-level", 5);
        int maxAbilities = moduleConfig().getInt("limits.max-abilities", 2);
        java.util.Map<String, Double> caps = AiActionValidator.defaultStatCaps();
        var capSec = moduleConfig().getConfigurationSection("limits.stat-caps");
        if (capSec != null) {
            for (String k : capSec.getKeys(false)) caps.put(k.toLowerCase(java.util.Locale.ROOT), capSec.getDouble(k));
        }
        this.validator = new AiActionValidator(abilities, maxStat, maxLevel, maxAbilities, caps);
        this.prompts = new AiPrompts(abilities);

        this.audit = new AiAuditStore(data().database());
        data().applyMigrations(AiAuditStore.migrations());

        plugin().rootCommand().register(new AiSubCommand(this));

        if (!config.hasApiKey()) {
            log().warn("AIAdminAssistant actif mais SANS clé API. "
                    + "Renseigne 'api-key' dans modules/ai-assistant.yml puis /moon ai reload.");
        } else {
            log().info("AIAdminAssistant prêt (" + config.provider() + " / " + config.model() + ").");
        }
    }

    @Override
    protected void onDisable() {
        // Le HttpClient n'a pas de ressources à fermer explicitement (daemon).
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        client.updateConfig(AiConfig.from(moduleConfig()));
        log().info("AIAdminAssistant rechargé (" + client.config().provider()
                + " / " + client.config().model() + ").");
    }

    /** Change le modèle à chaud (persiste dans la config du module). */
    public void setModel(String model) {
        setConfigValue("model", model);
    }

    /**
     * Modifie un paramètre IA en jeu et l'applique à chaud (provider, model, api-key,
     * endpoint, temperature, image-model…). Permet d'ajouter d'autres API que Grok
     * directement depuis le jeu, sans toucher aux fichiers.
     */
    public void setConfigValue(String key, String value) {
        switch (key) {
            case "temperature" -> {
                try { moduleConfig().set(key, Double.parseDouble(value)); }
                catch (NumberFormatException e) { moduleConfig().set(key, 0.7); }
            }
            case "generate-textures", "developer-mode" -> moduleConfig().set(key, Boolean.parseBoolean(value));
            case "max-output-tokens", "timeout-seconds", "max-requests-per-minute" -> {
                try { moduleConfig().set(key, Integer.parseInt(value)); }
                catch (NumberFormatException ignored) {}
            }
            default -> moduleConfig().set(key, value);
        }
        plugin().configManager().saveModuleConfig(id());
        client.updateConfig(AiConfig.from(moduleConfig()));
    }

    public com.mooncore.modules.boss.BossManagerModule bossModule() {
        return plugin().moduleManager().get(com.mooncore.modules.boss.BossManagerModule.class);
    }

    public com.mooncore.modules.customblock.CustomBlockManagerModule blockModule() {
        return plugin().moduleManager().get(com.mooncore.modules.customblock.CustomBlockManagerModule.class);
    }

    public com.mooncore.modules.ai.script.ScriptEngine scriptEngine() { return scriptEngine; }

    /** Mode développeur (exécution de code Java généré) — OFF par défaut. */
    public boolean devMode() { return moduleConfig().getBoolean("developer-mode", false); }

    /** Chemin optionnel vers javac (si le serveur tourne sur un JRE). */
    public String javacPath() { return moduleConfig().getString("javac-path", ""); }

    /** Ids de tous les modules (pour que l'IA sache ce qu'elle peut configurer). */
    public java.util.List<String> moduleIds() {
        return plugin().moduleManager().all().stream().map(com.mooncore.core.module.MoonModule::id).sorted().toList();
    }

    /**
     * Applique des changements de configuration à un module existant puis le recharge à
     * chaud. Sûr : n'écrit QUE des valeurs de config (jamais de code), sur un module connu.
     * Retourne null si OK, sinon un message d'erreur.
     */
    public String applyModuleConfig(String moduleId, java.util.Map<String, Object> values) {
        if (plugin().moduleManager().get(moduleId) == null) return "module inconnu : " + moduleId;
        if (values == null || values.isEmpty()) return "aucune valeur à appliquer";
        try {
            var cfg = plugin().configManager().moduleConfig(moduleId);
            for (var e : values.entrySet()) cfg.set(e.getKey(), e.getValue());
            plugin().configManager().saveModuleConfig(moduleId);
            plugin().moduleManager().reload(moduleId);
            return null;
        } catch (Exception e) {
            return "échec d'application : " + e.getMessage();
        }
    }

    // ---- accès pour la commande ----
    public AiClient client() { return client; }
    public AiActionValidator validator() { return validator; }
    public AiPrompts prompts() { return prompts; }
    public AiAuditStore audit() { return audit; }
    public com.mooncore.MoonCore mc() { return plugin; }

    public CustomItemManagerService customItems() {
        return services().get(CustomItemManagerService.class).orElse(null);
    }

    public CustomItemManagerModule customItemModule() {
        return plugin().moduleManager().get(CustomItemManagerModule.class);
    }
}
