package com.mooncore.modules.warp;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Remplace les warps d'EssentialsX : {@code /moon warp [nom]} (tp ou liste),
 * {@code /moon setwarp <nom>} et {@code /moon delwarp <nom>} (admin). Persisté dans
 * {@code plugins/MoonCore/warps.yml}.
 */
@ModuleInfo(id = "warp", name = "Warps")
public final class WarpModule extends AbstractModule {

    private final Map<String, Location> warps = new TreeMap<>();
    private File file;

    @Override
    protected void onEnable() {
        file = new File(plugin().getDataFolder(), "warps.yml");
        loadWarps();
        plugin().rootCommand().register(new Warp());
        plugin().rootCommand().register(new SetWarp());
        plugin().rootCommand().register(new DelWarp());
    }

    @Override protected void onDisable() { warps.clear(); }

    private void loadWarps() {
        warps.clear();
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String name : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(name);
            if (s == null) continue;
            var w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            warps.put(name.toLowerCase(Locale.ROOT), new Location(w,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
    }

    private void saveWarps() {
        YamlConfiguration y = new YamlConfiguration();
        warps.forEach((name, loc) -> {
            ConfigurationSection s = y.createSection(name);
            s.set("world", loc.getWorld() == null ? "" : loc.getWorld().getName());
            s.set("x", loc.getX()); s.set("y", loc.getY()); s.set("z", loc.getZ());
            s.set("yaw", loc.getYaw()); s.set("pitch", loc.getPitch());
        });
        try { if (file.getParentFile() != null) file.getParentFile().mkdirs(); y.save(file); }
        catch (Exception e) { log().warn("Sauvegarde warps.yml échouée : " + e.getMessage()); }
    }

    private final class Warp implements SubCommand {
        @Override public String name() { return "warp"; }
        @Override public List<String> aliases() { return List.of("warps"); }
        @Override public String permission() { return "mooncore.warp.use"; }
        @Override public String description() { return "Téléporte à un warp (sans arg : liste)"; }
        @Override public String category() { return "player"; }
        @Override public boolean playerOnly() { return true; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            if (a.length < 1) {
                p.sendMessage(Text.mm(warps.isEmpty() ? "<gray>Aucun warp défini."
                        : "<gray>Warps : <white>" + String.join(", ", warps.keySet())));
                return;
            }
            Location loc = warps.get(a[0].toLowerCase(Locale.ROOT));
            if (loc == null) { p.sendMessage(Text.mm("<red>Warp inconnu : <white>" + a[0])); return; }
            p.teleport(loc);
            p.sendMessage(Text.mm("<green>Téléporté au warp <white>" + a[0].toLowerCase(Locale.ROOT) + "</white>."));
        }
        @Override public List<String> tabComplete(MoonCore pl, CommandSender s, String[] a) {
            if (a.length == 1) {
                String pre = a[0].toLowerCase(Locale.ROOT);
                return warps.keySet().stream().filter(n -> n.startsWith(pre)).toList();
            }
            return List.of();
        }
    }

    private final class SetWarp implements SubCommand {
        @Override public String name() { return "setwarp"; }
        @Override public String permission() { return "mooncore.admin.warp"; }
        @Override public String description() { return "(admin) crée un warp à ta position"; }
        @Override public String category() { return "admin"; }
        @Override public boolean playerOnly() { return true; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            Player p = (Player) s;
            if (a.length < 1) { p.sendMessage(Text.mm("<red>Usage : /moon setwarp <nom>")); return; }
            String n = a[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
            if (n.isBlank()) { p.sendMessage(Text.mm("<red>Nom invalide.")); return; }
            warps.put(n, p.getLocation());
            saveWarps();
            p.sendMessage(Text.mm("<green>Warp <white>" + n + "</white> créé."));
        }
    }

    private final class DelWarp implements SubCommand {
        @Override public String name() { return "delwarp"; }
        @Override public String permission() { return "mooncore.admin.warp"; }
        @Override public String description() { return "(admin) supprime un warp"; }
        @Override public String category() { return "admin"; }
        @Override public void execute(MoonCore pl, CommandSender s, String[] a) {
            if (a.length < 1) { s.sendMessage(Text.mm("<red>Usage : /moon delwarp <nom>")); return; }
            if (warps.remove(a[0].toLowerCase(Locale.ROOT)) != null) { saveWarps(); s.sendMessage(Text.mm("<green>Warp supprimé.")); }
            else s.sendMessage(Text.mm("<red>Warp inconnu."));
        }
        @Override public List<String> tabComplete(MoonCore pl, CommandSender s, String[] a) {
            if (a.length == 1) {
                String pre = a[0].toLowerCase(Locale.ROOT);
                return warps.keySet().stream().filter(n -> n.startsWith(pre)).toList();
            }
            return List.of();
        }
    }
}
