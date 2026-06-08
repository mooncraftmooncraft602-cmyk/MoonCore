package com.mooncore.modules.ai;

import java.util.Locale;

/**
 * Erreur d'appel IA portant le code HTTP et un indicateur « quota/crédits épuisés »
 * pour pouvoir alerter les admins en jeu.
 */
public final class AiException extends RuntimeException {

    private final int status;
    private final boolean quota;

    public AiException(int status, String detail) {
        super(buildMessage(status, detail));
        this.status = status;
        this.quota = detectQuota(status, detail);
    }

    public int status() { return status; }
    public boolean quota() { return quota; }

    private static String buildMessage(int status, String detail) {
        String d = detail == null || detail.isBlank() ? "" : " — " + detail;
        return "HTTP " + status + d;
    }

    /** Détecte un épuisement de crédits/quota/facturation à partir du code et du message. */
    private static boolean detectQuota(int status, String detail) {
        if (status == 402) return true; // Payment Required
        String t = (detail == null ? "" : detail).toLowerCase(Locale.ROOT);
        if (status == 429 && (t.contains("quota") || t.contains("credit") || t.contains("billing")
                || t.contains("balance") || t.contains("insufficient") || t.contains("exceeded"))) {
            return true;
        }
        return t.contains("insufficient_quota") || t.contains("insufficient credit")
                || t.contains("no credits") || t.contains("out of credits")
                || t.contains("payment required") || t.contains("billing")
                || (t.contains("quota") && t.contains("exceed"));
    }
}
