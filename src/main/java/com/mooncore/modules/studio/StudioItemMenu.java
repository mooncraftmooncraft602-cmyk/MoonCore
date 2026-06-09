package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemDefStore;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.CustomItemMenu;
import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import com.mooncore.modules.customitem.editor.ItemEditorMenu;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Section Studio dédiée aux objets custom. */
public final class StudioItemMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private StudioItemMenu(MoonCore plugin, ChatInput chat, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.page = Math.max(0, page);
        CustomItemManagerModule module = items();
        this.ids = module == null ? List.of() : new ArrayList<>(module.rawDefs().keySet());
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, int page) {
        StudioItemMenu menu = new StudioItemMenu(plugin, chat, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>» Items"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        CustomItemManagerModule module = items();
        inv.setItem(0, StudioItems.btn(Material.LIME_DYE, "<green>Créer un item", "<gray>demande un id puis ouvre l'éditeur"));
        inv.setItem(1, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Créer avec l'IA", "<gray>description libre + texture"));
        inv.setItem(2, StudioItems.btn(Material.GRASS_BLOCK, "<aqua>Importer vanilla", "<gray>textures item/block depuis jar client"));
        inv.setItem(3, StudioItems.btn(Material.MAP, "<green>Rebuild pack"));
        inv.setItem(4, StudioItems.btn(Material.CHEST, "<yellow>Menu créatif", "<gray>recevoir les items existants"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
        if (module == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Module custom-item inactif"));
            return;
        }

        int start = page * StudioItems.CONTENT_SLOTS.length;
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length && start + i < ids.size(); i++) {
            CustomItemDef def = module.rawDef(ids.get(start + i));
            if (def == null) continue;
            org.bukkit.inventory.ItemStack item = module.buildItem(def, 1);
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.label(item.getType(),
                    "<aqua>" + def.id(),
                    "<gray>clic: éditer",
                    "<gray>clic droit: recevoir",
                    "<gray>shift: recette",
                    "<dark_gray>texture: " + (def.modelKey() == null ? "non" : def.modelKey())));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page précédente"));
        if (start + StudioItems.CONTENT_SLOTS.length < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
        inv.setItem(49, StudioItems.btn(Material.BOOK, "<gray>" + ids.size() + " item(s)"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        CustomItemManagerModule module = items();
        if (slot == 8) { StudioHubMenu.open(plugin, chat, p); return; }
        if (module == null) return;
        switch (slot) {
            case 0 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Id de l'item (ex <white>lame_lunaire</white>) :", in -> {
                    String id = StudioItems.slug(in);
                    if (!CustomItemDefStore.isValidId(id)) { p.sendMessage(Text.mm("<red>Id invalide.")); return; }
                    if (module.rawDef(id) != null) { p.sendMessage(Text.mm("<red>Cet item existe déjà.")); return; }
                    CustomItemDef def = new CustomItemDef(id);
                    ToolKind hint = ToolKind.fromText(id);
                    if (hint != ToolKind.NONE) def.setTool(hint, ToolTier.IRON);
                    module.put(def);
                    ItemEditorMenu.open(module, module.chatInput(), p, id);
                });
            }
            case 1 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Décris l'item à créer :", in -> p.performCommand("moon ai createitem " + in + " texture"));
            }
            case 2 -> { p.closeInventory(); p.performCommand("moon item importvanilla"); }
            case 3 -> StudioItems.rebuildAndResend(plugin, p);
            case 4 -> CustomItemMenu.open(module, p, 0);
            case 45 -> { if (page > 0) open(plugin, chat, p, page - 1); }
            case 53 -> open(plugin, chat, p, page + 1);
            default -> handleItemClick(p, slot, rightClick, shiftClick, module);
        }
    }

    private void handleItemClick(Player p, int slot, boolean rightClick, boolean shiftClick, CustomItemManagerModule module) {
        int idx = indexFor(slot);
        if (idx < 0) return;
        int itemIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (itemIndex >= ids.size()) return;
        String id = ids.get(itemIndex);
        if (shiftClick) { RecipeEditorMenu.open(plugin, chat, p, id); return; }
        if (rightClick) { module.give(p, id, 1); p.sendMessage(Text.mm("<green>Item reçu : <white>" + id)); return; }
        ItemEditorMenu.open(module, module.chatInput(), p, id);
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    private CustomItemManagerModule items() {
        return plugin.moduleManager().get(CustomItemManagerModule.class);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
