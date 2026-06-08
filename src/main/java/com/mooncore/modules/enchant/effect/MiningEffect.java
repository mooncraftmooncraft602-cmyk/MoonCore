package com.mooncore.modules.enchant.effect;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;

/** Effet déclenché quand le porteur casse un bloc avec l'outil. */
@FunctionalInterface
public interface MiningEffect {
    void onMine(Player miner, Block block, int level, BlockBreakEvent event);
}
