package com.mooncore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Conversion de texte MiniMessage → {@link Component}. Tous les messages utilisateur
 * passent par ici ; aucun texte legacy (§) n'est utilisé.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    /** Parse une chaîne MiniMessage. */
    public static Component mm(String input) {
        return MM.deserialize(input == null ? "" : input);
    }

    /** Parse avec des placeholders {@code <clé>} → valeur (paires clé, valeur). */
    public static Component mm(String input, String... placeholderPairs) {
        if (placeholderPairs.length == 0) {
            return mm(input);
        }
        if (placeholderPairs.length % 2 != 0) {
            throw new IllegalArgumentException("placeholderPairs doit contenir un nombre pair d'éléments");
        }
        TagResolver[] resolvers = new TagResolver[placeholderPairs.length / 2];
        for (int i = 0; i < resolvers.length; i++) {
            resolvers[i] = Placeholder.unparsed(placeholderPairs[i * 2], placeholderPairs[i * 2 + 1]);
        }
        return MM.deserialize(input == null ? "" : input, resolvers);
    }

    /** Retire tout formatage MiniMessage (texte brut). */
    public static String strip(String input) {
        return MM.stripTags(input == null ? "" : input);
    }
}
