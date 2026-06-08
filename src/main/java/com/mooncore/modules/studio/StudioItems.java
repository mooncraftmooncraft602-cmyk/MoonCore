package com.mooncore.modules.studio;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.util.Text;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class StudioItems {

    static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private StudioItems() {}

    static ItemStack btn(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
                for (String line : lore) lines.add(Text.mm(line).decoration(TextDecoration.ITALIC, false));
                meta.lore(lines);
            }
            meta.addItemFlags(ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }

    static ItemStack label(Material mat, String name, String... lore) {
        return btn(mat, name, lore);
    }

    static ItemStack pane() {
        return btn(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    static void fill(org.bukkit.inventory.Inventory inv) {
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    static String slug(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]", "_");
        s = s.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return s.isBlank() ? "custom" : (s.length() > 48 ? s.substring(0, 48) : s);
    }

    static void rebuildAndResend(MoonCore plugin, Player p) {
        plugin.services().get(ResourcePackService.class).ifPresentOrElse(rp -> {
            rp.rebuild();
            rp.resendAll();
            p.sendMessage(Text.mm("<green>Resource pack reconstruit et renvoyé. <gray>" + rp.url()));
        }, () -> p.sendMessage(Text.mm("<red>Module resource-pack inactif.")));
    }

    static void rebuild(MoonCore plugin, Player p) {
        plugin.services().get(ResourcePackService.class).ifPresentOrElse(rp -> {
            rp.rebuild();
            p.sendMessage(Text.mm("<green>Resource pack reconstruit. <gray>" + rp.url()));
        }, () -> p.sendMessage(Text.mm("<red>Module resource-pack inactif.")));
    }

    static void resend(MoonCore plugin, Player p) {
        plugin.services().get(ResourcePackService.class).ifPresentOrElse(rp -> {
            rp.resendAll();
            p.sendMessage(Text.mm("<green>Resource pack renvoyé aux joueurs Java."));
        }, () -> p.sendMessage(Text.mm("<red>Module resource-pack inactif.")));
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
