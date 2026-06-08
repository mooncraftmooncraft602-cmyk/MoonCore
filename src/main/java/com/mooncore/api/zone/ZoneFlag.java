package com.mooncore.api.zone;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Protections (flags) applicables à une zone. La clé textuelle ({@link #key()}) sert
 * en config et en commande (ex. {@code noblockbreak}).
 * <p>
 * Certains flags sont appliqués par le ZoneManager lui-même (interactions monde :
 * casse, pose, pvp, interact…) ; d'autres sont consommés par les modules concernés
 * via {@link ZoneService} (ex. {@code NO_HOME}/{@code NO_TPA} par le module de téléport,
 * {@code NO_SPAWNER} par AntiFarm).
 */
public enum ZoneFlag {
    NO_HOME("home", false),
    NO_TPA("tpa", false),
    NO_BED("bed", false),
    NO_ELYTRA("elytra", false),
    NO_ENDERPEARL("enderpearl", false),
    NO_CLAIM("claim", false),
    NO_BLOCK_BREAK("blockbreak", false),
    NO_BLOCK_PLACE("blockplace", false),
    NO_PVP("pvp", false),
    FORCE_PVP("forcepvp", false),
    NO_FLIGHT("flight", false),
    NO_GRIEF("grief", false),
    NO_SPAWNER("spawner", false),
    NO_MOB_SPAWN("mobspawn", false),
    NO_ITEM_DROP("itemdrop", false),
    NO_ITEM_PICKUP("itempickup", false),
    NO_INTERACT("interact", false),
    NO_ENTER("enter", false),
    NO_LEAVE("leave", false),
    NO_DAMAGE("damage", false),
    NO_COMMAND("command", false);

    private static final Map<String, ZoneFlag> BY_KEY = new HashMap<>();
    static {
        for (ZoneFlag f : values()) {
            BY_KEY.put("no" + f.suffix, f);
            BY_KEY.put(f.suffix, f);
        }
        // alias lisibles pour les flags "force"
        BY_KEY.put("forcepvp", FORCE_PVP);
    }

    private final String suffix;
    private final boolean defaultValue;

    ZoneFlag(String suffix, boolean defaultValue) {
        this.suffix = suffix;
        this.defaultValue = defaultValue;
    }

    /** Clé canonique (ex. {@code noblockbreak}, {@code forcepvp}). */
    public String key() {
        return this == FORCE_PVP ? "forcepvp" : "no" + suffix;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public static Optional<ZoneFlag> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key.toLowerCase(Locale.ROOT)));
    }
}
