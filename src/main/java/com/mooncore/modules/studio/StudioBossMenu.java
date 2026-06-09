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

/** Section Studio dédiée aux boss. */
public final class StudioBossMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final int page;
    private final List<String> ids;
    private Inventory inv;

    private StudioBossMenu(MoonCore plugin, ChatInput chat, int page) {
        this.plugin = plugin;
        this.chat = chat;
        this.page = Math.max(0, page);
        BossManagerModule module = boss();
        this.ids = module == null ? List.of() : new ArrayList<>(module.bossIds());
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, int page) {
        StudioBossMenu menu = new StudioBossMenu(plugin, chat, page);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Studio</gradient> <dark_gray>» Boss"));
        menu.build();
        p.openInventory(menu.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        inv.setItem(0, StudioItems.btn(Material.LIME_DYE, "<green>Créer simple"));
        inv.setItem(1, StudioItems.btn(Material.SHIELD, "<gold>Template Tank"));
        inv.setItem(2, StudioItems.btn(Material.BLAZE_ROD, "<light_purple>Template Mage"));
        inv.setItem(3, StudioItems.btn(Material.ZOMBIE_HEAD, "<red>Template Invocateur"));
        inv.setItem(4, StudioItems.btn(Material.FEATHER, "<aqua>Template Assassin"));
        inv.setItem(5, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Créer avec l'IA"));
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
                    "<red>" + def.id(),
                    "<gray>clic: éditer",
                    "<gray>clic droit: spawn test",
                    "<gray>shift: texture",
                    "<dark_gray>" + def.entityType().name() + " · " + (int) def.maxHealth() + " PV"));
        }
        if (page > 0) inv.setItem(45, StudioItems.btn(Material.ARROW, "<yellow>Page précédente"));
        if (start + StudioItems.CONTENT_SLOTS.length < ids.size()) inv.setItem(53, StudioItems.btn(Material.ARROW, "<yellow>Page suivante"));
        inv.setItem(49, StudioItems.btn(Material.BOOK, "<gray>" + ids.size() + " boss"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        BossManagerModule module = boss();
        if (slot == 8) { StudioHubMenu.open(plugin, chat, p); return; }
        if (module == null) return;
        switch (slot) {
            case 0 -> askCreate(p, "simple");
            case 1 -> askCreate(p, "tank");
            case 2 -> askCreate(p, "mage");
            case 3 -> askCreate(p, "summoner");
            case 4 -> askCreate(p, "assassin");
            case 5 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Décris le boss à créer :", in -> p.performCommand("moon ai createboss " + in));
            }
            case 45 -> { if (page > 0) open(plugin, chat, p, page - 1); }
            case 53 -> open(plugin, chat, p, page + 1);
            default -> handleBossClick(p, slot, rightClick, shiftClick, module);
        }
    }

    private void askCreate(Player p, String template) {
        p.closeInventory();
        chat.request(p, "<yellow>Id du boss " + template + " :", in -> {
            BossManagerModule module = boss();
            if (module == null) return;
            String id = StudioItems.slug(in);
            if (module.exists(id)) { p.sendMessage(Text.mm("<red>Ce boss existe déjà.")); return; }
            if (module.createBoss(id, template(template, id))) {
                p.sendMessage(Text.mm("<green>Boss créé : <white>" + id));
                BossEditorMenu.open(plugin, chat, p, id);
            }
        });
    }

    private void handleBossClick(Player p, int slot, boolean rightClick, boolean shiftClick, BossManagerModule module) {
        int idx = indexFor(slot);
        if (idx < 0) return;
        int bossIndex = page * StudioItems.CONTENT_SLOTS.length + idx;
        if (bossIndex >= ids.size()) return;
        String id = ids.get(bossIndex);
        if (shiftClick) BossEditorMenu.paint(plugin, chat, p, id);
        else if (rightClick) module.spawn(id, p.getLocation());
        else BossEditorMenu.open(plugin, chat, p, id);
    }

    static Map<String, Object> template(String template, String id) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("display-name", "<gradient:#ff4040:#8a2be2>" + id + "</gradient>");
        m.put("bar-color", "PURPLE");
        m.put("progression-xp", 1000);
        switch (template) {
            case "tank" -> {
                m.put("entity", "RAVAGER"); m.put("max-health", 900); m.put("damage", 14); m.put("speed", 0.22); m.put("armor", 20);
                m.put("phases", phases("tank"));
            }
            case "mage" -> {
                m.put("entity", "BLAZE"); m.put("max-health", 420); m.put("damage", 10); m.put("speed", 0.32); m.put("armor", 6);
                m.put("phases", phases("mage"));
            }
            case "summoner" -> {
                m.put("entity", "WITHER_SKELETON"); m.put("max-health", 650); m.put("damage", 11); m.put("speed", 0.28); m.put("armor", 12);
                m.put("phases", phases("summoner"));
            }
            case "assassin" -> {
                m.put("entity", "PIGLIN_BRUTE"); m.put("max-health", 380); m.put("damage", 16); m.put("speed", 0.42); m.put("armor", 8);
                m.put("phases", phases("assassin"));
            }
            default -> {
                m.put("entity", "ZOMBIE"); m.put("max-health", 250); m.put("damage", 8); m.put("speed", 0.25); m.put("armor", 4);
                m.put("phases", phases("simple"));
            }
        }
        return m;
    }

    static Map<String, Object> phases(String preset) {
        Map<String, Object> phases = new LinkedHashMap<>();
        phases.put("p1", phase(100, List.of(ability("AOE_DAMAGE", 120, 5, 1, 5))));
        switch (preset) {
            case "tank" -> {
                phases.put("p2", phase(50, List.of(ability("SHIELD", 260, 100, 1, 8), ability("DASH", 100, 1.2, 1, 14))));
                phases.put("p3", phase(25, List.of(ability("HEAL", 180, 35, 1, 8), ability("EXPLODE", 160, 3, 1, 7))));
            }
            case "mage" -> {
                phases.put("p2", phase(60, List.of(ability("TELEPORT", 100, 1, 1, 18), ability("IGNITE", 130, 80, 1, 7))));
                phases.put("p3", phase(25, List.of(ability("EXPLODE", 140, 4, 1, 8), ability("POISON", 120, 80, 1, 7))));
            }
            case "summoner" -> {
                phases.put("p2", phase(60, List.of(ability("SUMMON", 160, 1, 4, 8), ability("AOE_DAMAGE", 130, 6, 1, 6))));
                phases.put("p3", phase(25, List.of(ability("SUMMON", 110, 1, 6, 8), ability("HEAL", 220, 25, 1, 8))));
            }
            case "assassin" -> {
                phases.put("p2", phase(60, List.of(ability("DASH", 70, 1.8, 1, 18), ability("TELEPORT", 100, 1, 1, 18))));
                phases.put("p3", phase(25, List.of(ability("DASH", 45, 2.2, 1, 20), ability("POISON", 120, 80, 1, 6))));
            }
            default -> phases.put("p2", phase(50, List.of(ability("SUMMON", 180, 1, 2, 6))));
        }
        return phases;
    }

    private static Map<String, Object> phase(double from, List<Map<String, Object>> abilities) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("from-percent", from);
        p.put("abilities", abilities);
        return p;
    }

    private static Map<String, Object> ability(String type, long cd, double magnitude, int count, double radius) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("type", type); a.put("cooldown-ticks", cd); a.put("magnitude", magnitude); a.put("count", count); a.put("radius", radius);
        return a;
    }

    static Map<String, Object> phaseForStudio(double from, List<Map<String, Object>> abilities) {
        return phase(from, abilities);
    }

    static Map<String, Object> abilityForStudio(String type, long cd, double magnitude, int count, double radius) {
        return ability(type, cd, magnitude, count, radius);
    }

    private static Material icon(BossDefinition def) {
        return switch (def.entityType()) {
            case BLAZE -> Material.BLAZE_ROD;
            case RAVAGER -> Material.SADDLE;
            case WITHER, WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case PIGLIN_BRUTE, PIGLIN -> Material.PIGLIN_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            default -> Material.ZOMBIE_HEAD;
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
