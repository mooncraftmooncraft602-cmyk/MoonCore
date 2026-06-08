package com.mooncore.modules.customitem;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extrait les textures VANILLA (item/block PNG) d'un .jar client Minecraft ou d'un
 * resource pack .zip vers {@code plugins/MoonCore/vanilla-textures/}, afin de pouvoir
 * démarrer une édition « à partir » d'une texture vanilla (ex : deepslate_diamond_ore).
 * Le serveur n'embarque pas les assets vanilla (ils vivent côté client) : cet import
 * one-shot comble ce manque.
 */
public final class VanillaTextureImporter {

    private VanillaTextureImporter() {}

    public record Result(int extracted, String error) {}

    /** Dossier où l'admin dépose le .jar/.zip source. */
    public static File importFolder(File dataFolder) {
        File f = new File(dataFolder, "import");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File vanillaFolder(File dataFolder) {
        File f = new File(dataFolder, "vanilla-textures");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    /** Premier .jar/.zip trouvé dans le dossier import/, ou null. */
    public static File findSource(File dataFolder) {
        File[] files = importFolder(dataFolder).listFiles((d, n) -> {
            String s = n.toLowerCase(Locale.ROOT);
            return s.endsWith(".jar") || s.endsWith(".zip");
        });
        return (files == null || files.length == 0) ? null : files[0];
    }

    /**
     * Extrait toutes les textures {@code assets/minecraft/textures/(item|block)/*.png}
     * du fichier source vers vanilla-textures/ (noms aplatis). Écrase les existantes.
     */
    public static Result extract(File source, File dataFolder) {
        if (source == null || !source.isFile()) return new Result(0, "fichier source introuvable (dépose un .jar client ou un resource pack .zip dans plugins/MoonCore/import/)");
        File out = vanillaFolder(dataFolder);
        int count = 0;
        try (ZipFile zip = new ZipFile(source)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName().toLowerCase(Locale.ROOT);
                boolean tex = name.endsWith(".png") && name.contains("assets/minecraft/textures/")
                        && (name.contains("/textures/item/") || name.contains("/textures/block/"));
                if (!tex) continue;
                String base = name.substring(name.lastIndexOf('/') + 1); // <nom>.png
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, new File(out, base).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    count++;
                }
            }
        } catch (Exception ex) {
            return new Result(count, ex.getMessage());
        }
        return new Result(count, null);
    }
}
