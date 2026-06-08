package com.mooncore.modules.leaderboard;

/**
 * Définition d'un classement. {@code source} est soit une clé de stat (table statistics),
 * soit le mot-clé spécial {@code playtime} (temps de jeu du profil).
 */
public record LeaderboardDefinition(String id, String title, String source, int size) {

    public static final String SOURCE_PLAYTIME = "playtime";

    public boolean isPlaytime() {
        return SOURCE_PLAYTIME.equalsIgnoreCase(source);
    }
}
