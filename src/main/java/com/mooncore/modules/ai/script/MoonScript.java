package com.mooncore.modules.ai.script;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

/**
 * Contrat d'un script Java généré (mode développeur). L'IA produit une classe
 * {@code public final class GeneratedScript implements ...MoonScript} sans package.
 * Le moteur la compile, la charge et appelle {@link #run}.
 * <p>
 * AVERTISSEMENT : ce mécanisme exécute du code arbitraire — réservé à l'owner (op),
 * activé manuellement, avec revue + confirmation. Ce n'est PAS de la donnée validée.
 */
public interface MoonScript {
    void run(Plugin plugin, CommandSender sender) throws Exception;
}
