package com.mooncore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Fichier YAML autonome (hors {@code config.yml}). Crée la valeur par défaut depuis
 * les ressources du jar si elle existe, recharge et sauvegarde à la demande.
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String resourcePath; // chemin relatif dans le jar et sur le disque
    private final File file;
    private FileConfiguration config;

    public ConfigFile(Plugin plugin, String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Impossible de créer " + resourcePath + " : " + e.getMessage());
                }
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);

        // Fusionne les valeurs par défaut embarquées (clés manquantes) sans écraser l'existant.
        // try-with-resources : ferme le flux jar à chaque reload (évite la fuite de ressource).
        try (InputStream defaults = plugin.getResource(resourcePath)) {
            if (defaults != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaults, StandardCharsets.UTF_8));
                config.setDefaults(defConfig);
                config.options().copyDefaults(true);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Lecture des défauts de " + resourcePath + " : " + e.getMessage());
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder " + resourcePath + " : " + e.getMessage());
        }
    }

    public FileConfiguration get() {
        return config;
    }

    public File file() {
        return file;
    }
}
