package com.mooncore.modules.kit;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Remplace les <b>kits</b> d'EssentialsX : {@code /kit} liste les kits, {@code /kit <nom>} le
 * réclame (cooldown par joueur persisté dans la PDC). Kits définis dans {@code modules/kit.yml}
 * (items vanilla {@code MATERIAL [n]} ou objets custom {@code custom:<id> [n]}).
 */
@ModuleInfo(id = "kit", name = "Kits", softDepends = {"custom-item"})
public final class KitModule extends AbstractModule implements SubCommand {

    private record Kit(String name, long cooldownSeconds, String permission, List<String> items) {}

    private final Map<String, Kit> kits = new LinkedHashMap<>();

    @Override
    protected void onEnable() {
        load();
        plugin().rootCommand().register(this);
    }

    @Override protected void onDisable() { kits.clear(); }
    @Override protected void onReload() { reloadModuleConfig(); load(); }

    private void load() {
        kits.clear();
        long defCd = moduleConfig().getLong("cooldown-seconds", 86400);
        ConfigurationSection sec = moduleConfig().getConfigurationSection("kits");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection k = sec.getConfigurationSection(name);
            if (k == null) continue;
            kits.put(name.toLowerCase(Locale.ROOT), new Kit(
                    name.toLowerCase(Locale.ROOT),
                    k.getLong("cooldown-seconds", defCd),
                    k.getString("permission", ""),
                    k.getStringList("items")));
        }
    }

    // ---- SubCommand ----

    @Override public String name() { return "kit"; }
    @Override public List<String> aliases() { return List.of("kits"); }
    @Override public String permission() { return "mooncore.kit.use"; }
    @Override public String description() { return "Réclame un kit"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        Player p = (Player) s;
        if (a.length < 1) {
            List<String> avail = new ArrayList<>();
            for (Kit k : kits.values()) if (canUse(p, k)) avail.add(k.name());
            p.sendMessage(avail.isEmpty() ? Text.mm("<gray>Aucun kit disponible.")
                    : Text.mm("<gray>Kits : <white>" + String.join(", ", avail)));
            return;
        }
        Kit kit = kits.get(a[0].toLowerCase(Locale.ROOT));
        if (kit == null || !canUse(p, kit)) { p.sendMessage(Text.mm("<red>Kit inconnu ou non autorisé : <white>" + a[0])); return; }

        long now = System.currentTimeMillis();
        NamespacedKey key = new NamespacedKey(plugin, "kit_" + kit.name());
        Long last = p.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        if (kit.cooldownSeconds() > 0 && last != null) {
            long elapsed = (now - last) / 1000L;
            if (elapsed < kit.cooldownSeconds() && !p.hasPermission("mooncore.bypass.cooldown")) {
                long rem = kit.cooldownSeconds() - elapsed;
                p.sendMessage(Text.mm("<red>Kit <white>" + kit.name() + "</white> dispo dans <white>" + formatDur(rem) + "</white>."));
                return;
            }
        }

        List<ItemStack> stacks = buildItems(plugin, kit);
        if (stacks.isEmpty()) { p.sendMessage(Text.mm("<red>Ce kit est vide ou mal configuré.")); return; }
        for (ItemStack it : stacks) {
            p.getInventory().addItem(it).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
        p.getPersistentDataContainer().set(key, PersistentDataType.LONG, now);
        p.sendMessage(Text.mm("<green>Kit <white>" + kit.name() + "</white> reçu !"));
    }

    private boolean canUse(Player p, Kit k) {
        return k.permission() == null || k.permission().isBlank() || p.hasPermission(k.permission());
    }

    private List<ItemStack> buildItems(MoonCore plugin, Kit kit) {
        CustomItemManagerModule ci = plugin.moduleManager().get(CustomItemManagerModule.class);
        List<ItemStack> out = new ArrayList<>();
        for (String entry : kit.items()) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.trim().split("\\s+");
            int amount = 1;
            if (parts.length >= 2) { try { amount = Math.max(1, Integer.parseInt(parts[1])); } catch (NumberFormatException ignored) { } }
            String token = parts[0];
            if (token.toLowerCase(Locale.ROOT).startsWith("custom:") && ci != null) {
                ItemStack custom = ci.create(token.substring("custom:".length()).toLowerCase(Locale.ROOT), amount);
                if (custom != null) out.add(custom);
            } else {
                Material m = Material.matchMaterial(token.toUpperCase(Locale.ROOT));
                if (m != null && m.isItem()) out.add(new ItemStack(m, amount));
            }
        }
        return out;
    }

    private static String formatDur(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h" + (m > 0 ? m + "m" : "");
        if (m > 0) return m + "m" + (s > 0 ? s + "s" : "");
        return s + "s";
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1 && s instanceof Player p) {
            String pre = a[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Kit k : kits.values()) if (k.name().startsWith(pre) && canUse(p, k)) out.add(k.name());
            return out;
        }
        return List.of();
    }
}
