package com.mooncore.modules.ai.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mooncore.modules.ai.AiConfig;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/** Fournisseur OpenAI / compatible (Chat Completions). */
public final class OpenAiProvider implements AiProvider {

    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    @Override public String name() { return "openai"; }

    @Override
    public HttpRequest buildRequest(AiConfig cfg, String systemPrompt, String userPrompt, Gson gson) {
        JsonObject body = new JsonObject();
        body.addProperty("model", cfg.model());
        body.addProperty("temperature", cfg.temperature());
        body.addProperty("max_tokens", cfg.maxOutputTokens());

        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        body.add("messages", messages);

        String endpoint = cfg.endpointOverride() == null || cfg.endpointOverride().isBlank()
                ? DEFAULT_ENDPOINT : cfg.endpointOverride();

        return HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(cfg.timeoutSeconds()))
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + cfg.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    @Override
    public String extractText(String responseBody, Gson gson) {
        JsonObject root = gson.fromJson(responseBody, JsonObject.class);
        if (root == null) return null;
        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            throw new IllegalStateException("API OpenAI : " + err.get("message").getAsString());
        }
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        return msg != null && msg.has("content") ? msg.get("content").getAsString() : null;
    }
}
