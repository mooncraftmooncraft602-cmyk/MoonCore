package com.mooncore.modules.customitem.editor;

import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import com.mooncore.modules.customitem.paint.ItemPaintTarget;
import com.mooncore.api.customitem.ItemType;
import com.mooncore.api.customitem.Rarity;
import com.mooncore.util.ChatInput;
import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Assistant GUI tout-en-un : crée/édite un item custom en un seul menu (nom, matériau,
 * rareté, type, glow/incassable, stats, capacités, texture, recevoir). Édite le
 * {@link CustomItemDef} en direct (chaque action persiste via {@code module.put}).
 */
public final class ItemEditorMenu implements InventoryHolder {

    private final CustomItemManagerModule module;
    private final ChatInput chat;
    private final String id;
    private Inventory inv;

    private ItemEditorMenu(CustomItemManagerModule module, ChatInput chat, String id) {
        this.module = module;
        this.chat = chat;
        this.id = id;
    }

    public static void open(CustomItemManagerModule module, ChatInput chat, Player p, String id) {
        ItemEditorMenu m = new ItemEditorMenu(module, chat, id);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Éditeur</gradient> <dark_gray>» <white>" + id));
        m.build();
        p.openInventory(m.inv);
    }

    private CustomItemDef def() { return module.rawDef(id); }

