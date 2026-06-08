package com.mooncore.modules.customblock;

import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Génère les assets de resource pack pour les blocs custom : le blockstate
 * {@code note_block.json} (couvrant TOUS les états pour ne pas casser le rendu vanilla),
 * un modèle cube par bloc, et la copie des textures existantes. Ne fabrique aucune
 * texture ; journalise un warning si une PNG manque.
 */
public final class CustomBlockPackBuilder {

    private final MoonLogger log;

    public CustomBlockPackBuilder(MoonLogger log) { this.log = log; }

    /** @return nombre de blocs custom écrits. */
    public int build(Map<String, CustomBlockDef> defs, File outputDir, File textureSource, List<String> warnings) {
        // État assigné -> modelKey.
        Map<String, String> assigned = new LinkedHashMap<>(); // "instrument|note|powered" -> modelKey
        int count = 0;

        File assets = new File(outputDir, "assets/minecraft");
        File blockModels = new File(assets, "models/block/custom");
        File blockTextures = new File(assets, "textures/block/custom");
        File blockstates = new File(assets, "blockstates");
        blockModels.mkdirs();
        blockTextures.mkdirs();
        blockstates.mkdirs();

        for (CustomBlockDef def : defs.values()) {
            if (def.stateIndex() < 0 || def.modelKey() == null || def.modelKey().isBlank()) continue;
            BlockStateMap.State st = BlockStateMap.forIndex(def.stateIndex());
            assigned.put(st.instrumentName() + "|" + st.note() + "|" + st.powered(), def.modelKey());
            writeBlockModel(blockModels, def, warnings);
            count++;
            if (textureSource != null) {
                // Copie toutes les textures référencées (toutes-faces OU top/side/bottom).
                java.util.Set<String> keys = new java.util.LinkedHashSet<>();
                keys.add(def.modelKey());
                if (def.hasFaces()) { keys.add(def.textureTop()); keys.add(def.textureSide()); keys.add(def.textureBottom()); }
                for (String key : keys) copyTexture(textureSource, blockTextures, key, warnings);
            }
        }

        if (count > 0) {
            writeNoteBlockState(blockstates, assigned, warnings);
            log.info("[ResourcePack] " + count + " bloc(s) custom écrit(s) dans le pack.");
        }
        return count;
    }

    private void writeBlockModel(File dir, CustomBlockDef def, List<String> warnings) {
        String json;
        if (def.hasFaces()) {
            // Cube avec faces distinctes (haut / côtés / bas).
            json = """
                    {
                      "parent": "block/cube",
                      "textures": {
                        "up": "block/custom/%s",
                        "down": "block/custom/%s",
                        "north": "block/custom/%s",
                        "south": "block/custom/%s",
                        "east": "block/custom/%s",
                        "west": "block/custom/%s",
                        "particle": "block/custom/%s"
                      }
                    }
                    """.formatted(def.textureTop(), def.textureBottom(), def.textureSide(),
                    def.textureSide(), def.textureSide(), def.textureSide(), def.textureSide());
        } else {
            json = """
                    {
                      "parent": "block/cube_all",
                      "textures": { "all": "block/custom/%s" }
                    }
                    """.formatted(def.modelKey());
        }
        write(new File(dir, def.modelKey() + ".json"), json, warnings);
    }

    private void copyTexture(File src, File dst, String key, List<String> warnings) {
        if (key == null || key.isBlank()) return;
        File png = new File(src, key + ".png");
        if (png.isFile()) {
            try {
                Files.copy(png.toPath(), new File(dst, key + ".png").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                // Animation : copie le .png.mcmeta s'il existe (texture en bande de frames).
                File mcmeta = new File(src, key + ".png.mcmeta");
                if (mcmeta.isFile()) Files.copy(mcmeta.toPath(),
                        new File(dst, key + ".png.mcmeta").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                warnings.add("Copie texture bloc échouée : " + png.getName());
            }
        } else {
            warnings.add("Texture bloc manquante : " + key + ".png (rendu note_block).");
        }
    }

    /** Couvre TOUS les états du note block (sinon états vanilla = modèle manquant). */
    private void writeNoteBlockState(File dir, Map<String, String> assigned, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"variants\": {\n");
        boolean first = true;
        for (String instr : BlockStateMap.allInstrumentNames()) {
            for (int note = 0; note <= 24; note++) {
                for (boolean powered : new boolean[]{false, true}) {
                    String key = "instrument=" + instr + ",note=" + note + ",powered=" + powered;
                    String mk = assigned.get(instr + "|" + note + "|" + powered);
                    String model = (mk != null) ? "block/custom/" + mk : "block/note_block";
                    if (!first) sb.append(",\n");
                    sb.append("    \"").append(key).append("\": { \"model\": \"").append(model).append("\" }");
                    first = false;
                }
            }
        }
        sb.append("\n  }\n}\n");
        write(new File(dir, "note_block.json"), sb.toString(), warnings);
    }

    private void write(File file, String content, List<String> warnings) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Écriture échouée : " + file.getName());
        }
    }
}
