package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.CustomItemDefStore;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Section Studio dédiée aux blocs et minerais. */
public final class StudioBlockMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private StudioBlockMenu(MoonCore plugin, ChatInput chat, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.page = Math.max(0, page);
        CustomBlockManagerModule module = blocks();
        this.ids = module == null ? List.of() : new ArrayList<>(module.rawDefs().keySet());
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, int page) {
        StudioBlockMenu menu = new StudioBlockMenu(plugin, chat, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>» Blocs & minerais"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        inv.setItem(0, StudioItems.btn(Material.LIME_DYE, "<green>Créer un bloc"));
        inv.setItem(1, StudioItems.btn(Material.DEEPSLATE_DIAMOND_ORE, "<aqua>Créer un minerai",
                "<gray>worldgen activé, profondeur de base"));
        inv.setItem(2, StudioItems.btn(Material.GRASS_BLOCK, "<aqua>Importer vanilla"));
        inv.setItem(3, StudioItems.btn(Material.MAP, "<green>Rebuild pack"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
        CustomBlockManagerModule module = blocks();
        if (module == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Module custom-block inactif"));
            return;
        }
        int start = page * StudioItems.CONTENT_SLOTS.length;
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length && start + i < ids.size(); i++) {
            CustomBlockDef def = module.rawDef(ids.get(start + i));
            if (def == null) continue;
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.btn(def.generate() ? Material.DEEPSLATE_DIAMOND_ORE : Material.NOTE_BLOCK,
                    "<aqua>" + def.id(),
                    "<gray>clic: éditer",
                    "<gray>clic droit: recevoir",
                    "<gray>shift: peindre texture",
                    "<dark_gray>" + toolLine(def),
                    "<dark_gray>durabilite " + def.breakDurability() + (def.generate() ? " - minerai" : "")));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page précédente"));
        if (start + StudioItems.CONTENT_SLOTS.length < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
        inv.setItem(49, StudioItems.btn(Material.BOOK, "<gray>" + ids.size() + " bloc(s)"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        CustomBlockManagerModule module = blocks();
        if (slot == 8) { StudioHubMenu.open(plugin, chat, p); return; }
        if (module == null) return;
        switch (slot) {
            case 0 -> askCreate(p, false);
            case 1 -> askCreate(p, true);
            case 2 -> { p.closeInventory(); p.performCommand("moon block importvanilla"); }
            case 3 -> StudioItems.rebuildAndResend(plugin, p);
            case 45 -> { if (page > 0) open(plugin, chat, p, page - 1); }
            case 53 -> open(plugin, chat, p, page + 1);
            default -> handleBlockClick(p, slot, rightClick, shiftClick, module);
        }
    }

    private void askCreate(Player p, boolean ore) {
        p.closeInventory();
        chat.request(p, "<yellow>Id du " + (ore ? "minerai" : "bloc") + " :", in -> {
            CustomBlockManagerModule module = blocks();
            if (module == null) return;
            String id = StudioItems.slug(in);
            if (!CustomItemDefStore.isValidId(id)) { p.sendMessage(Text.mm("<red>Id invalide.")); return; }
            if (module.rawDef(id) != null) { p.sendMessage(Text.mm("<red>Ce bloc existe déjà.")); return; }
            CustomBlockDef def = new CustomBlockDef(id);
            if (ore) {
                def.setGenerate(true);
                def.setReplace(Material.DEEPSLATE);
                def.setYRange(-64, 16);
                def.setVeinsPerChunk(2);
                def.setVeinSize(4);
            }
            module.put(def);
            BlockEditorMenu.open(plugin, chat, p, id);
        });
    }

    private void handleBlockClick(Player p, int slot, boolean rightClick, boolean shiftClick, CustomBlockManagerModule module) {
        int idx = indexFor(slot);
        if (idx < 0) return;
        int blockIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (blockIndex >= ids.size()) return;
        String id = ids.get(blockIndex);
        if (shiftClick) {
            var ci = plugin.moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
            if (ci != null) ci.paintManager().open(p,
                    new com.mooncore.modules.customitem.paint.BlockPaintTarget(module, id), 16, null);
        } else if (rightClick) {
            module.give(p, id, 1);
            p.sendMessage(Text.mm("<green>Bloc reçu : <white>" + id));
        } else {
            BlockEditorMenu.open(plugin, chat, p, id);
        }
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    private static String toolLine(CustomBlockDef def) {
        if (def.requiredTool() == com.mooncore.modules.customitem.ToolKind.NONE) return "outil: aucun";
        return "outil: " + def.requiredTool().label() + " " + def.minToolTier().label() + "+";
    }

    private CustomBlockManagerModule blocks() {
        return plugin.moduleManager().get(CustomBlockManagerModule.class);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
