package com.mooncore.modules.customblock;

import com.mooncore.api.customblock.CustomBlockService;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.customblock.command.CustomBlockSubCommand;
import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gestionnaire de blocs custom data-driven via états de {@code note_block} + resource pack.
 * Optionnellement, certains blocs se génèrent comme des minerais dans les nouveaux chunks.
 * Limite assumée : pas de régénération des chunks déjà explorés (worldgen vanilla figée).
 */
@ModuleInfo(id = "custom-block", name = "CustomBlockManager", softDepends = {"custom-item", "resource-pack"})
public final class CustomBlockManagerModule extends AbstractModule implements CustomBlockService {

    private final Map<String, CustomBlockDef> defs = new LinkedHashMap<>();
    private final Map<Integer, CustomBlockDef> byState = new LinkedHashMap<>();
    private NamespacedKey idKey;
    private CustomBlockStore store;
    private boolean worldgenEnabled = true;

    @Override
    protected void onEnable() {
        this.idKey = new NamespacedKey(plugin(), "cb_id");
        this.store = new CustomBlockStore(plugin().getDataFolder(), log());
        this.worldgenEnabled = moduleConfig().getBoolean("worldgen-enabled", true);
        reloadDefinitions();
        services().register(CustomBlockService.class, this);
        registerListener(new CustomBlockListener(this));
        plugin().rootCommand().register(new CustomBlockSubCommand(this));
        log().info("CustomBlockManager : " + defs.size() + " bloc(s) custom (capacité " + BlockStateMap.capacity() + ").");
    }

    @Override
    protected void onDisable() {
        services().unregister(CustomBlockService.class);
        defs.clear();
        byState.clear();
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        this.worldgenEnabled = moduleConfig().getBoolean("worldgen-enabled", true);
        reloadDefinitions();
    }

    public boolean worldgenEnabled() { return worldgenEnabled; }

    @Override
    public void reloadDefinitions() {
        defs.clear();
        byState.clear();
        defs.putAll(store.loadAll());
        // Assigne un état stable aux blocs qui n'en ont pas encore.
        for (CustomBlockDef def : defs.values()) {
            if (def.stateIndex() < 0) {
                int idx = nextFreeState();
                def.setStateIndex(idx);
                store.save(def);
            }
            byState.put(def.stateIndex(), def);
        }
    }

    private int nextFreeState() {
        java.util.Set<Integer> used = new java.util.HashSet<>(byState.keySet());
        for (CustomBlockDef d : defs.values()) if (d.stateIndex() >= 0) used.add(d.stateIndex());
        for (int i = 0; i < BlockStateMap.capacity(); i++) {
            if (!used.contains(i)) return i;
        }
        return -1; // capacité épuisée (800)
    }

    // ---- Service ----

    @Override public Set<String> ids() { return Set.copyOf(defs.keySet()); }

    @Override
    public ItemStack item(String id, int amount) {
        CustomBlockDef def = rawDef(id);
        if (def == null) return null;
        ItemStack it = new ItemStack(Material.NOTE_BLOCK, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(def.displayName()).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public boolean give(Player player, String id, int amount) {
        ItemStack it = item(id, amount);
        if (it == null) return false;
        player.getInventory().addItem(it).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    @Override
    public String idAt(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return null;
        if (!(block.getBlockData() instanceof NoteBlock nb)) return null;
        int idx = BlockStateMap.indexOf(nb);
        if (idx < 0) return null;
        CustomBlockDef def = byState.get(idx);
        return def == null ? null : def.id();
    }

    // ---- Internes ----

    public CustomBlockDef rawDef(String id) {
        return id == null ? null : defs.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, CustomBlockDef> rawDefs() { return defs; }

    public void put(CustomBlockDef def) {
        if (def.stateIndex() < 0) def.setStateIndex(nextFreeState());
        defs.put(def.id(), def);
        byState.put(def.stateIndex(), def);
        store.save(def);
    }

    public boolean removeDef(String id) {
        CustomBlockDef d = defs.remove(id.toLowerCase(Locale.ROOT));
        if (d != null) byState.remove(d.stateIndex());
        store.delete(id);
        return d != null;
    }

    public String idFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Applique l'état du bloc custom à une position (pose / worldgen). */
    public void placeState(Block block, CustomBlockDef def) {
        block.setType(Material.NOTE_BLOCK, false);
        NoteBlock data = (NoteBlock) block.getBlockData();
        BlockStateMap.apply(data, def.stateIndex());
        block.setBlockData(data, false); // sans physics → état stable
    }

    public CustomBlockStore store() { return store; }
    public NamespacedKey idKey() { return idKey; }
    public com.mooncore.MoonCore mc() { return plugin; }

    public com.mooncore.api.customitem.CustomItemManagerService customItems() {
        return services().get(com.mooncore.api.customitem.CustomItemManagerService.class).orElse(null);
    }

    public org.bukkit.Server server() { return Bukkit.getServer(); }
}
