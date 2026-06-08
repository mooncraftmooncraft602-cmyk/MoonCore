package com.mooncore.modules.placeholder;

import java.util.Locale;

/** Aides pures de parsing des placeholders MoonCore (testables sans serveur). */
public final class Placeholders {

    private Placeholders() {}

    /** Référence à une entrée de classement extraite d'un placeholder. */
    public record LeaderboardRef(String board, int rank, String field) {}

    /**
     * Parse {@code leaderboard_<board>_<rank>_<name|value>}.
     * Le {@code board} peut contenir des underscores (rejoint). Retourne null si invalide.
     */
    public static LeaderboardRef parseLeaderboard(String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (!p.startsWith("leaderboard_")) return null;
        String rest = p.substring("leaderboard_".length());
        String[] parts = rest.split("_");
        if (parts.length < 3) return null;

        String field = parts[parts.length - 1];
        if (!field.equals("name") && !field.equals("value")) return null;

        int rank;
        try {
            rank = Integer.parseInt(parts[parts.length - 2]);
        } catch (NumberFormatException e) {
            return null;
        }
        if (rank < 1) return null;

        StringBuilder board = new StringBuilder();
        for (int i = 0; i < parts.length - 2; i++) {
            if (i > 0) board.append('_');
            board.append(parts[i]);
        }
        if (board.length() == 0) return null;
        return new LeaderboardRef(board.toString(), rank, field);
    }
}
