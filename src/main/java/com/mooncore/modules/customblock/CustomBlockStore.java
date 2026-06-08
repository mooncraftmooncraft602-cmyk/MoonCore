package com.mooncore.modules.customblock;

import com.mooncore.util.MoonLogger;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persistance des blocs custom en YAML ({@code plugins/MoonCore/blocks/<id>.yml}). */
public final class CustomBlockStore {

    private final File folder;
    private final MoonLogger log;

    public CustomBlockStore(File dataFolder, MoonLogger log) {
        this.folder = new File(dataFolder, "blocks");
        this.log = log;
        if (!folder.exists()) folder.mkdirs();
    }

    public Map<String, CustomBlockDef> loadAll() {
        Map<String, CustomBlockDef> out = new LinkedHashMap<>();
        File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            try {
                out.put(id, CustomBlockDef.load(id, YamlConfiguration.loadConfiguration(f)));
            } catch (Exception e) {
                log.error("Bloc custom invalide : " + f.getName(), e);
            }
        }
        return out;
    }

    public void save(CustomBlockDef def) {
        File f = new File(folder, def.id() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        def.save(yml);
        try {
            yml.save(f);
        } catch (IOException e) {
            log.error("Échec de sauvegarde du bloc custom " + def.id(), e);
        }
    }

    public boolean delete(String id) {
        File f = new File(folder, id.toLowerCase(Locale.ROOT) + ".yml");
        return f.exists() && f.delete();
    }

    public File texturesFolder() {
        File f = new File(folder.getParentFile(), "blocks-textures");
        if (!f.exists()) f.mkdirs();
        return f;
    }
}
