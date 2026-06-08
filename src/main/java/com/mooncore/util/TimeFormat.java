package com.mooncore.util;

/** Formatage de durées (pur, testable). */
public final class TimeFormat {

    private TimeFormat() {}

    /** Formate une durée en secondes : ex. "1j 2h 3m 4s", "5m 0s", "0s". */
    public static String shortDuration(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("j ");
        if (days > 0 || hours > 0) sb.append(hours).append("h ");
        if (days > 0 || hours > 0 || minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString();
    }
}
