package com.mooncore.modules.customitem.paint;

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
 * Modèles & tampons : bases d'objet procédurales (clic = dessine dans la couleur choisie,
 * efface la toile) et tampons de formes (clic = applique par-dessus). Routé par {@link PaintListener}.
 */
public final class PaintTemplatesMenu implements InventoryHolder {

    private static final int BASE_START = 0;   // 10 bases sur la 1re rangée
    private static final int STAMP_START = 27; // 8 tampons sur la 4e rangée

    private final PaintSession session;
    private Inventory inv;

    private PaintTemplatesMenu(PaintSession session) { this.session = session; }

    public static void open(PaintSession session) {
        Player p = session.player();
        if (p == null) return;
        PaintTemplatesMenu m = new PaintTemplatesMenu(session);
        m.inv = Bukkit.createInventory(m, 54, Text.mm("<gradient:#8a2be2:#c77dff>Modèles & tampons</gradient>"));
        m.build();
        p.openInventory(m.inv);
    }

    private void build() {
        ItemStack pane = btn(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, pane);

        inv.setItem(18, btn(Material.BOOK, "<aqua>Bases d'objet ↑", "<gray>clic = dessine la base (efface la toile)"));
        for (int i = 0; i < TextureTemplates.BASES.length; i++) {
            String id = TextureTemplates.BASES[i];
            inv.setItem(BASE_START + i, btn(baseIcon(id), "<aqua>" + TextureTemplates.label(id),
                    "<gray>base dans la couleur actuelle"));
        }

        inv.setItem(36, btn(Material.BOOK, "<light_purple>Tampons formes ↓", "<gray>clic = applique par-dessus"));
        for (int i = 0; i < TextureTemplates.STAMPS.length; i++) {
            String id = TextureTemplates.STAMPS[i];
            inv.setItem(STAMP_START + i, btn(stampIcon(id), "<light_purple>" + TextureTemplates.label(id),
                    "<gray>tampon dans la couleur actuelle"));
        }

        inv.setItem(49, btn(Material.OAK_DOOR, "<yellow>← Retour assistant"));
        inv.setItem(53, btn(Material.BARRIER, "<red>Fermer"));
    }

    public void click(Player p, int slot) {
        if (slot == 49) { PaintAssistantMenu.open(session); return; }
        if (slot == 53) { p.closeInventory(); return; }
        if (slot >= BASE_START && slot < BASE_START + TextureTemplates.BASES.length) {
            session.applyBase(TextureTemplates.BASES[slot - BASE_START]);
            p.closeInventory(); // base = on efface : on referme pour voir la toile
            return;
        }
        if (slot >= STAMP_START && slot < STAMP_START + TextureTemplates.STAMPS.length) {
            session.applyStamp(TextureTemplates.STAMPS[slot - STAMP_START]); // tampon = on reste pour enchaîner
        }
    }

    private static Material baseIcon(String id) {
        return switch (id) {
            case "sword" -> Material.IRON_SWORD; case "pickaxe" -> Material.IRON_PICKAXE;
            case "axe" -> Material.IRON_AXE; case "shovel" -> Material.IRON_SHOVEL;
            case "ingot" -> Material.IRON_INGOT; case "gem" -> Material.DIAMOND;
            case "potion" -> Material.POTION; case "ring" -> Material.GOLD_NUGGET;
            case "ore" -> Material.IRON_ORE; case "shield" -> Material.SHIELD;
            default -> Material.PAPER;
        };
    }

    private static Material stampIcon(String id) {
        return switch (id) {
            case "circle" -> Material.SNOWBALL; case "ring" -> Material.IRON_NUGGET;
            case "diamond" -> Material.DIAMOND; case "heart" -> Material.POPPY;
            case "cross" -> Material.RED_DYE; case "frame" -> Material.ITEM_FRAME;
            case "triangle" -> Material.PRISMARINE_SHARD; case "star" -> Material.NETHER_STAR;
            default -> Material.PAPER;
        };
    }

    private static ItemStack btn(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                java.util.List<net.kyori.adventure.text.Component> l = new java.util.ArrayList<>();
                for (String s : lore) l.add(Text.mm(s).decoration(TextDecoration.ITALIC, false));
                meta.lore(l);
            }
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    @Override
    public Inventory getInventory() { return inv; }
}
