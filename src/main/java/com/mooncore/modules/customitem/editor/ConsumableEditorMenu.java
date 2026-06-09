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

/**
 * Sous-menu des effets de CONSOMMATION (potions/nourriture custom) : clic = ajouter/retirer,
 * clic droit = amplificateur +1 (max 4), shift = durée suivante (5/10/30/60 s).
 */
public final class ConsumableEditorMenu implements InventoryHolder {

    private static final int PER_PAGE = 45;
    private static final int[] DURATIONS = {100, 200, 600, 1200}; // ticks : 5s, 10s, 30s, 60s

    /** Effets proposés : {clé de registre, libellé}. */
    private static final String[][] EFFECTS = {
            {"speed", "Vitesse"}, {"slowness", "Lenteur"}, {"haste", "Célérité"}, {"mining_fatigue", "Fatigue"},
            {"strength", "Force"}, {"weakness", "Faiblesse"}, {"instant_health", "Soin instantané"},
            {"instant_damage", "Dégâts instantanés"}, {"jump_boost", "Saut"}, {"nausea", "Nausée"},
            {"regeneration", "Régénération"}, {"resistance", "Résistance"}, {"fire_resistance", "Résistance au feu"},
            {"water_breathing", "Respiration aquatique"}, {"invisibility", "Invisibilité"}, {"blindness", "Cécité"},
            {"night_vision", "Vision nocturne"}, {"hunger", "Faim"}, {"poison", "Poison"}, {"wither", "Wither"},
            {"health_boost", "Bonus de vie"}, {"absorption", "Absorption"}, {"saturation", "Saturation"},
            {"glowing", "Lueur"}, {"levitation", "Lévitation"}, {"slow_falling", "Chute lente"},
            {"conduit_power", "Pouvoir du conduit"}, {"dolphins_grace", "Grâce du dauphin"},
            {"darkness", "Ténèbres"}, {"hero_of_the_village", "Héros du village"},
    };

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private final int page;
    private Inventory inv;

    private ConsumableEditorMenu(CustomItemManagerModule module, ChatInput chat, String id, int page) {
        this.module = module;
        this.chat = chat;
        this.id = id;
        this.page = Math.max(0, page);
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        open(module, chat, p, id, 0);
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id, int page) {
        ConsumableEditorMenu m = new ConsumableEditorMenu(module, chat, id, page);
        int pages = Math.max(1, (int) Math.ceil(EFFECTS.length / (double) PER_PAGE));
        int pg = Math.min(m.page, pages - 1);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Effets (consommable)</gradient> <dark_gray>» <white>" + id));
        m.build(pg);
        p.openInventory(m.inv);
    }

    private void build(int pg) {
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        int start = pg * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < EFFECTS.length; i++) {
            String[] ef = EFFECTS[start + i];
            CustomItemDef.ConsumeEffect cur = find(d, ef[0]);
            String state = cur != null ? " <white>niv " + (cur.amplifier() + 1) + " · " + (cur.duration() / 20) + "s" : "";
            inv.setItem(i, ItemEditorMenu.btn(cur != null ? Material.POTION : Material.GLASS_BOTTLE,
                    (cur != null ? "<green>● " : "<gray>○ ") + ef[1] + state,
                    "<dark_gray>" + ef[0],
                    "<dark_gray>clic = on/off · clic droit = niveau+ · shift = durée"));
        }
        inv.setItem(49, ItemEditorMenu.btn(Material.OAK_DOOR, "<yellow>← Retour à l'objet"));
        if (pg > 0) inv.setItem(45, ItemEditorMenu.btn(Material.ARROW, "<yellow>← Page précédente"));
        if (start + PER_PAGE < EFFECTS.length) inv.setItem(53, ItemEditorMenu.btn(Material.ARROW, "<yellow>Page suivante →"));
    }

    public void click(Player p, int rawSlot, boolean right, boolean shift) {
        if (rawSlot == 45 && page > 0) { open(module, chat, p, id, page - 1); return; }
        if (rawSlot == 53) { open(module, chat, p, id, page + 1); return; }
        if (rawSlot == 49) { ItemEditorMenu.open(module, chat, p, id); return; }
        if (rawSlot < 0 || rawSlot >= PER_PAGE) return;
        int index = page * PER_PAGE + rawSlot;
        if (index >= EFFECTS.length) return;
        CustomItemDef d = module.rawDef(id);
        if (d == null) return;
        String key = EFFECTS[index][0];
        CustomItemDef.ConsumeEffect cur = find(d, key);
        if (cur == null) {
            d.setConsumeEffect(key, DURATIONS[1], 0); // ajout par défaut : 10s niveau 1
        } else if (shift) {
            d.setConsumeEffect(key, nextDuration(cur.duration()), cur.amplifier());
        } else if (right) {
            d.setConsumeEffect(key, cur.duration(), (cur.amplifier() + 1) % 4); // niv 1→4 puis retour
        } else {
            d.setConsumeEffect(key, 0, 0); // retire
        }
        module.put(d);
        open(module, chat, p, id, page);
    }

    private static CustomItemDef.ConsumeEffect find(CustomItemDef d, String key) {
        for (CustomItemDef.ConsumeEffect c : d.consumeEffects()) if (c.key().equals(key)) return c;
        return null;
    }

    private static int nextDuration(int cur) {
        for (int dval : DURATIONS) if (dval > cur) return dval;
        return DURATIONS[0];
    }

    @Override
    public Inventory getInventory() { return inv; }
}
