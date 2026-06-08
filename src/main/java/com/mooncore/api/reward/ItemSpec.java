package com.mooncore.api.reward;

import java.util.List;

/**
 * Description d'un objet à octroyer. {@code name} et {@code lore} sont au format MiniMessage
 * (peuvent être nuls). {@code material} est un nom Bukkit (ex. {@code DIAMOND}).
 */
public record ItemSpec(String material, int amount, String name, List<String> lore) {
    public ItemSpec {
        if (amount <= 0) amount = 1;
        if (lore == null) lore = List.of();
    }
}
