package com.mooncore.modules.resourcepack;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.ResourcePackBuilder;
import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assemble le resource pack distribuable : fusionne les assets fournis par l'admin
 * ({@code pack-sources/} — sons, autres textures…) avec les modèles/textures d'items
 * générés, puis produit {@code pack.zip} et son SHA-1 (requis pour un pack forcé).
 */
public final class PackAssembler {

    public record Built(File zip, byte[] sha1, int models, int copiedTextures) {}

    private final MoonLogger log;

    public PackAssembler(MoonLogger log) {
        this.log = log;
    }

    public Built assemble(Map<String, CustomItemDef> defs, File buildDir, File texturesSrc,
                          File packSources, File outZip,
                          Map<String, com.mooncore.modules.customblock.CustomBlockDef> blockDefs,
                          File blockTexturesSrc,
                          Map<String, com.mooncore.modules.boss.BossDefinition> bossDefs,
                          File bossTexturesSrc) throws Exception {
        deleteRecursive(buildDir);
        buildDir.mkdirs();

        // 1) Assets fournis par l'admin (sons, etc.) copiés tels quels.
        if (packSources != null && packSources.isDirectory()) {
            copyTree(packSources, buildDir);
        }

        // 2) Modèles + textures d'items générés par-dessus (pack.mcmeta régénéré → valide).
        ResourcePackBuilder.Result r =
                new ResourcePackBuilder(log).build(defs, buildDir, texturesSrc);

        // 2b) Blocs custom (blockstate note_block + modèles + textures).
        int blocks = 0;
        if (blockDefs != null && !blockDefs.isEmpty()) {
            blocks = new com.mooncore.modules.customblock.CustomBlockPackBuilder(log)
                    .build(blockDefs, buildDir, blockTexturesSrc, r.warnings());
        }

        // 2c) Boss custom : textures cosmétiques portées sur la tête (carved_pumpkin + CMD).
        int bossModels = 0, bossCopied = 0;
        if (bossDefs != null && !bossDefs.isEmpty()) {
            var br = new com.mooncore.modules.boss.BossPackBuilder(log)
                    .build(bossDefs, buildDir, bossTexturesSrc, r.warnings());
            bossModels = br.models();
            bossCopied = br.copied();
        }

        // 3) Zip + SHA-1.
        outZip.getParentFile().mkdirs();
        zipDir(buildDir, outZip);
        byte[] sha1 = sha1(outZip);
        return new Built(outZip, sha1, r.models() + blocks + bossModels, r.copied() + bossCopied);
    }

    // ---- helpers ----

    private static void copyTree(File src, File dst) throws IOException {
        File[] children = src.listFiles();
        if (children == null) return;
        for (File c : children) {
            File target = new File(dst, c.getName());
            if (c.isDirectory()) {
                target.mkdirs();
                copyTree(c, target);
            } else {
                target.getParentFile().mkdirs();
                Files.copy(c.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void zipDir(File dir, File outZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outZip.toPath()))) {
            zipInto(dir, dir, zos);
        }
    }

    private static void zipInto(File root, File current, ZipOutputStream zos) throws IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File c : children) {
            String rel = root.toPath().relativize(c.toPath()).toString().replace('\\', '/');
            if (c.isDirectory()) {
                zos.putNextEntry(new ZipEntry(rel + "/"));
                zos.closeEntry();
                zipInto(root, c, zos);
            } else {
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(c.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    private static byte[] sha1(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(Files.readAllBytes(file.toPath()));
        return md.digest();
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    /** Représentation hex du SHA-1 (debug/log). */
    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }
}
