package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.CustomItemDefStore;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.paint.BlockFacePaintTarget;
import com.mooncore.modules.customitem.paint.BlockPaintTarget;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Éditeur Studio d'un bloc/minerai custom. */
public final class BlockEditorMenu implements StudioMenu {

    private static final List<Material> REPLACE = List.of(
            Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.NETHERRACK, Material.END_STONE, Material.CALCITE
    );

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private BlockEditorMenu(MoonCore plugin, ChatInput chat, String id) {
        this.plugin = plugin;
        this.chat = chat;
        this.id = id;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String id) {
        BlockEditorMenu menu = new BlockEditorMenu(plugin, chat, id);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Bloc</gradient> <dark_gray>» <white>" + id));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        CustomBlockDef def = def();
        if (def == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Bloc introuvable"));
            return;
        }
        inv.setItem(4, StudioItems.btn(def.generate() ? Material.DEEPSLATE_DIAMOND_ORE : Material.NOTE_BLOCK,
                "<aqua>" + def.id(),
                "<gray>nom: <reset>" + def.displayName(),
                "<gray>état: <white>" + def.stateIndex(),
                "<gray>modèle: <white>" + def.modelKey()));

        inv.setItem(10, StudioItems.btn(Material.NAME_TAG, "<yellow>Nom", "<gray>clic = changer"));
        inv.setItem(11, StudioItems.btn(Material.BRUSH, "<light_purple>Texture toutes faces", "<gray>ouvre la toile"));
        inv.setItem(12, StudioItems.btn(Material.CHEST, "<green>Recevoir"));
        inv.setItem(13, StudioItems.btn(Material.GRASS_BLOCK, "<green>Placer test", "<gray>pose devant toi"));
        inv.setItem(14, StudioItems.btn(Material.MAP, "<green>Rebuild pack"));
        inv.setItem(16, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));

        inv.setItem(19, StudioItems.btn(def.requiresPickaxe() ? Material.IRON_PICKAXE : Material.WOODEN_PICKAXE,
                "<yellow>Pioche requise : " + onOff(def.requiresPickaxe())));
        inv.setItem(20, StudioItems.btn(Material.EXPERIENCE_BOTTLE, "<yellow>XP drop : <white>" + def.dropXp(),
                "<gray>clic = +1 · clic droit = -1",
                "<gray>shift = +10 / -10"));
        inv.setItem(21, StudioItems.btn(Material.HOPPER, "<yellow>Drop : <white>" + (def.dropItemId() == null ? "lui-même" : def.dropItemId()),
                "<gray>clic = choisir item",
                "<gray>clic droit = lui-même"));
        inv.setItem(22, StudioItems.btn(def.generate() ? Material.LIME_DYE : Material.GRAY_DYE,
                "<aqua>Worldgen : " + onOff(def.generate())));
        inv.setItem(23, StudioItems.btn(def.replace(), "<aqua>Remplace : <white>" + def.replace().name(), "<gray>clic = suivant"));
        inv.setItem(24, StudioItems.btn(Material.DEEPSLATE, "<aqua>Y : <white>" + def.minY() + " → " + def.maxY(),
                "<gray>clic = cycle preset",
                "<gray>clic droit = saisie exacte"));
        inv.setItem(25, StudioItems.btn(Material.AMETHYST_CLUSTER, "<aqua>Veines/chunk : <white>" + def.veinsPerChunk(),
                "<gray>clic = +1 · clic droit = -1"));
        inv.setItem(26, StudioItems.btn(Material.RAW_IRON, "<aqua>Taille veine : <white>" + def.veinSize(),
                "<gray>clic = +1 · clic droit = -1"));

