package com.mooncore.command;

import com.mooncore.MoonCore;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Une sous-commande de {@code /moon}. */
public interface SubCommand {

    /** Nom (premier argument), en minuscules. */
    String name();

    /** Alias éventuels. */
    default List<String> aliases() { return List.of(); }

    /** Permission requise (null = aucune). */
    String permission();

    /** Description courte (aide). */
    String description();

    /** Catégorie d'aide : "player" ou "admin". */
    String category();

    /** Réservé aux joueurs ? */
    default boolean playerOnly() { return false; }

    void execute(MoonCore plugin, CommandSender sender, String[] args);

    default List<String> tabComplete(MoonCore plugin, CommandSender sender, String[] args) {
        return List.of();
    }
}
