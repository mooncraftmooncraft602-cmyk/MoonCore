package com.mooncore.modules.enditems;

import com.mooncore.api.item.CustomItemService;
import com.mooncore.command.sub.ItemSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * EndGameItems : objets endgame custom (marqués via PDC). Inclut le <b>Netherite Flight Core</b>
 * — un plastron netherite qui conserve ses stats d'armure tout en offrant le vol type Elytra
 * (composant {@code GLIDER} de Paper) — et d'autres objets utiles.
 */
@ModuleInfo(id = "endgame-items", name = "EndGameItems", softDepends = {"custom-enchant", "progression"})
public final class EndgameItemsModule extends AbstractModule implements CustomItemService {

    public static final String FLIGHT_CORE = "netherite-flight-core";
    public static final String RECALL_STAFF = "recall-staff";

    private final Map<String, Supplier<ItemStack>> registry = new LinkedHashMap<>();
    private NamespacedKey itemKey;

    @Override
    protected void onEnable() {
        this.itemKey = new NamespacedKey(plugin(), "item");

        registry.put(FLIGHT_CORE, this::buildFlightCore);
        registry.put(RECALL_STAFF, this::buildRecallStaff);

        services().register(CustomItemService.class, this);
        registerListener(new EndItemListener(plugin(), this));
        plugin().rootCommand().register(new ItemSubCommand(this));
        log().info("EndGameItems : " + registry.size() + " objet(s) endgame.");
    }

    @Override
    protected void onDisable() {
        services().unregister(CustomItemService.class);
        registry.clear();
    }

    // ---- CustomItemService ----

    @Override
    public Set<String> ids() {
        return registry.keySet();
    }

    @Override
    public ItemStack create(String id) {
        Supplier<ItemStack> s = registry.get(id);
        return s == null ? null : s.get();
    }

    @Override
    public boolean give(Player player, String id) {
        ItemStack item = create(id);
        if (item == null) return false;
        player.getInventory().addItem(item).values()
                .forEach(of -> player.getWorld().dropItemNaturally(player.getLocation(), of));
        return true;
    }

    @Override
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    // ---- Fabriques d'objets ----

    private ItemStack base(Material material, String id, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.mm(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<Component> loreComponents = new java.util.ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Text.mm(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        meta.lore(loreComponents);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildFlightCore() {
        ItemStack item = base(Material.NETHERITE_CHESTPLATE, FLIGHT_CORE,
                "<gradient:#5d4fff:#c77dff>Noyau de Vol Netherite</gradient>",
                List.of(
                        "<gray>Conserve les stats du plastron netherite</gray>",
                        "<gray>et permet le vol type <white>Élytre</white>.</gray>",
                        "<dark_gray>Objet endgame</dark_gray>"));
        // Composant GLIDER (Paper 1.21.4+) : l'objet agit comme une élytre dans l'emplacement
        // plastron tout en gardant l'armure netherite. Appliqué par réflexion pour rester
        // compatible avec les versions antérieures (1.21.1 : reste un plastron netherite).
        applyGliderComponent(item);
        return item;
    }

    /**
     * Applique le composant GLIDER par réflexion (Paper 1.21.4+). No-op sur les versions
     * antérieures (le Flight Core reste alors un plastron netherite sans vol).
     */
    private void applyGliderComponent(ItemStack item) {
        try {
            Class<?> types = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            Object glider = types.getField("GLIDER").get(null);
            Class<?> nonValued = Class.forName("io.papermc.paper.datacomponent.DataComponentType$NonValued");
            ItemStack.class.getMethod("setData", nonValued).invoke(item, glider);
        } catch (Throwable ignored) {
            log().debug("Composant GLIDER indisponible (Paper < 1.21.4) : Flight Core en mode armure seule.");
        }
    }

    private ItemStack buildRecallStaff() {
        return base(Material.BLAZE_ROD, RECALL_STAFF,
                "<gradient:#ffd000:#ff7b00>Bâton de Rappel</gradient>",
                List.of(
                        "<gray>Clic droit : retour au point d'apparition.</gray>",
                        "<dark_gray>Cooldown</dark_gray>"));
    }
}
