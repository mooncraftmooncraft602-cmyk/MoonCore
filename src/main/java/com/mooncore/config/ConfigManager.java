package com.mooncore.config;

import com.mooncore.MoonCore;
import com.mooncore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère {@code config.yml}, le fichier de langue ({@code lang/<code>.yml}) et les
 * configurations par module ({@code modules/<id>.yml}). Tout est rechargeable à chaud.
 */
public final class ConfigManager {

    private final MoonCore plugin;
    private ConfigFile lang;
    private final Map<String, ConfigFile> moduleConfigs = new ConcurrentHashMap<>();

    public ConfigManager(MoonCore plugin) {
        this.plugin = plugin;
    }

    /** Chargement initial : config.yml (par le plugin) + langue. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        loadLanguage();
    }

    private void loadLanguage() {
        String code = main().getString("core.language", "fr");
        if (code == null || code.isBlank()) code = "fr"; // évite lang/null.yml si la clé vaut null
        this.lang = new ConfigFile(plugin, "lang/" + code + ".yml");
    }

    /** Recharge config.yml + langue + tous les fichiers de module déjà ouverts. */
    public void reloadAll() {
        plugin.reloadConfig();
        loadLanguage();
        moduleConfigs.values().forEach(ConfigFile::reload);
    }

    public FileConfiguration main() {
        return plugin.getConfig();
    }

    // ---- Modules ----

    public boolean isModuleEnabled(String id) {
        return main().getBoolean("modules." + id, true);
    }

    public FileConfiguration moduleConfig(String id) {
        return moduleConfigs
                .computeIfAbsent(id, k -> new ConfigFile(plugin, "modules/" + k + ".yml"))
                .get();
    }

    public void reloadModuleConfig(String id) {
        ConfigFile cf = moduleConfigs.get(id);
        if (cf != null) cf.reload();
    }

    public void saveModuleConfig(String id) {
        ConfigFile cf = moduleConfigs.get(id);
        if (cf != null) cf.save();
    }

    // ---- Langue / messages ----

    public String prefix() {
        return main().getString("core.prefix", "");
    }

    /** Message localisé brut (chaîne MiniMessage), ou la clé entre crochets si absente. */
    public String raw(String key) {
        return lang.get().getString(key, "<red>[missing: " + key + "]</red>");
    }

    /** Message localisé sans préfixe, en Component. */
    public Component message(String key, String... placeholders) {
        return Text.mm(raw(key), placeholders);
    }

    /** Message localisé préfixé, en Component. */
    public Component prefixed(String key, String... placeholders) {
        return Text.mm(prefix() + raw(key), placeholders);
    }
}
