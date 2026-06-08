package com.mooncore.modules.audio;

import com.mooncore.MoonCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Fichier de données runtime de l'audio ({@code audio-data.yml}) : loops persistés + zones. */
public final class AudioData {

    private final File file;
    private YamlConfiguration yml;

    public AudioData(MoonCore plugin) {
        this.file = new File(plugin.getDataFolder(), "audio-data.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public YamlConfiguration yml() {
        return yml;
    }

    public synchronized void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            // best-effort : ne pas casser le jeu pour une sauvegarde audio
        }
    }
}
