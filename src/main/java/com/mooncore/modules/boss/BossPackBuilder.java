package com.mooncore.modules.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mooncore.util.MoonLogger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Génère les assets de resource pack pour les textures cosmétiques de boss. */
public final class BossPackBuilder {

    public record Result(int models, int copied) {}

    private static final String BASE_ITEM = "carved_pumpkin";
    private final MoonLogger log;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public BossPackBuilder(MoonLogger log) {
        this.log = log;
    }

    public Result build(Map<String, BossDefinition> defs, File outputDir, File textureSource, List<String> warnings) {
        if (defs == null || defs.isEmpty()) return new Result(0, 0);

        File assets = new File(outputDir, "assets/minecraft");
        File modelsItem = new File(assets, "models/item");
        File modelsCustom = new File(modelsItem, "custom");
        File texturesItem = new File(assets, "textures/item/custom");
        modelsItem.mkdirs();
        modelsCustom.mkdirs();
        texturesItem.mkdirs();

        List<OverrideDef> overrides = new ArrayList<>();
        int models = 0, copied = 0;
        for (BossDefinition def : defs.values()) {
            if (def.textureKey() == null) continue;
            String textureKey = safe(def.textureKey());
            String modelKey = "boss_" + textureKey;
            int cmd = def.textureCustomModelData() > 0
                    ? def.textureCustomModelData() : BossManagerModule.textureModelData(def.id());
            writeModel(modelsCustom, modelKey, warnings);
            models++;
            overrides.add(new OverrideDef(cmd, "item/custom/" + modelKey));

            if (textureSource != null) {
                File png = new File(textureSource, textureKey + ".png");
                if (png.isFile()) {
                    try {
                        Files.copy(png.toPath(), new File(texturesItem, modelKey + ".png").toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        copied++;
                    } catch (IOException e) {
                        warnings.add("Copie texture boss échouée : " + png.getName() + " (" + e.getMessage() + ")");
                    }
                } else {
                    warnings.add("Texture boss manquante : " + textureKey + ".png.");
                }
            }
        }

        if (!overrides.isEmpty()) {
            mergeBaseOverrides(new File(modelsItem, BASE_ITEM + ".json"), overrides, warnings);
            log.info("[ResourcePack] " + models + " texture(s) boss écrite(s) dans le pack.");
        }
        return new Result(models, copied);
    }

    private void writeModel(File dir, String modelKey, List<String> warnings) {
        String json = """
                {
                  "parent": "item/generated",
                  "textures": { "layer0": "item/custom/%s" }
                }
                """.formatted(modelKey);
        write(new File(dir, modelKey + ".json"), json, warnings);
    }

    private void mergeBaseOverrides(File file, List<OverrideDef> additions, List<String> warnings) {
        JsonObject root = readBase(file, warnings);
        List<JsonObject> all = new ArrayList<>();
        JsonArray existing = root.has("overrides") && root.get("overrides").isJsonArray()
                ? root.getAsJsonArray("overrides") : new JsonArray();
        for (JsonElement el : existing) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            int cmd = customModelData(obj);
            boolean replaced = additions.stream().anyMatch(a -> a.customModelData == cmd);
            if (!replaced) all.add(obj);
        }
        for (OverrideDef add : additions) all.add(override(add.customModelData, add.model));
        all.sort(Comparator.comparingInt(BossPackBuilder::customModelData));

        JsonArray merged = new JsonArray();
        for (JsonObject obj : all) merged.add(obj);
        root.add("overrides", merged);
        write(file, gson.toJson(root), warnings);
    }

    private JsonObject readBase(File file, List<String> warnings) {
        if (file.isFile()) {
            try {
                JsonObject root = gson.fromJson(Files.readString(file.toPath()), JsonObject.class);
                if (root != null) return root;
            } catch (Exception e) {
                warnings.add("Lecture modèle " + file.getName() + " échouée, régénération.");
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("parent", "item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "item/" + BASE_ITEM);
        root.add("textures", textures);
        root.add("overrides", new JsonArray());
        return root;
    }

    private static JsonObject override(int customModelData, String model) {
        JsonObject root = new JsonObject();
        JsonObject predicate = new JsonObject();
        predicate.addProperty("custom_model_data", customModelData);
        root.add("predicate", predicate);
        root.addProperty("model", model);
        return root;
    }

    private static int customModelData(JsonObject override) {
        try {
            JsonObject predicate = override.getAsJsonObject("predicate");
            return predicate != null && predicate.has("custom_model_data")
                    ? predicate.get("custom_model_data").getAsInt() : Integer.MAX_VALUE;
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    private void write(File file, String content, List<String> warnings) {
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warnings.add("Écriture boss pack échouée : " + file.getName() + " (" + e.getMessage() + ")");
        }
    }

    private static String safe(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private record OverrideDef(int customModelData, String model) {}
}
