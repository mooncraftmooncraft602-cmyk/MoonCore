package com.mooncore.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Helpers de compatibilité multi-plateforme (Java / Bedrock via Geyser+Floodgate).
 * <p>
 * La détection Bedrock utilise l'API Floodgate <b>par réflexion</b> si elle est
 * présente (aucune dépendance dure : le serveur peut tourner sans Floodgate).
 * À défaut, repli sur l'heuristique : Floodgate attribue aux joueurs Bedrock un
 * UUID dont les bits de poids fort valent 0.
 */
public final class Compat {

    private static final Object FLOODGATE_API;
    private static final Method IS_FLOODGATE_PLAYER;

    static {
        Object api = null;
        Method method = null;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Throwable ignored) {
            // Floodgate absent : on utilisera l'heuristique UUID.
        }
        FLOODGATE_API = api;
        IS_FLOODGATE_PLAYER = method;
    }

    private Compat() {}

    /** {@code true} si le joueur est connecté via Bedrock (Geyser/Floodgate). */
    public static boolean isBedrock(Player p) {
        if (p == null) return false;
        if (FLOODGATE_API != null && IS_FLOODGATE_PLAYER != null) {
            try {
                Object result = IS_FLOODGATE_PLAYER.invoke(FLOODGATE_API, p.getUniqueId());
                if (result instanceof Boolean b) return b;
            } catch (Throwable ignored) {
                // Repli sur l'heuristique.
            }
        }
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    public static boolean floodgatePresent() { return FLOODGATE_API != null; }
}
