package com.mooncore.modules.customitem;

import com.mooncore.MoonCore;
import com.mooncore.api.customitem.ItemStats;
import com.mooncore.api.customitem.Rarity;
import com.mooncore.modules.customitem.ability.Ability;
import com.mooncore.modules.customitem.ability.AbilityRegistry;
import com.mooncore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Construit l'{@link ItemStack} d'une définition. Toutes les informations (rareté,
 * stats, capacités) sont écrites dans le <b>lore</b> en plus de la PDC : c'est le
 * fallback Bedrock — même sans modèle custom, le joueur voit tout et le gameplay
 * (PDC côté serveur) reste identique.
 */
public final class CustomItemFactory {

    /** Résout libellé + couleur d'une rareté (surchargés par la config). */
    public interface RarityResolver {
        String color(Rarity rarity);
        String label(Rarity rarity);
    }

    private final MoonCore plugin;
    private final NamespacedKey idKey;
    private final AbilityRegistry abilities;
    private final RarityResolver rarities;

    public CustomItemFactory(MoonCore plugin, NamespacedKey idKey,
                             AbilityRegistry abilities, RarityResolver rarities) {
        this.plugin = plugin;
        this.idKey = idKey;
        this.abilities = abilities;
        this.rarities = rarities;
    }

    public ItemStack build(CustomItemDef def, int amount) {
        ItemStack item = new ItemStack(def.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item; // matériau sans meta (improbable)

        // Nom coloré par rareté.
        meta.displayName(Text.mm(def.displayName()).decoration(TextDecoration.ITALIC, false));

        // Lore.
        meta.lore(buildLore(def));

        // PDC : identité de l'objet.
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());

        // Modèle custom (optionnel ; ignoré silencieusement par les clients sans pack).
        if (def.customModelData() > 0) {
            meta.setCustomModelData(def.customModelData());
        }
        if (def.glowing()) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (def.unbreakable()) {
            meta.setUnbreakable(true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        }
        // On masque l'affichage vanilla des attributs (« +13 Attack Damage »…) : nos stats sont
        // déjà listées dans la section « Statistiques » → évite le doublon et raccourcit l'infobulle.
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        // Attributs vanilla (s'appliquent serveur-side → identiques Java/Bedrock).
        applyAttributes(def, meta);

        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(CustomItemDef def) {
        List<Component> lore = new ArrayList<>();
        String rarColor = rarities.color(def.rarity());
        String rarLabel = rarities.label(def.rarity());

        // Ligne de rareté + type.
        lore.add(line(rarColor + "❖ " + rarLabel + " <dark_gray>• <gray>" + typeLabel(def)));

        // Stats.
        if (!def.stats().isEmpty()) {
            lore.add(Component.empty());
            lore.add(line("<gray>Statistiques :"));
            for (Map.Entry<String, Double> e : def.stats().entrySet()) {
                String label = ItemStats.label(e.getKey());
                boolean pct = ItemStats.isPercent(e.getKey());
                String val = formatValue(e.getValue(), pct);
                lore.add(line(" <dark_gray>▸ <white>" + label + " <green>" + val));
            }
        }

        // Capacités. Affichage COMPACT dès qu'il y en a beaucoup (sinon l'infobulle déborde
        // de l'écran) : 1 ligne par capacité, descriptions seulement s'il y en a peu, et on
        // plafonne le nombre de lignes affichées (« +N autres »).
        if (!def.abilities().isEmpty()) {
            List<CustomItemDef.AbilityRef> refs = def.abilities();
            int n = refs.size();
            int maxShown = 14;                // garde-fou anti-débordement de l'infobulle
            lore.add(Component.empty());
            lore.add(line("<gray>Capacités <dark_gray>(" + n + ") :"));
            int shown = Math.min(n, maxShown);
            // 1 SEULE ligne par capacité, SANS description (l'infobulle débordait de l'écran ;
            // les descriptions restent visibles dans l'éditeur de capacités).
            for (int i = 0; i < shown; i++) {
                CustomItemDef.AbilityRef ref = refs.get(i);
                Ability ab = abilities.get(ref.id());
                String name = ab != null ? ab.displayName() : ref.id();
                String tag = (ab != null && ab.isActive()) ? "<gold>⚡" : "<aqua>✦";
                lore.add(line(" " + tag + " <yellow>" + name + " <gray>" + roman(ref.level())));
            }
            if (n > maxShown) lore.add(line(" <dark_gray>+ " + (n - maxShown) + " autre(s)…"));
            if (refs.stream().anyMatch(r -> { Ability a = abilities.get(r.id()); return a != null && a.isActive(); })) {
                lore.add(line(" <dark_gray><italic>Clic droit = capacité active"));
            }
        }

        // Lore narratif libre.
        if (!def.lore().isEmpty()) {
            lore.add(Component.empty());
            for (String l : def.lore()) {
                lore.add(line(l));
            }
        }
        return lore;
    }

    private void applyAttributes(CustomItemDef def, ItemMeta meta) {
        EquipmentSlotGroup group;
        if (def.type().appliesWorn()) {
            group = EquipmentSlotGroup.ARMOR;
        } else if (def.type().appliesHeld()) {
            group = EquipmentSlotGroup.MAINHAND;
        } else {
            group = EquipmentSlotGroup.ANY;
        }

        for (Map.Entry<String, Double> e : def.stats().entrySet()) {
            ItemStats.StatMeta m = ItemStats.meta(e.getKey());
            if (m == null || m.vanillaAttribute() == null) continue; // stat gérée par le listener
            Attribute attr = matchAttribute(m.vanillaAttribute());
            if (attr == null) continue;

            double value = e.getValue();
            AttributeModifier.Operation op;
            if (m.percent()) {
                op = AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                value = value / 100.0;
            } else {
                op = AttributeModifier.Operation.ADD_NUMBER;
            }
            NamespacedKey modKey = new NamespacedKey(plugin, "ci_" + e.getKey());
            meta.addAttributeModifier(attr, new AttributeModifier(modKey, value, op, group));
        }
    }

    private static Attribute matchAttribute(String name) {
        return com.mooncore.util.Attrs.byKey(name);
    }

    private static String typeLabel(CustomItemDef def) {
        if (def.toolKind() != com.mooncore.modules.customitem.ToolKind.NONE) {
            return def.toolKind().label() + " " + def.toolTier().label();
        }
        return switch (def.type()) {
            case WEAPON -> "Arme";
            case TOOL -> "Outil";
            case ARMOR -> "Armure";
            case ACCESSORY -> "Accessoire";
            case RELIC -> "Relique";
            case ARTIFACT -> "Artéfact";
            case BOSS_ITEM -> "Objet de boss";
            case CONSUMABLE -> "Consommable";
            case EVENT_ITEM -> "Objet d'événement";
        };
    }

    private static String formatValue(double v, boolean percent) {
        String sign = v > 0 ? "+" : "";
        String num = (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
        return sign + num + (percent ? "%" : "");
    }

    private static String roman(int n) {
        return switch (Math.max(1, Math.min(10, n))) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; default -> "X";
        };
    }

    private static Component line(String mm) {
        return Text.mm(mm).decoration(TextDecoration.ITALIC, false);
    }

    public NamespacedKey idKey() { return idKey; }

    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }
}
