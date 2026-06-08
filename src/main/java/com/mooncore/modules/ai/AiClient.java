package com.mooncore.modules.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mooncore.modules.ai.provider.AiProvider;
import com.mooncore.util.ImageUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Client IA asynchrone. Aucune requête ne touche le thread principal : tout passe par
 * le pool interne de {@link HttpClient}, et les callbacks doivent re-synchroniser via
 * le scheduler avant toute API Bukkit. Gère timeout, erreurs réseau et limitation de
 * débit (fenêtre glissante d'une minute).
 */
public final class AiClient {

    private final Gson gson = new Gson();
    private final HttpClient http;
    private volatile AiConfig config;
    private volatile AiProvider provider;
    private final Deque<Long> recentRequests = new ArrayDeque<>();

    public AiClient(AiConfig config) {
        this.config = config;
        this.provider = AiProvider.forName(config.provider());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(5, config.timeoutSeconds())))
                .build();
    }

    public void updateConfig(AiConfig config) {
        this.config = config;
        this.provider = AiProvider.forName(config.provider());
    }

    public AiConfig config() { return config; }

    /** Réserve un créneau de débit ({@code true} si autorisé). */
    public synchronized boolean tryAcquireRate(long nowMs) {
        long windowStart = nowMs - 60_000L;
        while (!recentRequests.isEmpty() && recentRequests.peekFirst() < windowStart) {
            recentRequests.pollFirst();
        }
        if (recentRequests.size() >= config.maxRequestsPerMinute()) return false;
        recentRequests.addLast(nowMs);
        return true;
    }

    /**
     * Envoie une requête et renvoie le texte de complétion. Le future se complète
     * exceptionnellement en cas d'erreur réseau, de timeout, de code HTTP ≥ 400 ou
     * d'erreur API. À appeler hors thread principal (renvoie un future de toute façon).
     */
    public CompletableFuture<String> ask(String systemPrompt, String userPrompt) {
        AiConfig cfg = this.config;
        AiProvider prov = this.provider;
        if (!cfg.hasApiKey()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Aucune clé API configurée (modules/ai-assistant.yml)."));
        }
        try {
            var request = prov.buildRequest(cfg, systemPrompt, userPrompt, gson);
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        if (resp.statusCode() >= 400) {
                            throw new java.util.concurrent.CompletionException(
                                    new AiException(resp.statusCode(), parseError(resp.body())));
                        }
                        String text = prov.extractText(resp.body(), gson);
                        if (text == null || text.isBlank()) {
                            throw new java.util.concurrent.CompletionException(
                                    new IllegalStateException("Réponse IA vide."));
                        }
                        return text;
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Génère une texture d'item via l'API image (xAI/OpenAI), renvoie un PNG carré
     * redimensionné prêt pour un resource pack. À appeler hors thread principal.
     */
    public CompletableFuture<byte[]> generateTexture(String prompt) {
        return generateTexture(prompt, true);
    }

    public CompletableFuture<byte[]> generateTexture(String prompt, boolean removeBackground) {
        AiConfig cfg = this.config;
        if (!cfg.hasApiKey()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Aucune clé API."));
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.imageModel());
        body.addProperty("prompt", prompt);
        body.addProperty("n", 1);
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.imageEndpoint()))
                .timeout(Duration.ofSeconds(Math.max(30, cfg.timeoutSeconds())))
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenCompose(resp -> {
                    if (resp.statusCode() >= 400) {
                        throw new CompletionException(new AiException(resp.statusCode(), parseError(resp.body())));
                    }
                    JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
                    JsonArray data = root != null ? root.getAsJsonArray("data") : null;
                    if (data == null || data.isEmpty()) {
                        throw new CompletionException(new IllegalStateException("Réponse image vide."));
                    }
                    JsonObject first = data.get(0).getAsJsonObject();
                    if (first.has("b64_json")) {
                        byte[] raw = Base64.getDecoder().decode(first.get("b64_json").getAsString());
                        return CompletableFuture.completedFuture(raw);
                    }
                    if (!first.has("url")) {
                        throw new CompletionException(new IllegalStateException("Réponse image sans url ni b64_json."));
                    }
                    String url = first.get("url").getAsString();
                    return http.sendAsync(HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(Math.max(30, cfg.timeoutSeconds())))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray()).thenApply(HttpResponse::body);
                })
                .thenApply(raw -> {
                    try {
                        return removeBackground
                                ? ImageUtil.toItemIcon(raw, cfg.textureSize(), cfg.texturePalette(), cfg.textureDither())
                                : ImageUtil.toSquareOpaque(raw, cfg.textureSize(), cfg.texturePalette(), cfg.textureDither());
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });
    }

    /**
     * Extrait un message d'erreur lisible du corps de réponse, en gérant les formats
     * OpenAI ({@code {"error":{"message":...}}}) et xAI ({@code {"code":...,"error":...}}).
     */
    private String parseError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root == null) return body;
            if (root.has("error")) {
                var err = root.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    return err.getAsJsonObject().get("message").getAsString();
                }
                if (err.isJsonPrimitive()) return err.getAsString();
            }
            if (root.has("message")) return root.get("message").getAsString();
            if (root.has("code")) return root.get("code").getAsString();
            return body;
        } catch (Exception e) {
            return body;
        }
    }
}