    private void build() {
        CustomItemDef d = def();
        if (d == null) return;
        filler();
        inv.setItem(4, labeled(module.buildItem(d, 1), "<aqua>Aperçu : <reset>" + d.displayName()));
        inv.setItem(19, btn(Material.NAME_TAG, "<yellow>Nom", "<gray>" + d.displayName(), "<dark_gray>clic = changer"));
        inv.setItem(20, labeled(safeItem(d.material()), "<yellow>Matériau : <white>" + d.material().name(),
                "<dark_gray>clic = changer (nom Bukkit)"));
        inv.setItem(21, btn(Material.BOOK, "<yellow>Rareté : <white>" + d.rarity().id(), "<dark_gray>clic = suivante"));
        inv.setItem(22, btn(Material.COMPASS, "<yellow>Type : <white>" + d.type().id(), "<dark_gray>clic = suivant"));
        inv.setItem(23, btn(d.glowing() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER,
                "<yellow>Brillance : " + onOff(d.glowing())));
        inv.setItem(24, btn(d.unbreakable() ? Material.NETHERITE_INGOT : Material.IRON_NUGGET,
                "<yellow>Incassable : " + onOff(d.unbreakable())));
        inv.setItem(25, btn(Material.PAPER, "<yellow>Lore : <white>" + d.lore().size() + " ligne(s)",
                "<dark_gray>clic = ajouter une ligne <dark_gray>| clic droit = vider"));

        inv.setItem(26, btn(toolIcon(d.toolKind()), "<yellow>Outil reel : <white>" + toolLabel(d),
                "<gray>clic = famille (hache/pioche/pelle...)",
                "<gray>clic droit = tier (bois, fer, diamant...)",
                "<dark_gray>change aussi le materiau vanilla"));

        inv.setItem(27, btn(Material.POTION, "<gold>Effets (consommable)", "<gray>" + d.consumeEffects().size() + " effet(s)",
                "<dark_gray>actif si type = consumable · clic = éditer"));
        inv.setItem(28, btn(Material.ENCHANTING_TABLE, "<gold>Enchantements", "<gray>" + d.enchants().size() + " actif(s)", "<dark_gray>clic = éditer (Sharpness, Protection…)"));
        inv.setItem(29, btn(Material.ANVIL, "<gold>Stats", "<gray>" + d.stats().size() + " active(s)", "<dark_gray>clic = éditer"));
        inv.setItem(30, btn(Material.ENCHANTED_BOOK, "<gold>Capacités", "<gray>" + d.abilities().size(), "<dark_gray>clic = éditer"));
        inv.setItem(31, btn(Material.BRUSH, "<light_purple>🎨 Texture", "<dark_gray>dessiner / éditer en jeu"));
        inv.setItem(32, btn(Material.FURNACE, "<yellow>Fonte : " + (d.canSmelt()
                        ? "<green>→ " + d.smeltsInto().name() + " ×" + d.smeltAmount() : "<red>non fondable"),
                "<gray>clic = définir le résultat (ex <white>IRON_INGOT</white> ou <white>GOLD_NUGGET 2</white>)",
                "<dark_gray>clic droit = désactiver la fonte"));
        inv.setItem(33, btn(Material.CHEST, "<green>📦 Recevoir l'objet"));
        inv.setItem(49, btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int rawSlot, boolean right) {
        CustomItemDef d = def();
        if (d == null) { p.closeInventory(); return; }
        switch (rawSlot) {
            case 19 -> { p.closeInventory(); chat.request(p, "<yellow>Nouveau nom (MiniMessage, ex <gold>Lame du Dragon</gold>) :",
                    in -> { d.setDisplayName(in); module.put(d); reopen(p); }); }
            case 20 -> { p.closeInventory(); chat.request(p, "<yellow>Matériau (nom Bukkit, ex NETHERITE_SWORD) :", in -> {
                    Material m = Material.matchMaterial(in.toUpperCase(java.util.Locale.ROOT));
                    if (m == null || !m.isItem()) p.sendMessage(Text.mm("<red>Matériau invalide : " + in));
                    else { d.setMaterial(m); module.put(d); }
                    reopen(p);
                }); }
            case 21 -> { d.setRarity(cycle(Rarity.values(), d.rarity())); module.put(d); refresh(); }
            case 22 -> { d.setType(cycle(ItemType.values(), d.type())); module.put(d); refresh(); }
            case 23 -> { d.setGlowing(!d.glowing()); module.put(d); refresh(); }
            case 24 -> { d.setUnbreakable(!d.unbreakable()); module.put(d); refresh(); }
            case 25 -> {
                if (right) { d.lore().clear(); module.put(d); refresh(); }
                else { p.closeInventory(); chat.request(p, "<yellow>Ligne de lore à ajouter (MiniMessage) :",
                        in -> { d.lore().add(in); module.put(d); reopen(p); }); }
            }
            case 26 -> {
                if (right) d.setToolTier(nextTier(d.toolTier()));
                else d.setToolKind(nextKind(d.toolKind()));
                module.put(d);
                refresh();
            }
            case 27 -> ConsumableEditorMenu.open(module, chat, p, id);
            case 28 -> EnchantEditorMenu.open(module, chat, p, id);
            case 29 -> StatEditorMenu.open(module, chat, p, id);
            case 30 -> AbilityEditorMenu.open(module, chat, p, id);
            case 31 -> openTexture(p);
            case 32 -> {
                if (right) {
                    d.clearSmelt(); module.put(d); refreshRecipes();
                    p.sendActionBar(Text.mm("<gray>Fonte désactivée")); refresh();
                } else {
                    p.closeInventory();
                    chat.request(p, "<yellow>Résultat de fonte (ex <white>IRON_INGOT</white> ou <white>GOLD_NUGGET 2</white>, ou <white>aucun</white>) :", in -> {
                        String[] parts = in.trim().split("\\s+");
                        if (parts.length == 0 || parts[0].equalsIgnoreCase("aucun") || parts[0].equalsIgnoreCase("none")) {
                            d.clearSmelt();
                        } else {
                            Material m = Material.matchMaterial(parts[0].toUpperCase(java.util.Locale.ROOT));
                            if (m == null || !m.isItem()) { p.sendMessage(Text.mm("<red>Matériau invalide : " + parts[0])); reopen(p); return; }
                            int amt = 1;
                            if (parts.length > 1) try { amt = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { }
                            d.setSmeltsInto(m, amt);
                            p.sendMessage(Text.mm("<green>Fonte : <white>" + id + " <gray>→ <white>" + m.name() + " ×" + d.smeltAmount()
                                    + " <gray>(au four)"));
                        }
                        module.put(d); refreshRecipes(); reopen(p);
                    });
                }
            }
            case 33 -> { module.give(p, id, 1); p.sendMessage(Text.mm("<green>Reçu : <reset>" + d.displayName())); }
            case 49 -> p.closeInventory();
            default -> { }
        }
    }

    private void openTexture(Player p) {
        p.closeInventory();
        java.io.File existing = new java.io.File(module.texturesFolder(), id + ".png");
        java.io.File source = existing.isFile() ? existing : null;
        // Toile 16×16 par défaut (texels gros = facile) ; onClose = rouvrir cet éditeur.
        module.paintManager().open(p, new ItemPaintTarget(module, id), 16, source,
                () -> { if (p.isOnline()) open(module, chat, p, id); });
    }

    private void reopen(Player p) { if (p.isOnline()) open(module, chat, p, id); }

    private void refresh() { build(); }

    /** Ré-enregistre les recettes (artisanat + fonte) après un changement de fonte. */
    private void refreshRecipes() {
        try { module.recipeManager().unregisterAll(); module.recipeManager().registerAll(); }
        catch (Exception ignored) { }
    }

    // ---- helpers ----

    private void filler() {
        ItemStack pane = btn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);
    }

    private static <E> E cycle(E[] values, E current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    private static String onOff(boolean b) { return b ? "<green>ON" : "<red>OFF"; }

    private static ToolKind nextKind(ToolKind current) {
        ToolKind[] values = {ToolKind.NONE, ToolKind.PICKAXE, ToolKind.AXE, ToolKind.SHOVEL, ToolKind.HOE, ToolKind.SWORD};
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return ToolKind.PICKAXE;
    }

    private static ToolTier nextTier(ToolTier current) {
        ToolTier[] values = {ToolTier.WOOD, ToolTier.STONE, ToolTier.IRON, ToolTier.GOLD, ToolTier.DIAMOND, ToolTier.NETHERITE};
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return ToolTier.IRON;
    }

    private static String toolLabel(CustomItemDef d) {
        return d.toolKind() == ToolKind.NONE ? "aucun" : d.toolKind().label() + " " + d.toolTier().label();
    }

    private static Material toolIcon(ToolKind kind) {
        return switch (kind) {
            case PICKAXE -> Material.IRON_PICKAXE;
            case AXE -> Material.IRON_AXE;
            case SHOVEL -> Material.IRON_SHOVEL;
            case HOE -> Material.IRON_HOE;
            case SWORD -> Material.IRON_SWORD;
            case NONE -> Material.STICK;
        };
    }

    private static ItemStack safeItem(Material m) {
        return new ItemStack(m != null && m.isItem() ? m : Material.PAPER);
    }

    static ItemStack btn(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                java.util.List<net.kyori.adventure.text.Component> l = new java.util.ArrayList<>();
                for (String s : lore) l.add(Text.mm(s).decoration(TextDecoration.ITALIC, false));
                meta.lore(l);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    static ItemStack labeled(ItemStack it, String... lines) {
        ItemMeta meta = it.getItemMeta();
        if (meta != null && lines.length > 0) {
            meta.displayName(Text.mm(lines[0]).decoration(TextDecoration.ITALIC, false));
            if (lines.length > 1) {
                java.util.List<net.kyori.adventure.text.Component> l = new java.util.ArrayList<>();
                for (int i = 1; i < lines.length; i++) l.add(Text.mm(lines[i]).decoration(TextDecoration.ITALIC, false));
                meta.lore(l);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public Inventory getInventory() { return inv; }
}
