package com.mooncore.core.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Déclare les métadonnées d'un {@link MoonModule}.
 * <p>
 * {@code depends} liste les ids des modules qui doivent être ENABLED <b>avant</b> celui-ci
 * (dépendance dure : absente/désactivée → ce module échoue). {@code softDepends} n'impose
 * qu'un ordre de chargement <i>si</i> la dépendance est présente.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {

    /** Identifiant unique et stable (clé dans {@code config.yml > modules}). */
    String id();

    /** Nom lisible (logs, commandes). */
    String name();

    /** Ids des dépendances dures. */
    String[] depends() default {};

    /** Ids des dépendances molles (ordre seulement). */
    String[] softDepends() default {};
}
