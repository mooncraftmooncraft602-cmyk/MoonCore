package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.boss.AbilityInstance;
import com.mooncore.modules.boss.BossDefinition;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.boss.BossPhase;
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

/** Éditeur GUI des phases d'un boss : ajouter/retirer une phase, ouvrir ses capacités. */
public final class BossPhaseMenu implements StudioMenu {

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private BossPhaseMenu(MoonCore plugin, ChatInput chat, String id) {
        this.plugin = plugin;
        this.chat = chat;
        this.id = id;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String id) {
        BossPhaseMenu m = new BossPhaseMenu(plugin, chat, id);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Phases</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        BossManagerModule boss = boss();
        BossDefinition def = boss == null ? null : boss.definition(id);
        if (def == null) { inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Boss introuvable")); return; }
        List<BossPhase> phases = def.phases();
        for (int i = 0; i < phases.size() && i < 45; i++) {
            BossPhase ph = phases.get(i);
            inv.setItem(i, StudioItems.btn(Material.RED_STAINED_GLASS,
                    "<yellow>Phase " + (i + 1) + " <gray>(dès " + (int) ph.fromPercent() + "% PV)",
                    "<gray>" + ph.abilities().size() + " capacité(s)",
                    "<dark_gray>clic = éditer les capacités",
                    "<dark_gray>clic droit = retirer la phase"));
        }
        inv.setItem(48, StudioItems.btn(Material.LIME_DYE, "<green>Ajouter une phase", "<gray>demande le seuil de PV (%)"));
        inv.setItem(49, StudioItems.btn(Material.BOOK, "<gray>" + phases.size() + " phase(s)"));
        inv.setItem(53, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 53) { BossEditorMenu.open(plugin, chat, p, id); return; }
        BossManagerModule boss = boss();
        BossDefinition def = boss == null ? null : boss.definition(id);
        if (def == null) return;
        if (slot == 48) {
            p.closeInventory();
            chat.request(p, "<yellow>Seuil de la phase en % de PV (ex <white>50</white>) :", in -> {
                int pct;
                try { pct = Math.max(1, Math.min(100, Integer.parseInt(in.trim()))); }
                catch (NumberFormatException e) { p.sendMessage(Text.mm("<red>Nombre invalide.")); open(plugin, chat, p, id); return; }
                List<BossPhase> phases = new ArrayList<>(def.phases());
                phases.add(new BossPhase("p" + (phases.size() + 1), pct, List.of()));
                save(boss, id, phases);
                open(plugin, chat, p, id);
            });
            return;
        }
        if (slot >= 0 && slot < def.phases().size() && slot < 45) {
            if (rightClick) {
                if (def.phases().size() <= 1) { p.sendActionBar(Text.mm("<red>Au moins une phase requise.")); return; }
                List<BossPhase> phases = new ArrayList<>(def.phases());
                phases.remove(slot);
                save(boss, id, phases);
                build();
            } else {
                BossPhaseAbilitiesMenu.open(plugin, chat, p, id, slot);
            }
        }
    }

    /** Sérialise une liste de phases vers le format YAML attendu et persiste via le module boss. */
    static void save(BossManagerModule boss, String id, List<BossPhase> phases) {
        Map<String, Object> map = new LinkedHashMap<>();
        int i = 1;
        for (BossPhase ph : phases) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("from-percent", ph.fromPercent());
            List<Map<String, Object>> abs = new ArrayList<>();
            for (AbilityInstance ai : ph.abilities()) {
                Map<String, Object> am = new LinkedHashMap<>();
                am.put("type", ai.type().name());
                am.put("cooldown-ticks", ai.cooldownTicks());
                am.put("magnitude", ai.magnitude());
                am.put("count", ai.count());
                am.put("radius", ai.radius());
                abs.add(am);
            }
            pm.put("abilities", abs);
            map.put("p" + i, pm);
            i++;
        }
        boss.setPhases(id, map);
    }

    private BossManagerModule boss() { return plugin.moduleManager().get(BossManagerModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
