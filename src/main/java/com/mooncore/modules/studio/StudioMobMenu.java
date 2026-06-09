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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Createur de creatures custom legeres, base sur le moteur boss existant. */
public final class StudioMobMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private StudioMobMenu(MoonCore plugin, ChatInput chat, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.page = Math.max(0, page);
        BossManagerModule module = boss();
        this.ids = module == null ? List.of() : new ArrayList<>(module.bossIds()).stream()
                .filter(id -> id.startsWith("mob_"))
                .toList();
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, int page) {
        StudioMobMenu menu = new StudioMobMenu(plugin, chat, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>> Mobs"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        inv.setItem(0, StudioItems.btn(Material.ZOMBIE_HEAD, "<green>Mob simple"));
        inv.setItem(1, StudioItems.btn(Material.SKELETON_SKULL, "<yellow>Archer"));
        inv.setItem(2, StudioItems.btn(Material.SPIDER_EYE, "<red>Rapide"));
        inv.setItem(3, StudioItems.btn(Material.ROTTEN_FLESH, "<gold>Elite"));
        inv.setItem(4, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Mob avec IA"));
        inv.setItem(8, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));

        BossManagerModule module = boss();
        if (module == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Module boss inactif"));
            return;
        }
        int start = page * StudioItems.CONTENT_SLOTS.length;
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length && start + i < ids.size(); i++) {
            BossDefinition def = module.definition(ids.get(start + i));
            if (def == null) continue;
            inv.setItem(StudioItems.CONTENT_SLOTS[i], StudioItems.btn(icon(def),
                    "<green>" + def.id(),
                    "<gray>clic: editer",
                    "<gray>clic droit: spawn test",
                    "<gray>shift: texture",
                    "<dark_gray>" + def.entityType().name() + " - " + (int) def.maxHealth() + " PV"));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page precedente"));
        if (start + StudioItems.CONTENT_SLOTS.length < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
        inv.setItem(49, StudioItems.btn(Material.BOOK, "<gray>" + ids.size() + " mob(s)"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        BossManagerModule module = boss();
        if (slot == 8) { StudioHubMenu.open(plugin, chat, p); return; }
        if (module == null) return;
        switch (slot) {
            case 0 -> askCreate(p, "simple");
            case 1 -> askCreate(p, "archer");
            case 2 -> askCreate(p, "fast");
            case 3 -> askCreate(p, "elite");
            case 4 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Decris le mob a creer :", in ->
                        p.performCommand("moon ai create mob " + in + " texture"));
            }
            case 45 -> { if (page > 0) open(plugin, chat, p, page - 1); }
            case 53 -> open(plugin, chat, p, page + 1);
            default -> handleMobClick(p, slot, rightClick, shiftClick, module);
        }
    }

    private void askCreate(Player p, String template) {
        p.closeInventory();
        chat.request(p, "<yellow>Id du mob " + template + " :", in -> {
            BossManagerModule module = boss();
            if (module == null) return;
            String id = StudioItems.slug(in);
            if (!id.startsWith("mob_")) id = "mob_" + id;
            if (module.exists(id)) {
                p.sendMessage(Text.mm("<red>Ce mob existe deja."));
                return;
            }
            if (module.createBoss(id, mobTemplate(template, id))) {
                p.sendMessage(Text.mm("<green>Mob cree : <white>" + id));
                BossEditorMenu.open(plugin, chat, p, id);
            }
        });
    }

    private void handleMobClick(Player p, int slot, boolean rightClick, boolean shiftClick, BossManagerModule module) {
        int idx = indexFor(slot);
        if (idx < 0) return;
        int mobIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (mobIndex >= ids.size()) return;
        String id = ids.get(mobIndex);
        if (shiftClick) BossEditorMenu.paint(plugin, chat, p, id);
        else if (rightClick) module.spawn(id, p.getLocation());
        else BossEditorMenu.open(plugin, chat, p, id);
    }

    private static Map<String, Object> mobTemplate(String template, String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("display-name", "<green>" + id + "</green>");
        m.put("bar-color", "GREEN");
        m.put("progression-xp", 120);
        switch (template) {
            case "archer" -> {
                m.put("entity", "SKELETON"); m.put("max-health", 55); m.put("damage", 5); m.put("speed", 0.27); m.put("armor", 2);
                m.put("phases", simplePhases("archer"));
            }
            case "fast" -> {
                m.put("entity", "SPIDER"); m.put("max-health", 45); m.put("damage", 6); m.put("speed", 0.38); m.put("armor", 1);
                m.put("phases", simplePhases("fast"));
            }
            case "elite" -> {
                m.put("entity", "HUSK"); m.put("max-health", 95); m.put("damage", 8); m.put("speed", 0.28); m.put("armor", 6);
                m.put("phases", simplePhases("elite"));
            }
            default -> {
                m.put("entity", "ZOMBIE"); m.put("max-health", 50); m.put("damage", 5); m.put("speed", 0.25); m.put("armor", 1);
                m.put("phases", simplePhases("simple"));
            }
        }
        return m;
    }

    private static Map<String, Object> simplePhases(String preset) {
        Map<String, Object> phases = new LinkedHashMap<>();
        phases.put("p1", StudioBossMenu.phaseForStudio(100, List.of(StudioBossMenu.abilityForStudio("AOE_DAMAGE", 180, 3, 1, 4))));
        switch (preset) {
            case "fast" -> phases.put("p2", StudioBossMenu.phaseForStudio(45, List.of(StudioBossMenu.abilityForStudio("DASH", 100, 1.5, 1, 12))));
            case "elite" -> phases.put("p2", StudioBossMenu.phaseForStudio(55, List.of(
                    StudioBossMenu.abilityForStudio("SHIELD", 180, 80, 1, 6),
                    StudioBossMenu.abilityForStudio("HEAL", 220, 12, 1, 6))));
            case "archer" -> phases.put("p2", StudioBossMenu.phaseForStudio(50, List.of(StudioBossMenu.abilityForStudio("POISON", 160, 60, 1, 7))));
            default -> phases.put("p2", StudioBossMenu.phaseForStudio(50, List.of(StudioBossMenu.abilityForStudio("DASH", 160, 1.0, 1, 8))));
        }
        return phases;
    }

    private static Material icon(BossDefinition def) {
        return switch (def.entityType()) {
            case SKELETON -> Material.SKELETON_SKULL;
            case SPIDER, CAVE_SPIDER -> Material.SPIDER_EYE;
            case HUSK, ZOMBIE -> Material.ZOMBIE_HEAD;
            default -> Material.NAME_TAG;
        };
    }

    private static int indexFor(int slot) {
        for (int i = 0; i < StudioItems.CONTENT_SLOTS.length; i++) if (StudioItems.CONTENT_SLOTS[i] == slot) return i;
        return -1;
    }

    private BossManagerModule boss() { return plugin.moduleManager().get(BossManagerModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
