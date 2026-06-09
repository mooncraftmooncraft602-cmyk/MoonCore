package com.mooncore.modules.customitem.editor;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Sous-menu des enchantements vanilla (paginé) : clic = ajouter/retirer, clic droit = niveau +1 (max 5). */
public final class EnchantEditorMenu implements InventoryHolder {

    private static final int MAX_LEVEL = 5;
    private static final int PER_PAGE = 45;

    /** Enchantements proposés : {clé de registre, libellé}. */
    private static final String[][] ENCHANTS = {
            {"sharpness", "Tranchant"}, {"smite", "Châtiment"}, {"bane_of_arthropods", "Fléau des arthropodes"},
            {"knockback", "Recul"}, {"fire_aspect", "Aura de feu"}, {"looting", "Butin"},
            {"sweeping_edge", "Fil tranchant"}, {"efficiency", "Efficacité"}, {"silk_touch", "Toucher de soie"},
            {"fortune", "Fortune"}, {"unbreaking", "Solidité"}, {"mending", "Raccommodage"},
            {"protection", "Protection"}, {"fire_protection", "Protection feu"}, {"blast_protection", "Protection explosions"},
            {"projectile_protection", "Protection projectiles"}, {"feather_falling", "Chute amortie"},
            {"respiration", "Apnée"}, {"aqua_affinity", "Affinité aquatique"}, {"thorns", "Épines"},
            {"depth_strider", "Agilité aquatique"}, {"frost_walker", "Pas glaciaire"}, {"soul_speed", "Vitesse des âmes"},
            {"swift_sneak", "Furtivité"}, {"power", "Puissance"}, {"punch", "Frappe"}, {"flame", "Flamme"},
            {"infinity", "Infinité"}, {"luck_of_the_sea", "Chance de la mer"}, {"lure", "Appât"},
            {"loyalty", "Loyauté"}, {"impaling", "Empalement"}, {"riptide", "Propulsion aquatique"},
            {"channeling", "Canalisation"}, {"multishot", "Multitir"}, {"quick_charge", "Charge rapide"},
            {"piercing", "Perforation"}, {"vanishing_curse", "Malédiction de disparition"},
            {"binding_curse", "Malédiction du lien"},
    };

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private final int page;
    private Inventory inv;

    private EnchantEditorMenu(CustomItemManagerModule module, ChatInput chat, String id, int page) {
        this.module = module;
        this.chat = chat;
        this.id = id;
        this.page = Math.max(0, page);
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        open(module, chat, p, id, 0);
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id, int page) {
        EnchantEditorMenu m = new EnchantEditorMenu(module, chat, id, page);
        int pages = Math.max(1, (int) Math.ceil(ENCHANTS.length / (double) PER_PAGE));
        int pg = Math.min(m.page, pages - 1);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Enchantements</gradient> <dark_gray>» <white>"
                + id + " <dark_gray>(" + (pg + 1) + "/" + pages + ")"));
        m.build(pg);
        p.openInventory(m.inv);
    }

    private void build(int pg) {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        int start = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < ENCHANTS.length; i++) {
            String[] ench = ENCHANTS[start + i];
            int level = d.enchants().getOrDefault(ench[0], 0);
            boolean on = level > 0;
            inv.setItem(i, ItemEditorMenu.btn(on ? Material.ENCHANTED_BOOK : Material.BOOK,
                    (on ? "<green>● " : "<gray>○ ") + ench[1] + (on ? " <white>niv " + level : ""),
                    "<dark_gray>" + ench[0],
                    "<dark_gray>clic = " + (on ? "retirer" : "ajouter") + " · clic droit = niveau +1"));
        }
        if (pg > 0) inv.setItem(45, ItemEditorMenu.btn(Material.ARROW, "<yellow>← Page précédente"));
        inv.setItem(49, ItemEditorMenu.btn(Material.OAK_DOOR, "<yellow>← Retour à l'objet"));
        if (start + PER_PAGE < ENCHANTS.length) inv.setItem(53, ItemEditorMenu.btn(Material.ARROW, "<yellow>Page suivante →"));
    }

    public void click(Player p, int rawSlot, boolean right) {
        if (rawSlot == 45 && page > 0) { open(module, chat, p, id, page - 1); return; }
        if (rawSlot == 53) { open(module, chat, p, id, page + 1); return; }
        if (rawSlot == 49) { ItemEditorMenu.open(module, chat, p, id); return; }
        if (rawSlot < 0 || rawSlot >= PER_PAGE) return;
        int index = page * PER_PAGE + rawSlot;
        if (index >= ENCHANTS.length) return;
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        String key = ENCHANTS[index][0];
        int level = d.enchants().getOrDefault(key, 0);
        if (right) d.setEnchant(key, level <= 0 ? 1 : Math.min(MAX_LEVEL, level + 1));
        else d.setEnchant(key, level > 0 ? 0 : 1);
        module.put(d);
        open(module, chat, p, id, page);
    }

    @Override
    public Inventory getInventory() { return inv; }
}
