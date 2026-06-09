package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.modules.boss.AbilityInstance;
import com.mooncore.modules.boss.BossAbilityType;
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
import java.util.List;

/** Capacités d'une phase de boss : ajouter par type, régler magnitude/nombre, retirer. */
public final class BossPhaseAbilitiesMenu implements StudioMenu {

    private static final BossAbilityType[] TYPES = BossAbilityType.values();

    private final MoonCore plugin;
    private final ChatInput chat;
    private final String id;
    private final int phaseIndex;
    private Inventory inv;

    private BossPhaseAbilitiesMenu(MoonCore plugin, ChatInput chat, String id, int phaseIndex) {
        this.plugin = plugin;
        this.chat = chat;
        this.id = id;
        this.phaseIndex = phaseIndex;
    }

    public static void open(MoonCore plugin, ChatInput chat, Player p, String id, int phaseIndex) {
        BossPhaseAbilitiesMenu m = new BossPhaseAbilitiesMenu(plugin, chat, id, phaseIndex);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Capacités phase " + (phaseIndex + 1) + "</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        StudioItems.fill(inv);
        BossPhase ph = phase();
        if (ph == null) { inv.setItem(22, StudioItems.btn(Material.BARRIER, "<red>Phase introuvable")); return; }

        // Rangée du haut : types de capacités à ajouter.
        for (int i = 0; i < TYPES.length && i < 9; i++) {
            inv.setItem(i, StudioItems.btn(icon(TYPES[i]), "<green>+ " + TYPES[i].name(), "<gray>ajouter à la phase"));
        }
        // Capacités actuelles.
        List<AbilityInstance> abs = ph.abilities();
        for (int i = 0; i < abs.size() && i < 27; i++) {
            AbilityInstance ai = abs.get(i);
            inv.setItem(18 + i, StudioItems.btn(icon(ai.type()), "<aqua>" + ai.type().name(),
                    "<gray>CD <white>" + ai.cooldownTicks() + "t <gray>· mag <white>" + (int) ai.magnitude()
                            + " <gray>· n <white>" + ai.count() + " <gray>· r <white>" + (int) ai.radius(),
                    "<dark_gray>clic = magnitude+ · shift = nombre+ · clic droit = retirer"));
        }
        inv.setItem(53, StudioItems.btn(Material.OAK_DOOR, "<yellow>Retour aux phases"));
    }

    @Override
    public void click(Player p, int slot, boolean rightClick, boolean shiftClick) {
        if (slot == 53) { BossPhaseMenu.open(plugin, chat, p, id); return; }
        BossManagerModule boss = boss();
        BossDefinition def = boss == null ? null : boss.definition(id);
        if (def == null || phaseIndex >= def.phases().size()) return;
        List<BossPhase> phases = new ArrayList<>(def.phases());
        BossPhase ph = phases.get(phaseIndex);
        List<AbilityInstance> abs = new ArrayList<>(ph.abilities());

        if (slot >= 0 && slot < TYPES.length && slot < 9) {
            abs.add(new AbilityInstance(TYPES[slot], 100, 5, 1, 6)); // valeurs par défaut
        } else if (slot >= 18 && slot - 18 < abs.size()) {
            int idx = slot - 18;
            AbilityInstance ai = abs.get(idx);
            if (rightClick) {
                abs.remove(idx);
            } else if (shiftClick) {
                abs.set(idx, new AbilityInstance(ai.type(), ai.cooldownTicks(), ai.magnitude(),
                        ai.count() >= 10 ? 1 : ai.count() + 1, ai.radius()));
            } else {
                double mag = ai.magnitude() >= 100 ? 5 : ai.magnitude() + 5;
                abs.set(idx, new AbilityInstance(ai.type(), ai.cooldownTicks(), mag, ai.count(), ai.radius()));
            }
        } else {
            return;
        }
        phases.set(phaseIndex, new BossPhase(ph.name(), ph.fromPercent(), abs));
        BossPhaseMenu.save(boss, id, phases);
        build();
    }

    private BossPhase phase() {
        BossManagerModule boss = boss();
        BossDefinition def = boss == null ? null : boss.definition(id);
        if (def == null || phaseIndex < 0 || phaseIndex >= def.phases().size()) return null;
        return def.phases().get(phaseIndex);
    }

    private static Material icon(BossAbilityType t) {
        return switch (t) {
            case SUMMON -> Material.ZOMBIE_HEAD;
            case HEAL -> Material.GOLDEN_APPLE;
            case POISON -> Material.SPIDER_EYE;
            case IGNITE -> Material.FIRE_CHARGE;
            case EXPLODE -> Material.TNT;
            case AOE_DAMAGE -> Material.IRON_SWORD;
            case TELEPORT -> Material.ENDER_PEARL;
            case DASH -> Material.FEATHER;
            case SHIELD -> Material.SHIELD;
        };
    }

    private BossManagerModule boss() { return plugin.moduleManager().get(BossManagerModule.class); }

    @Override
    public Inventory getInventory() { return inv; }
}
