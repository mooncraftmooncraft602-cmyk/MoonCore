package com.mooncore.modules.audio;

/**
 * Définition d'une piste audio. {@code sound} est une clé Minecraft/resource-pack
 * (ex. {@code minecraft:music.creative} ou {@code mooncore:boss_theme}).
 * {@code bedrockSound} est un repli optionnel pour les clients Bedrock.
 * {@code lengthSeconds} sert d'intervalle de relance quand {@code loop} est vrai
 * (Minecraft n'a pas de boucle native : on rejoue la piste à intervalle).
 */
public record Track(String id, String sound, String bedrockSound,
                    float volume, float pitch, boolean loop,
                    int fadeIn, int fadeOut, int lengthSeconds) {
    public Track {
        if (volume <= 0) volume = 1f;
        if (pitch <= 0) pitch = 1f;
        if (lengthSeconds <= 0) lengthSeconds = 60;
    }

    /** Clé sonore à utiliser pour un joueur Bedrock (repli si défini). */
    public String soundFor(boolean bedrock) {
        return (bedrock && bedrockSound != null && !bedrockSound.isBlank()) ? bedrockSound : sound;
    }
}
