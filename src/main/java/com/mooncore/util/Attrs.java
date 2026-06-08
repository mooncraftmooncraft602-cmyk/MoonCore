package com.mooncore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import java.util.Locale;

/**
 * Résolution version-safe des attributs vanilla.
 * <p>
 * En 1.21.3+, l'enum {@link Attribute} a été refondu : les constantes ont perdu le
 * préfixe {@code GENERIC_} (ex. {@code GENERIC_MAX_HEALTH} → {@code MAX_HEALTH}) et les
 * clés de registre ont perdu le préfixe {@code generic.}. Référencer directement
 * {@code Attribute.GENERIC_*} (compilé contre 1.21.1) provoque un
 * {@link NoSuchFieldError} au runtime sur 1.21.3 → 1.21.11+.
 * <p>
 * Ce helper passe par {@link Registry#ATTRIBUTE} et essaie la clé moderne (sans
 * préfixe) puis la clé legacy ({@code generic.*}). Il compile sur 1.21.1 (aucune
 * référence aux champs renommés) et fonctionne de 1.21.1 à 1.21.11+.
 */
public final class Attrs {

    public static final Attribute MAX_HEALTH = resolve("max_health");
    public static final Attribute ATTACK_DAMAGE = resolve("attack_damage");
    public static final Attribute ATTACK_SPEED = resolve("attack_speed");
    public static final Attribute MOVEMENT_SPEED = resolve("movement_speed");
    public static final Attribute ARMOR = resolve("armor");
    public static final Attribute ARMOR_TOUGHNESS = resolve("armor_toughness");
    public static final Attribute KNOCKBACK_RESISTANCE = resolve("knockback_resistance");
    public static final Attribute LUCK = resolve("luck");

    private Attrs() {}

    /** Résout par clé courte ({@code max_health}), en essayant moderne puis legacy. */
    public static Attribute resolve(String shortKey) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(shortKey));
        if (a == null) a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + shortKey));
        return a;
    }

    /**
     * Résout depuis n'importe quelle forme : {@code "max_health"},
     * {@code "generic.max_health"}, {@code "GENERIC_MAX_HEALTH"} ou
     * {@code "minecraft:..."}. Retourne {@code null} si inconnu.
     */
    public static Attribute byKey(String key) {
        if (key == null) return null;
        String k = key.toLowerCase(Locale.ROOT).replace("minecraft:", "").trim();
        if (k.startsWith("generic_")) k = k.substring("generic_".length());
        if (k.startsWith("generic.")) k = k.substring("generic.".length());
        k = k.replace('.', '_');
        return resolve(k);
    }
}
