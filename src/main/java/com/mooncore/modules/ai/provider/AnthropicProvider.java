package com.mooncore.modules.ai.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mooncore.modules.ai.AiConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/** Fournisseur Anthropic (API Messages). */
public final class AnthropicProvider implements AiProvider {

    private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    @Override public String name() { return "anthropic"; }

    @Override
    public HttpRequest buildRequest(AiConfig cfg, String systemPrompt, String userPrompt, Gson gson) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model());
        body.addProperty("max_tokens", cfg.maxOutputTokens());
        body.addProperty("temperature", cfg.temperature());
        body.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        body.add("messages", messages);

        String endpoint = cfg.endpointOverride() == null || cfg.endpointOverride().isBlank()
                ? DEFAULT_ENDPOINT : cfg.endpointOverride();

        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("content-type", "application/json")
                .header("x-api-key", cfg.apiKey())
                .header("anthropic-version", API_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
    }

    @Override
    public String extractText(String responseBody, Gson gson) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root == null) return null;
        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            throw new IllegalStateException("API Anthropic : " + err.get("message").getAsString());
        }
        JsonArray content = root.getAsJsonArray("content");
        if (content == null || content.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (var el : content) {
            JsonObject block = el.getAsJsonObject();
            if (block.has("text")) sb.append(block.get("text").getAsString());
        }
        return sb.toString();
    }
}
