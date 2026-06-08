package com.mooncore.api.customblock;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Service public des blocs custom (placeables via note_block + resource pack). */
public interface CustomBlockService {

    Set<String> ids();

    /** Item plaçable d'un bloc custom ({@code null} si inconnu). */
    ItemStack item(String id, int amount);

    /** Donne l'item plaçable au joueur. */
    boolean give(Player player, String id, int amount);

    /** Id du bloc custom à cette position, ou {@code null} si ce n'en est pas un. */
    String idAt(Block block);

    void reloadDefinitions();
}