        inv.setItem(29, StudioItems.btn(Material.LIGHT_BLUE_DYE, "<light_purple>Texture haut"));
        inv.setItem(30, StudioItems.btn(Material.CYAN_DYE, "<light_purple>Texture côtés"));
        inv.setItem(31, StudioItems.btn(Material.BLUE_DYE, "<light_purple>Texture bas"));
        inv.setItem(32, StudioItems.btn(Material.TNT, "<red>Réinitialiser faces", "<gray>revient à texture unique"));
        inv.setItem(33, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Retexture IA", "<gray>demande une texture par description"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        CustomBlockManagerModule module = blocks();
        CustomBlockDef def = def();
        if (module == null || def == null) return;
        switch (slot) {
            case 10 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Nouveau nom MiniMessage :", in -> { def.setDisplayName(in); module.put(def); open(plugin, chat, p, id); });
            }
            case 11 -> paintAll(p, module);
            case 12 -> module.give(p, id, 1);
            case 13 -> placeTest(p, module, def);
            case 14 -> StudioItems.rebuildAndResend(plugin, p);
            case 16 -> StudioBlockMenu.open(plugin, chat, p, 0);
            case 19 -> { def.setRequiresPickaxe(!def.requiresPickaxe()); module.put(def); build(); }
            case 20 -> { def.setDropXp(Math.max(0, def.dropXp() + (rightClick ? -1 : 1) * (shiftClick ? 10 : 1))); module.put(def); build(); }
            case 21 -> {
                if (rightClick) { def.setDropItemId(null); module.put(def); build(); }
                else askDropItem(p, module, def);
            }
            case 22 -> { def.setGenerate(!def.generate()); module.put(def); build(); }
            case 23 -> { def.setReplace(nextReplace(def.replace())); module.put(def); build(); }
            case 24 -> { if (rightClick) askY(p, module, def); else cycleY(def, module); }
            case 25 -> { def.setVeinsPerChunk(StudioItems.clamp(def.veinsPerChunk() + (rightClick ? -1 : 1), 1, 12)); module.put(def); build(); }
            case 26 -> { def.setVeinSize(StudioItems.clamp(def.veinSize() + (rightClick ? -1 : 1), 1, 16)); module.put(def); build(); }
            case 29 -> paintFace(p, module, BlockFacePaintTarget.Face.TOP, id + "_top");
            case 30 -> paintFace(p, module, BlockFacePaintTarget.Face.SIDE, id + "_side");
            case 31 -> paintFace(p, module, BlockFacePaintTarget.Face.BOTTOM, id + "_bottom");
            case 32 -> { def.setTextureTop(null); def.setTextureSide(null); def.setTextureBottom(null); module.put(def); StudioItems.rebuildAndResend(plugin, p); build(); }
            case 33 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Description du bloc/minerai IA :", in -> p.performCommand("moon ai createblock " + id + " " + in + " texture"));
            }
            default -> { }
        }
    }

    private void paintAll(Player p, CustomBlockManagerModule module) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (ci == null) return;
        p.closeInventory();
        ci.paintManager().open(p, new BlockPaintTarget(module, id), 16, null, () -> {
            if (p.isOnline()) open(plugin, chat, p, id);
        });
    }

    private void paintFace(Player p, CustomBlockManagerModule module, BlockFacePaintTarget.Face face, String key) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        if (ci == null) return;
        p.closeInventory();
        ci.paintManager().open(p, new BlockFacePaintTarget(module, id, key, face), 16, null, () -> {
            if (p.isOnline()) open(plugin, chat, p, id);
        });
    }

    private void askDropItem(Player p, CustomBlockManagerModule module, CustomBlockDef def) {
        p.closeInventory();
        chat.request(p, "<yellow>Item custom à drop (<white>self</white> = lui-même) :", in -> {
            if (in.equalsIgnoreCase("self")) def.setDropItemId(null);
            else def.setDropItemId(StudioItems.slug(in));
            module.put(def);
            open(plugin, chat, p, id);
        });
    }

    private void askY(Player p, CustomBlockManagerModule module, CustomBlockDef def) {
        p.closeInventory();
        chat.request(p, "<yellow>Range Y min max (ex <white>-64 16</white>) :", in -> {
            String[] parts = in.trim().split("\\s+");
            if (parts.length >= 2) {
                try {
                    int min = Integer.parseInt(parts[0]), max = Integer.parseInt(parts[1]);
                    def.setYRange(Math.min(min, max), Math.max(min, max));
                    module.put(def);
                } catch (NumberFormatException e) {
                    p.sendMessage(Text.mm("<red>Nombres invalides."));
                }
            }
            open(plugin, chat, p, id);
        });
    }

    private void cycleY(CustomBlockDef def, CustomBlockManagerModule module) {
        if (def.minY() == -64 && def.maxY() == 16) def.setYRange(0, 64);
        else if (def.minY() == 0 && def.maxY() == 64) def.setYRange(32, 128);
        else if (def.minY() == 32 && def.maxY() == 128) def.setYRange(-16, 32);
        else def.setYRange(-64, 16);
        module.put(def);
        build();
    }

    private Material nextReplace(Material current) {
        int idx = REPLACE.indexOf(current);
        return REPLACE.get((idx + 1 + REPLACE.size()) % REPLACE.size());
    }

    private void placeTest(Player p, CustomBlockManagerModule module, CustomBlockDef def) {
        Location loc = p.getLocation().add(p.getLocation().getDirection().normalize().multiply(2));
        Block b = loc.getBlock();
        module.placeState(b, def);
        p.sendMessage(Text.mm("<green>Bloc test placé devant toi : <white>" + id));
    }

    private static String onOff(boolean value) { return value ? "<green>ON" : "<red>OFF"; }

    private CustomBlockManagerModule blocks() { return plugin.moduleManager().get(CustomBlockManagerModule.class); }
    private CustomBlockDef def() { CustomBlockManagerModule m = blocks(); return m == null ? null : m.rawDef(id); }

    @Override
    public Inventory getInventory() { return inv; }
}
