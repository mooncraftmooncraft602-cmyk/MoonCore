package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.boss.BossDefinition;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/** Équipement du boss : un objet custom ou vanilla par emplacement (casque, plastron, mains…). */
public final class BossEquipmentMenu implements StudioMenu {

    private static final String[][] SLOTS = {
            {"helmet", "Casque"}, {"chestplate", "Plastron"}, {"leggings", "Jambières"},
            {"boots", "Bottes"}, {"mainhand", "Main principale"}, {"offhand", "Main secondaire"},
    };
    private static final int[] CELLS = {10, 11, 12, 13, 15, 16};

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private BossEquipmentMenu(MoonCore plugin, ChatInput chat, String id) {
        this.plugin = plugin;
        this.chat = chat;
        this.id = id;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String id) {
        BossEquipmentMenu m = new BossEquipmentMenu(plugin, chat, id);
        m.inv = Bukkit.createInventory(m, 27, Text.mm("<gradient:#8a2be2:#c77dff>Équipement</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        BossManagerModule boss = boss();
        BossDefinition def = boss == null ? null : boss.definition(id);
        if (def == null) { inv.setItem(13, StudioItems.btn(Material.BARRIER, "<red>Boss introuvable")); return; }
        for (int i = 0; i < SLOTS.length; i++) {
            String cur = def.equipment().get(SLOTS[i][0]);
            inv.setItem(CELLS[i], StudioItems.btn(icon(SLOTS[i][0]), "<yellow>" + SLOTS[i][1] + " : <white>" + (cur == null ? "vide" : cur),
                    "<gray>clic = définir (custom:<id> ou Material)",
                    "<dark_gray>clic droit = retirer"));
        }
        inv.setItem(26, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 26) { BossEditorMenu.open(plugin, chat, p, id); return; }
        BossManagerModule boss = boss();
        if (boss == null) return;
        int idx = cell(slot);
        if (idx < 0) return;
        String key = "equipment." + SLOTS[idx][0];
        if (rightClick) {
            boss.setField(id, key, null);
            open(plugin, chat, p, id);
            return;
        }
        p.closeInventory();
        chat.request(p, "<yellow>" + SLOTS[idx][1] + " — objet (<white>custom:" + "<id></white> ou Material ex <white>DIAMOND_SWORD</white>, ou <white>aucun</white>) :", in -> {
            String v = in.trim();
            boss.setField(id, key, (v.equalsIgnoreCase("aucun") || v.equalsIgnoreCase("none") || v.isEmpty()) ? null : v);
            open(plugin, chat, p, id);
        });
    }

    private static int cell(int slot) {
        for (int i = 0; i < CELLS.length; i++) if (CELLS[i] == slot) return i;
        return -1;
    }

    private static Material icon(String slot) {
        return switch (slot) {
            case "helmet" -> Material.DIAMOND_HELMET;
            case "chestplate" -> Material.DIAMOND_CHESTPLATE;
            case "leggings" -> Material.DIAMOND_LEGGINGS;
            case "boots" -> Material.DIAMOND_BOOTS;
            case "mainhand" -> Material.DIAMOND_SWORD;
            case "offhand" -> Material.SHIELD;
            default -> Material.ARMOR_STAND;
        };
    }

    private BossManagerModule boss() { return plugin.moduleManager().get(BossManagerModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
