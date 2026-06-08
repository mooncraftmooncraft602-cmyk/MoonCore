package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Choix des DROPS d'un boss : liste les objets custom ; clic = (dé)activer le drop pour ce
 * boss, clic droit = cycler la probabilité. Le drop est stocké sur l'objet sous la source
 * {@code boss:<id>} (consommé par CustomItemListener.onDeath).
 */
public final class BossDropMenu implements StudioMenu {

    private static final double[] CHANCES = {0.05, 0.10, 0.25, 0.50, 1.0};

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String bossId;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private BossDropMenu(MoonCore plugin, ChatInput chat, String bossId, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.bossId = bossId;
        this.page = Math.max(0, page);
        CustomItemManagerModule m = items();
        this.ids = m == null ? List.of() : new ArrayList<>(m.rawDefs().keySet());
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String bossId, int page) {
        BossDropMenu menu = new BossDropMenu(plugin, chat, bossId, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Drops</gradient> <dark_gray>» <white>" + bossId));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        inv.setItem(4, StudioItems.btn(Material.WITHER_SKELETON_SKULL, "<red>Drops de " + bossId,
                "<gray>clic = (dé)activer le drop", "<gray>clic droit = changer la chance"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
        CustomItemManagerModule module = items();
        if (module == null) { inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>custom-item inactif")); return; }

        int per = StudioItems.CONTENT_SLOTS.length;
        int start = page * per;
        for (int i = 0; i < per && start + i < ids.size(); i++) {
            CustomItemDef def = module.rawDef(ids.get(start + i));
            if (def == null) continue;
            double ch = chanceOf(def);
            String state = ch > 0 ? "<green>DROP " + Math.round(ch * 100) + "%" : "<dark_gray>non";
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.label(module.buildItem(def, 1).getType(),
                    "<aqua>" + def.id(), "<gray>statut: " + state,
                    "<dark_gray>clic = on/off · clic droit = chance"));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page précédente"));
        if (start + per < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 8) { BossEditorMenu.open(plugin, chat, p, bossId); return; }
        if (slot == 45) { if (page > 0) open(plugin, chat, p, bossId, page - 1); return; }
        if (slot == 53) { open(plugin, chat, p, bossId, page + 1); return; }
        CustomItemManagerModule module = items();
        if (module == null) return;
        int idx = indexFor(slot);
        if (idx < 0) return;
        int itemIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (itemIndex >= ids.size()) return;
        CustomItemDef def = module.rawDef(ids.get(itemIndex));
        if (def == null) return;
        double cur = chanceOf(def);
        if (rightClick) {
            if (cur <= 0) setChance(module, def, CHANCES[0]); // active à la 1re chance
            else setChance(module, def, nextChance(cur));
        } else {
            setChance(module, def, cur > 0 ? 0 : 0.25); // toggle (défaut 25%)
        }
        build();
    }

    private double chanceOf(CustomItemDef def) {
        for (CustomItemDef.DropRule d : def.drops())
            if (d.source().equalsIgnoreCase("boss:" + bossId)) return d.chance();
        return -1;
    }

    private void setChance(CustomItemManagerModule module, CustomItemDef def, double chance) {
        def.drops().removeIf(d -> d.source().equalsIgnoreCase("boss:" + bossId));
        if (chance > 0) def.drops().add(new CustomItemDef.DropRule("boss:" + bossId, chance, 1, 1));
        module.put(def);
    }

    private static double nextChance(double cur) {
        for (double c : CHANCES) if (c > cur + 1e-6) return c;
        return CHANCES[0];
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    private CustomItemManagerModule items() { return plugin.moduleManager().get(CustomItemManagerModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
