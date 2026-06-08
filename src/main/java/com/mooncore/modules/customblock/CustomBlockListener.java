package com.mooncore.modules.customblock;

import com.mooncore.api.customitem.CustomItemManagerService;
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
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Gameplay des blocs custom : pose, minage (drops custom), neutralisation des mécaniques
 * de note block (son / changement de note / d'instrument) pour garder l'état stable, et
 * génération de minerais dans les nouveaux chunks.
 */
public final class CustomBlockListener implements Listener {

    private final CustomBlockManagerModule module;

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
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        String id = module.idAt(e.getBlock());
        if (id == null) return;
        CustomBlockDef def = module.rawDef(id);
        if (def == null) return;

        e.setDropItems(false); // pas de drop vanilla (note block)
        ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
        boolean okTool = !def.requiresPickaxe() || isPickaxe(tool.getType());
        if (!okTool) return; // mauvais outil → rien ne tombe (comme un vrai minerai)

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
        return module.item(def.id(), 1); // sinon, se drop lui-même
    }

    /** Empêche le son du note block (les blocs custom ne doivent pas « jouer »). */
    @EventHandler
    public void onNotePlay(NotePlayEvent e) {
        if (module.idAt(e.getBlock()) != null) e.setCancelled(true);
    }

    /** Empêche le changement de note (clic droit) sans bloquer la pose contre le bloc. */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() != Material.NOTE_BLOCK) return;
        if (module.idAt(e.getClickedBlock()) != null) {
            e.setUseInteractedBlock(Event.Result.DENY); // pas de bump de note ; pose toujours possible
        }
    }

    /** Empêche le recalcul d'instrument (mise à jour de voisin) qui changerait l'état. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.NOTE_BLOCK && module.idAt(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    // ---- Worldgen (minerais dans les nouveaux chunks) ----

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

    private static boolean isPickaxe(Material m) {
        return m == Material.WOODEN_PICKAXE || m == Material.STONE_PICKAXE || m == Material.IRON_PICKAXE
                || m == Material.GOLDEN_PICKAXE || m == Material.DIAMOND_PICKAXE || m == Material.NETHERITE_PICKAXE;
    }
}
