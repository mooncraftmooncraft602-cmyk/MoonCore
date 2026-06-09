package com.mooncore.modules.customblock;

import com.mooncore.api.customitem.CustomItemManagerService;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import com.mooncore.util.Text;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gameplay des blocs custom : pose, minage, drops, worldgen et blocage des
 * mecaniques de note block pour garder l'etat stable.
 */
public final class CustomBlockListener implements Listener {

    private final CustomBlockManagerModule module;
    private final Map<BlockKey, Integer> breakProgress = new ConcurrentHashMap<>();

    public CustomBlockListener(CustomBlockManagerModule module) {
        this.module = module;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        String id = module.idFromItem(e.getItemInHand());
        if (id == null) return;
        CustomBlockDef def = module.rawDef(id);
        if (def == null) return;
        module.placeState(e.getBlockPlaced(), def);
        breakProgress.remove(BlockKey.of(e.getBlockPlaced()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        String id = module.idAt(e.getBlock());
        if (id == null) return;
        CustomBlockDef def = module.rawDef(id);
        if (def == null) return;

        ToolInfo tool = toolInfo(e.getPlayer().getInventory().getItemInMainHand());
        if (!canHarvest(def, tool)) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Text.mm("<red>Outil requis : <white>" + toolRequirement(def)));
            return;
        }

        BlockKey key = BlockKey.of(e.getBlock());
        if (def.breakDurability() > 1) {
            Integer prev = breakProgress.get(key);
            int progress = (prev == null ? 0 : prev) + minePower(def, tool);
            breakProgress.put(key, progress);
            if (progress < def.breakDurability()) {
                e.setCancelled(true);
                e.getPlayer().sendActionBar(Text.mm("<yellow>Resistance du bloc : <white>"
                        + progress + "/" + def.breakDurability()));
                return;
            }
            breakProgress.remove(key);
        }

        e.setDropItems(false);
        Block b = e.getBlock();
        ItemStack drop = resolveDrop(def);
        if (drop != null && b.getWorld() != null) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        if (def.dropXp() > 0) e.setExpToDrop(def.dropXp());
    }

    private ItemStack resolveDrop(CustomBlockDef def) {
        if (def.dropItemId() != null && !def.dropItemId().isBlank()) {
            CustomItemManagerService ci = module.customItems();
            if (ci != null) {
                ItemStack it = ci.create(def.dropItemId(), 1);
                if (it != null) return it;
            }
        }
        return module.item(def.id(), 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        protectExplosion(e.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        protectExplosion(e.blockList());
    }

    private void protectExplosion(java.util.List<Block> blocks) {
        blocks.removeIf(block -> {
            String id = module.idAt(block);
            if (id == null) return false;
            CustomBlockDef def = module.rawDef(id);
            return def != null && def.blastResistance() >= 4.0;
        });
    }

    @EventHandler
    public void onNotePlay(NotePlayEvent e) {
        if (module.idAt(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.NOTE_BLOCK) return;
        if (module.idAt(e.getClickedBlock()) != null) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NOTE_BLOCK && module.idAt(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!e.isNewChunk() || !module.worldgenEnabled()) return;
        boolean any = module.rawDefs().values().stream().anyMatch(CustomBlockDef::generate);
        if (!any) return;
        generate(e.getChunk());
    }

    private void generate(Chunk chunk) {
        World w = chunk.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int minH = w.getMinHeight(), maxH = w.getMaxHeight();
        for (CustomBlockDef def : module.rawDefs().values()) {
            if (!def.generate()) continue;
            int loY = Math.max(minH, def.minY());
            int hiY = Math.min(maxH - 1, def.maxY());
            if (hiY <= loY) continue;
            for (int v = 0; v < def.veinsPerChunk(); v++) {
                int bx = rnd.nextInt(16), bz = rnd.nextInt(16);
                int by = rnd.nextInt(loY, hiY + 1);
                placeVein(chunk, bx, by, bz, def, rnd);
            }
        }
    }

    private void placeVein(Chunk chunk, int bx, int by, int bz, CustomBlockDef def, ThreadLocalRandom rnd) {
        World w = chunk.getWorld();
        int placed = 0;
        for (int attempt = 0; attempt < def.veinSize() * 2 && placed < def.veinSize(); attempt++) {
            int x = (chunk.getX() << 4) + Math.min(15, Math.max(0, bx + rnd.nextInt(3) - 1));
            int z = (chunk.getZ() << 4) + Math.min(15, Math.max(0, bz + rnd.nextInt(3) - 1));
            int y = Math.min(w.getMaxHeight() - 1, Math.max(w.getMinHeight(), by + rnd.nextInt(3) - 1));
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() == def.replace()) {
                module.placeState(b, def);
                placed++;
            }
        }
    }

    private boolean canHarvest(CustomBlockDef def, ToolInfo info) {
        if (def.requiredTool() == ToolKind.NONE) return true;
        return info.kind() == def.requiredTool() && info.tier().level() >= def.minToolTier().level();
    }

    private int minePower(CustomBlockDef def, ToolInfo info) {
        if (def.requiredTool() == ToolKind.NONE) return Math.max(1, info.tier().level() + 1);
        return Math.max(1, info.tier().level() - def.minToolTier().level() + 1);
    }

    private ToolInfo toolInfo(ItemStack item) {
        if (item == null || item.getType().isAir()) return new ToolInfo(ToolKind.NONE, ToolTier.HAND);
        CustomItemManagerModule ci = module.mc().moduleManager().get(CustomItemManagerModule.class);
        if (ci != null) {
            String customId = ci.idOf(item);
            CustomItemDef def = customId == null ? null : ci.rawDef(customId);
            if (def != null && def.toolKind() != ToolKind.NONE) return new ToolInfo(def.toolKind(), def.toolTier());
        }
        return new ToolInfo(ToolKind.fromMaterial(item.getType()), ToolTier.fromMaterial(item.getType()));
    }

    private static String toolRequirement(CustomBlockDef def) {
        if (def.requiredTool() == ToolKind.NONE) return "aucun";
        return def.requiredTool().label() + " " + def.minToolTier().label() + "+";
    }

    private record ToolInfo(ToolKind kind, ToolTier tier) {}

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}
