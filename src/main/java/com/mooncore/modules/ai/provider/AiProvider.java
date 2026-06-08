package com.mooncore.modules.ai.provider;

import com.google.gson.Gson;
import com.mooncore.modules.ai.AiConfig;

import java.net.http.HttpRequest;

/**
 * Abstraction d'un fournisseur d'IA. Permet de brancher Anthropic, OpenAI ou tout
 * service compatible sans toucher au reste du module.
 */
public interface AiProvider {

    String name();

    /** Construit la requête HTTP (endpoint, en-têtes, corps JSON, timeout). */
    HttpRequest buildRequest(AiConfig cfg, String systemPrompt, String userPrompt, Gson gson);

    /** Extrait le texte de complétion du corps de réponse JSON. */
    String extractText(String responseBody, Gson gson);

    static AiProvider forName(String provider) {
        return "openai".equalsIgnoreCase(provider) ? new OpenAiProvider() : new AnthropicProvider();
    }
}
