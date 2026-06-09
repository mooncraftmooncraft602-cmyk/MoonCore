package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.boss.BossDefinition;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.paint.BossPaintTarget;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/** Éditeur Studio d'un boss existant. */
public final class BossEditorMenu implements StudioMenu {

    private static final List<String> BAR_COLORS = List.of("PURPLE", "RED", "BLUE", "GREEN", "YELLOW", "WHITE", "PINK");

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private BossEditorMenu(MoonCore plugin, ChatInput chat, String id) {
        this.plugin = plugin;
        this.chat = chat;
        this.id = id;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String id) {
        BossEditorMenu menu = new BossEditorMenu(plugin, chat, id);
        menu.inv = Bukkit.createInventory(menu, 54, Text.mm("<gradient:#8a2be2:#c77dff>Boss</gradient> <dark_gray>» <white>" + id));
        menu.build();
        p.openInventory(menu.inv);
    }

    static void paint(MoonCore plugin, ChatInput chat, Player p, String id) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        BossManagerModule boss = plugin.moduleManager().get(BossManagerModule.class);
        if (ci == null || boss == null) return;
        p.closeInventory();
        ci.paintManager().open(p, new BossPaintTarget(boss, id), 16, null, () -> {
            if (p.isOnline()) open(plugin, chat, p, id);
        });
    }

    private void build() {
        StudioItems.fill(inv);
        BossDefinition def = def();
        if (def == null) {
            inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Boss introuvable"));
            return;
        }
        inv.setItem(4, StudioItems.btn(Material.WITHER_SKELETON_SKULL, "<red>" + def.id(),
                "<gray>type: <white>" + def.entityType().name(),
                "<gray>PV: <white>" + (int) def.maxHealth() + " <gray>dégâts: <white>" + def.damage(),
                "<gray>texture: <white>" + (def.textureKey() == null ? "aucune" : def.textureKey())));

        inv.setItem(10, StudioItems.btn(Material.NAME_TAG, "<yellow>Nom", "<gray>clic = changer"));
        inv.setItem(11, StudioItems.btn(Material.SPAWNER, "<green>Spawn test"));
        inv.setItem(12, StudioItems.btn(Material.BRUSH, "<light_purple>Texture boss"));
        inv.setItem(13, StudioItems.btn(Material.MAP, "<green>Rebuild pack"));
        inv.setItem(14, StudioItems.btn(Material.BEACON, "<aqua>Couleur barre : <white>" + def.barColor(), "<gray>clic = suivant"));
        inv.setItem(15, StudioItems.btn(Material.CHEST, "<gold>Drops du boss", "<gray>choisir les objets lâchés + leur chance"));
        inv.setItem(16, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));

        inv.setItem(19, StudioItems.btn(Material.RED_DYE, "<red>PV max : <white>" + (int) def.maxHealth(), "<gray>clic = +50 · clic droit = -50", "<gray>shift = x5"));
        inv.setItem(20, StudioItems.btn(Material.IRON_SWORD, "<red>Dégâts : <white>" + def.damage(), "<gray>clic = +1 · clic droit = -1"));
        inv.setItem(21, StudioItems.btn(Material.IRON_CHESTPLATE, "<yellow>Armure : <white>" + def.armor(), "<gray>clic = +1 · clic droit = -1"));
        inv.setItem(22, StudioItems.btn(Material.FEATHER, "<aqua>Vitesse : <white>" + String.format(java.util.Locale.ROOT, "%.2f", def.speed()), "<gray>clic = +0.02 · clic droit = -0.02"));
        inv.setItem(23, StudioItems.btn(Material.EXPERIENCE_BOTTLE, "<green>XP progression : <white>" + def.progressionXp(), "<gray>clic = +250 · clic droit = -250"));
        inv.setItem(24, StudioItems.btn(Material.ZOMBIE_HEAD, "<yellow>Entité : <white>" + def.entityType().name(), "<gray>clic = saisir EntityType"));
        inv.setItem(25, StudioItems.btn(Material.ARMOR_STAND, "<gold>Équipement", "<gray>casque, plastron, armes… (objets custom ou vanilla)"));
        inv.setItem(26, StudioItems.btn(Material.RED_STAINED_GLASS, "<gold>Éditer les phases", "<gray>seuils de PV + capacités par phase (GUI)"));

        inv.setItem(28, StudioItems.btn(Material.SHIELD, "<gold>Preset phases Tank"));
        inv.setItem(29, StudioItems.btn(Material.BLAZE_ROD, "<light_purple>Preset phases Mage"));
        inv.setItem(30, StudioItems.btn(Material.ZOMBIE_HEAD, "<red>Preset phases Invocateur"));
        inv.setItem(31, StudioItems.btn(Material.FEATHER, "<aqua>Preset phases Assassin"));
        inv.setItem(32, StudioItems.btn(Material.PAPER, "<yellow>Afficher résumé phases"));
        inv.setItem(33, StudioItems.btn(Material.ENCHANTED_BOOK, "<light_purple>Refaire via IA", "<gray>crée/écrase un boss IA"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        BossManagerModule module = boss();
        BossDefinition def = def();
        if (module == null || def == null) return;
        switch (slot) {
            case 10 -> askName(p, module);
            case 11 -> module.spawn(id, p.getLocation());
            case 12 -> paint(plugin, chat, p, id);
            case 13 -> StudioItems.rebuildAndResend(plugin, p);
            case 14 -> { module.setField(id, "bar-color", nextBar(def.barColor())); build(); }
            case 15 -> BossDropMenu.open(plugin, chat, p, id, 0);
            case 16 -> StudioBossMenu.open(plugin, chat, p, 0);
            case 19 -> { module.setField(id, "max-health", Math.max(10, def.maxHealth() + (rightClick ? -50 : 50) * (shiftClick ? 5 : 1))); build(); }
            case 20 -> { module.setField(id, "damage", Math.max(1, Math.min(40, def.damage() + (rightClick ? -1 : 1)))); build(); }
            case 21 -> { module.setField(id, "armor", Math.max(0, Math.min(30, def.armor() + (rightClick ? -1 : 1)))); build(); }
            case 22 -> { module.setField(id, "speed", Math.max(0.1, Math.min(0.6, def.speed() + (rightClick ? -0.02 : 0.02)))); build(); }
            case 23 -> { module.setField(id, "progression-xp", Math.max(0, def.progressionXp() + (rightClick ? -250 : 250))); build(); }
            case 24 -> askEntity(p, module);
            case 25 -> BossEquipmentMenu.open(plugin, chat, p, id);
            case 26 -> BossPhaseMenu.open(plugin, chat, p, id);
            case 28 -> { module.setPhases(id, StudioBossMenu.phases("tank")); p.sendMessage(Text.mm("<green>Preset tank appliqué.")); }
            case 29 -> { module.setPhases(id, StudioBossMenu.phases("mage")); p.sendMessage(Text.mm("<green>Preset mage appliqué.")); }
            case 30 -> { module.setPhases(id, StudioBossMenu.phases("summoner")); p.sendMessage(Text.mm("<green>Preset invocateur appliqué.")); }
            case 31 -> { module.setPhases(id, StudioBossMenu.phases("assassin")); p.sendMessage(Text.mm("<green>Preset assassin appliqué.")); }
            case 32 -> phaseSummary(p, def);
            case 33 -> {
                p.closeInventory();
                chat.request(p, "<yellow>Nouvelle description IA du boss :", in -> p.performCommand("moon ai createboss " + in));
            }
            default -> { }
        }
    }

    private void askName(Player p, BossManagerModule module) {
        p.closeInventory();
        chat.request(p, "<yellow>Nouveau nom MiniMessage :", in -> { module.setField(id, "display-name", in); open(plugin, chat, p, id); });
    }

    private void askEntity(Player p, BossManagerModule module) {
        p.closeInventory();
        chat.request(p, "<yellow>EntityType Bukkit (ex <white>WITHER_SKELETON</white>) :", in -> {
            try {
                EntityType type = EntityType.valueOf(in.toUpperCase(java.util.Locale.ROOT));
                module.setField(id, "entity", type.name());
            } catch (IllegalArgumentException e) {
                p.sendMessage(Text.mm("<red>EntityType invalide : " + in));
            }
            open(plugin, chat, p, id);
        });
    }

    private void phaseSummary(Player p, BossDefinition def) {
        p.sendMessage(Text.mm("<gradient:#8a2be2:#c77dff>Phases de " + id + "</gradient>"));
        def.phases().forEach(phase -> p.sendMessage(Text.mm("<gray>• <white>" + phase.name()
                + " <gray>à <white>" + phase.fromPercent() + "% <gray>— <white>" + phase.abilities().size() + " capacité(s)")));
    }

    private String nextBar(String current) {
        int idx = BAR_COLORS.indexOf(current == null ? "PURPLE" : current.toUpperCase(java.util.Locale.ROOT));
        return BAR_COLORS.get((idx + 1 + BAR_COLORS.size()) % BAR_COLORS.size());
    }

    private BossManagerModule boss() { return plugin.moduleManager().get(BossManagerModule.class); }
    private BossDefinition def() { BossManagerModule m = boss(); return m == null ? null : m.definition(id); }

    @Override
    public Inventory getInventory() { return inv; }
}
