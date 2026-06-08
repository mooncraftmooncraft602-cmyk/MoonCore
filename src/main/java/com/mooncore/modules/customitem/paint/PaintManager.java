package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Gère les sessions d'éditeur de texture (une par joueur). */
public final class PaintManager {

    private final MoonCore plugin;
    private final Map<UUID, PaintSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Double> sensitivityMemory = new ConcurrentHashMap<>();

    public PaintManager(MoonCore plugin) {
        this.plugin = plugin;
    }

    /** Sensibilité curseur retenue pour ce joueur (sinon {@code dflt}). */
    public double sensitivity(UUID player, double dflt) {
        return sensitivityMemory.getOrDefault(player, dflt);
    }

    /** Mémorise la sensibilité choisie par un joueur (persiste entre ouvertures de l'éditeur). */
    public void rememberSensitivity(UUID player, double value) {
        sensitivityMemory.put(player, value);
    }

    public boolean open(Player p, PaintTarget target, int size) {
        return open(p, target, size, null, null);
    }

    public boolean open(Player p, PaintTarget target, int size, java.io.File sourceTexture) {
        return open(p, target, size, sourceTexture, null);
    }

    /**
     * @param size 0 = auto (détecte depuis la base/texture existante, défaut 16)
     * @param onClose exécuté à la fermeture de l'éditeur (ex. rouvrir un menu)
     */
    public boolean open(Player p, PaintTarget target, int size, java.io.File sourceTexture, Runnable onClose) {
        close(p.getUniqueId());
        int actual = size > 0 ? size : detectSize(sourceTexture, target.textureFile());
        PaintSession session = new PaintSession(plugin, target, this, p, actual, sourceTexture);
        session.setOnClose(onClose);
        if (!session.start()) {
            p.sendMessage(Text.mm("<red>Impossible d'ouvrir l'éditeur ici (place-toi face à un espace dégagé)."));
            return false;
        }
        sessions.put(p.getUniqueId(), session);
        return true;
    }

    /** Détecte une taille de toile (16/32/64/128) depuis la 1re image existante, défaut 16. */
    public static int detectSize(java.io.File... candidates) {
        for (java.io.File f : candidates) {
            if (f == null || !f.isFile()) continue;
            try {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                if (img == null) continue;
                int w = Math.max(img.getWidth(), img.getHeight());
                if (w <= 16) return 16;
                if (w <= 32) return 32;
                if (w <= 64) return 64;
                return 128;
            } catch (Exception ignored) { }
        }
        return 16;
    }

    /**
     * Résout la texture PNG d'une base à importer : item custom → bloc custom → boss
     * custom → texture VANILLA (vanilla-textures/, ex. deepslate_diamond_ore). null si introuvable.
     */
    public static java.io.File resolveTexture(MoonCore plugin, String id) {
        if (id == null || id.isBlank()) return null;
        String norm = id.toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
        java.io.File item = new java.io.File(plugin.getDataFolder(), "items-textures/" + norm + ".png");
        if (item.isFile()) return item;
        java.io.File block = new java.io.File(plugin.getDataFolder(), "blocks-textures/" + norm + ".png");
        if (block.isFile()) return block;
        java.io.File boss = new java.io.File(plugin.getDataFolder(), "boss-textures/" + norm + ".png");
        if (boss.isFile()) return boss;
        java.io.File vanilla = new java.io.File(plugin.getDataFolder(), "vanilla-textures/" + norm + ".png");
        if (vanilla.isFile()) return vanilla;
        return null;
    }

    public PaintSession get(UUID uuid) { return sessions.get(uuid); }

    public boolean has(UUID uuid) { return sessions.containsKey(uuid); }

    public void close(UUID uuid) {
        PaintSession s = sessions.remove(uuid);
        if (s != null) s.close();
    }

    public void closeAll() {
        for (UUID id : Map.copyOf(sessions).keySet()) close(id);
    }
}
