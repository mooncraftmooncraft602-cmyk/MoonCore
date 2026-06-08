package com.mooncore.modules.home;

import java.util.Locale;
import java.util.regex.Pattern;

/** Règles pures des homes (validation, limites) — testables sans serveur. */
public final class HomesLogic {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private HomesLogic() {}

    public static boolean isValidName(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Peut-on créer un home de plus ? ({@code max} ≤ 0 = illimité). */
    public static boolean canCreate(int current, int max) {
        return max <= 0 || current < max;
    }
}
