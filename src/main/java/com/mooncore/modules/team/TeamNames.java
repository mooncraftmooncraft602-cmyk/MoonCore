package com.mooncore.modules.team;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation et normalisation des noms d'équipe (pur, testable). */
public final class TeamNames {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private TeamNames() {}

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    /** Identifiant canonique dérivé du nom (minuscules). */
    public static String toId(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
