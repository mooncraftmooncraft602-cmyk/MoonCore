package com.mooncore.modules.customitem;

import com.mooncore.util.MoonLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Persistance des définitions d'objets custom sous forme de fichiers YAML autonomes
 * ({@code plugins/MoonCore/items/<id>.yml}). Choix volontaire d'un stockage fichier
 * plutôt que SQL : les définitions sont éditées par les admins, doivent être lisibles,
 * versionnables et exportables/importables facilement.
 */
public final class CustomItemDefStore {

    private final File folder;
    private final MoonLogger log;

    public CustomItemDefStore(File dataFolder, MoonLogger log) {
        this.folder = new File(dataFolder, "items");
        this.log = log;
        if (!folder.exists() && !folder.mkdirs()) {
            log.warn("Impossible de créer le dossier items/ pour les objets custom.");
        }
    }

    public File folder() { return folder; }

    /** Charge toutes les définitions présentes sur le disque. */
    public Map<String, CustomItemDef> loadAll() {
        Map<String, CustomItemDef> out = new LinkedHashMap<>();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
                out.put(id, CustomItemDef.load(id, yml));
            } catch (Exception e) {
                log.error("Définition d'objet custom invalide : " + f.getName(), e);
            }
        }
        return out;
    }

    public void save(CustomItemDef def) {
        File f = new File(folder, def.id() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        def.save(yml);
        try {
            yml.save(f);
        } catch (IOException e) {
            log.error("Échec de sauvegarde de l'objet custom " + def.id(), e);
        }
    }

    public boolean delete(String id) {
        File f = new File(folder, id.toLowerCase(Locale.ROOT) + ".yml");
        return f.exists() && f.delete();
    }

    public boolean exists(String id) {
        return new File(folder, id.toLowerCase(Locale.ROOT) + ".yml").exists();
    }
}
