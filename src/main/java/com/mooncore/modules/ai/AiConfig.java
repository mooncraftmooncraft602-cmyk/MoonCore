package com.mooncore.modules.ai;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Configuration de l'assistant IA, chargée côté serveur uniquement. La clé API
 * <b>ne quitte jamais le serveur</b> : elle n'est jamais envoyée aux joueurs, jamais
 * journalisée, jamais exposée par une commande.
 */
public final class AiConfig {

    private final String provider;       // "anthropic" | "openai"
    private final String model;
    private final String apiKey;
    private final String endpointOverride;
    private final double temperature;
    private final int timeoutSeconds;
    private final int maxRequestsPerMinute;
    private final int maxOutputTokens;
    private final List<String> availableModels;
    private final boolean generateTextures;
    private final String imageModel;
    private final int textureSize;
    private final int texturePalette;
    private final boolean textureDither;

    private AiConfig(String provider, String model, String apiKey, String endpointOverride,
                     double temperature, int timeoutSeconds, int maxRequestsPerMinute,
                     int maxOutputTokens, List<String> availableModels,
                     boolean generateTextures, String imageModel, int textureSize,
                     int texturePalette, boolean textureDither) {
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey;
        this.endpointOverride = endpointOverride;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxOutputTokens = maxOutputTokens;
        this.availableModels = availableModels;
        this.generateTextures = generateTextures;
        this.imageModel = imageModel;
        this.textureSize = textureSize;
        this.texturePalette = texturePalette;
        this.textureDither = textureDither;
    }

    public static AiConfig from(FileConfiguration cfg) {
        String provider = cfg.getString("provider", "anthropic").toLowerCase(Locale.ROOT);
        String model = cfg.getString("model", provider.equals("openai") ? "gpt-4o-mini" : "claude-opus-4-8");
        String apiKey = cfg.getString("api-key", "");
        String endpoint = cfg.getString("endpoint", "");
        double temp = cfg.getDouble("temperature", 0.7);
        int timeout = cfg.getInt("timeout-seconds", 30);
        int rate = cfg.getInt("max-requests-per-minute", 10);
        int maxTokens = cfg.getInt("max-output-tokens", 1500);
        List<String> models = cfg.getStringList("available-models");
        if (models.isEmpty()) {
            models = provider.equals("openai")
                    ? List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1")
                    : List.of("claude-opus-4-8", "claude-sonnet-4-6", "claude-haiku-4-5-20251001");
        }
        boolean genTex = cfg.getBoolean("generate-textures", false);
        String imageModel = cfg.getString("image-model", "grok-imagine-image");
        int texSize = cfg.getInt("texture-size", 64);
        int texPalette = cfg.getInt("texture-palette", 0); // 0 = pas de quantization
        boolean texDither = cfg.getBoolean("texture-dither", false);
        return new AiConfig(provider, model, apiKey, endpoint, temp, timeout, rate, maxTokens, models,
                genTex, imageModel, texSize, texPalette, texDither);
    }

    public String provider() { return provider; }
    public String model() { return model; }
    public String apiKey() { return apiKey; }
    public String endpointOverride() { return endpointOverride; }
    public double temperature() { return temperature; }
    public int timeoutSeconds() { return timeoutSeconds; }
    public int maxRequestsPerMinute() { return maxRequestsPerMinute; }
    public int maxOutputTokens() { return maxOutputTokens; }
    public List<String> availableModels() { return availableModels; }
    public boolean generateTextures() { return generateTextures; }
    public String imageModel() { return imageModel; }
    public int textureSize() { return textureSize; }
    public int texturePalette() { return texturePalette; }
    public boolean textureDither() { return textureDither; }

    public boolean hasApiKey() { return apiKey != null && !apiKey.isBlank(); }

    /** Endpoint de génération d'image dérivé de l'endpoint de chat (même base API). */
    public String imageEndpoint() {
        String base = endpointOverride;
        if (base == null || base.isBlank()) {
            return provider.equals("openai")
                    ? "https://api.openai.com/v1/images/generations"
                    : "https://api.x.ai/v1/images/generations";
        }
        if (base.contains("/chat/completions")) return base.replace("/chat/completions", "/images/generations");
        return base.endsWith("/") ? base + "images/generations" : base + "/images/generations";
    }

    /** Copie avec un modèle différent (pour {@code /moon ai model set}). */
    public AiConfig withModel(String newModel) {
        return new AiConfig(provider, newModel, apiKey, endpointOverride, temperature,
                timeoutSeconds, maxRequestsPerMinute, maxOutputTokens, availableModels,
                generateTextures, imageModel, textureSize, texturePalette, textureDither);
    }
}
