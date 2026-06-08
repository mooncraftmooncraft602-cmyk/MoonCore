package com.mooncore.api.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Service public des objets endgame custom. */
public interface CustomItemService {

    /** Ids disponibles. */
    Set<String> ids();

    /** Crée un exemplaire de l'objet (null si id inconnu). */
    ItemStack create(String id);

    /** Donne l'objet au joueur (false si id inconnu). */
    boolean give(Player player, String id);

    /** Id custom porté par un objet, ou null s'il n'en a pas. */
    String idOf(ItemStack item);
}
