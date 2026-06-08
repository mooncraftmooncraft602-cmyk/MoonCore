package com.mooncore.modules.customitem;

import com.mooncore.util.MoonLogger;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Génère un resource pack <b>vanilla</b> (overrides {@code custom_model_data}) à partir
 * des définitions qui déclarent un modèle. Le plugin <b>n'invente pas</b> de PNG : il
 * copie les textures existantes depuis un dossier source ({@code items-textures/}) et
 * journalise un warning pour chaque texture manquante (fallback : rendu vanilla).
 * <p>
 * Compat Bedrock : les {@code custom_model_data} ne sont pas rendus nativement par
 * Bedrock. Pour Geyser, un pack Bedrock séparé doit être généré côté Geyser ; en
 * l'absence, l'objet s'affiche avec sa texture vanilla — d'où l'importance du lore
 * (toujours présent) comme source de vérité visuelle. Un warning le rappelle.
 */
public final class ResourcePackBuilder {

    public record Result(int models, int copied, List<String> warnings) {}

    private final MoonLogger log;

    public ResourcePackBuilder(MoonLogger log) {
        this.log = log;
    }

    /**
     * @param defs           définitions chargées
     * @param outputDir      dossier de sortie du pack (sera créé/écrasé)
     * @param textureSource  dossier contenant les PNG ({@code <model-key>.png}), peut être null
     */
    public Result build(Map<String, CustomItemDef> defs, File outputDir, File textureSource) {
        List<String> warnings = new ArrayList<>();
        writePackMeta(outputDir, warnings);
        // material -> (customModelData -> modelKey)
        Map<Material, Map<Integer, String>> byMaterial = new LinkedHashMap<>();
        for (CustomItemDef def : defs.values()) {
            if (def.customModelData() <= 0 || def.modelKey() == null || def.modelKey().isBlank()) continue;
            byMaterial.computeIfAbsent(def.material(), k -> new LinkedHashMap<>())
                    .put(def.customModelData(), def.modelKey().toLowerCase(Locale.ROOT));
        }
        if (byMaterial.isEmpty()) {
            warnings.add("Aucune définition ne déclare de modèle (custom-model-data + model-key).");
            return new Result(0, 0, warnings);
        }

        File assets = new File(outputDir, "assets/minecraft");
        File modelsItem = new File(assets, "models/item");
        File modelsCustom = new File(modelsItem, "custom");
        File texturesItem = new File(assets, "textures/item/custom");
        modelsCustom.mkdirs();
        texturesItem.mkdirs();

        int models = 0, copied = 0;
        for (Map.Entry<Material, Map<Integer, String>> e : byMaterial.entrySet()) {
            Material mat = e.getKey();
            String matName = mat.name().toLowerCase(Locale.ROOT);
            // Modèle de base avec overrides predicate custom_model_data.
            boolean handheld = isHandheld(matName);
            writeBaseModel(modelsItem, matName, e.getValue(), handheld, warnings);
            for (Map.Entry<Integer, String> ov : e.getValue().entrySet()) {
                String modelKey = ov.getValue();
                writeCustomModel(modelsCustom, modelKey, handheld, warnings);
                models++;
                // Copie de la texture si présente.
                if (textureSource != null) {
                    File png = new File(textureSource, modelKey + ".png");
                    if (png.isFile()) {
                        try {
                            Files.copy(png.toPath(), new File(texturesItem, modelKey + ".png").toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            copied++;
                        } catch (IOException io) {
                            warnings.add("Copie texture échouée : " + png.getName() + " (" + io.getMessage() + ")");
                        }
                    } else {
                        warnings.add("Texture manquante : " + modelKey + ".png — rendu vanilla utilisé.");
                    }
                }
            }
        }
        warnings.add("Bedrock : les custom-model-data ne sont pas rendus nativement ; "
                + "générer un pack Geyser séparé. Le lore reste la source visuelle.");
        for (String w : warnings) log.warn("[ResourcePack] " + w);
        return new Result(models, copied, warnings);
    }

    private void writePackMeta(File outputDir, List<String> warnings) {
        outputDir.mkdirs();
        String meta = """
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "MoonCore - objets custom"
                  }
                }
                """;
        write(new File(outputDir, "pack.mcmeta"), meta, warnings);
    }

    /** Outils/armes = tenus en main (item/handheld) ; le reste = icône plate (item/generated). */
    private static boolean isHandheld(String matName) {
        return matName.endsWith("_sword") || matName.endsWith("_axe") || matName.endsWith("_pickaxe")
                || matName.endsWith("_shovel") || matName.endsWith("_hoe")
                || matName.equals("trident") || matName.equals("mace") || matName.equals("stick")
                || matName.equals("fishing_rod") || matName.equals("carrot_on_a_stick")
                || matName.equals("warped_fungus_on_a_stick") || matName.equals("brush")
                || matName.equals("flint_and_steel") || matName.equals("bow")
                || matName.equals("crossbow") || matName.equals("blaze_rod") || matName.equals("debug_stick");
    }

    private void writeBaseModel(File modelsItem, String matName, Map<Integer, String> overrides,
                               boolean handheld, List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"parent\": \"").append(handheld ? "item/handheld" : "item/generated").append("\",\n");
        sb.append("  \"textures\": { \"layer0\": \"item/").append(matName).append("\" },\n");
        sb.append("  \"overrides\": [\n");
        List<Map.Entry<Integer, String>> sorted = new ArrayList<>(overrides.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<Integer, String> ov = sorted.get(i);
            sb.append("    { \"predicate\": { \"custom_model_data\": ").append(ov.getKey())
                    .append(" }, \"model\": \"item/custom/").append(ov.getValue()).append("\" }");
            sb.append(i < sorted.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        write(new File(modelsItem, matName + ".json"), sb.toString(), warnings);
    }

    private void writeCustomModel(File modelsCustom, String modelKey, boolean handheld, List<String> warnings) {
        String parent = handheld ? "item/handheld" : "item/generated";
        String json = """
                {
                  "parent": "%s",
                  "textures": { "layer0": "item/custom/%s" }
                }
                """.formatted(parent, modelKey);
        write(new File(modelsCustom, modelKey + ".json"), json, warnings);
    }

    private void write(File file, String content, List<String> warnings) {
        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Écriture échouée : " + file.getName() + " (" + e.getMessage() + ")");
        }
    }
}
