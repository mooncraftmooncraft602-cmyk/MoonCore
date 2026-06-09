package com.mooncore.modules.rtp;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Remplace le plugin <b>Simple_RTP</b> : {@code /moon rtp} téléporte le joueur à une position
 * ALÉATOIRE et SÛRE de son monde (anneau {@code min}→{@code radius} autour de 0,0), avec
 * cooldown. Évite lave/eau/vide. Config : {@code modules/rtp.yml} (radius, min, cooldown-seconds).
 */
@ModuleInfo(id = "rtp", name = "RandomTeleport")
public final class RtpModule extends AbstractModule implements SubCommand {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private int radius, min, cooldownSeconds;

    @Override
    protected void onEnable() {
        load();
        plugin().rootCommand().register(this);
    }

    @Override protected void onDisable() { cooldowns.clear(); }
    @Override protected void onReload() { reloadModuleConfig(); load(); }

    private void load() {
        radius = Math.max(100, moduleConfig().getInt("radius", 5000));
        min = Math.max(0, Math.min(radius - 1, moduleConfig().getInt("min", 500)));
        cooldownSeconds = Math.max(0, moduleConfig().getInt("cooldown-seconds", 60));
    }

    @Override public String name() { return "rtp"; }
    @Override public java.util.List<String> aliases() { return java.util.List.of("wild", "randomtp"); }
    @Override public String permission() { return "mooncore.rtp.use"; }
    @Override public String description() { return "Téléportation aléatoire sûre dans le monde"; }
    @Override public String category() { return "player"; }
    @Override public boolean playerOnly() { return true; }

    @Override
    public void execute(MoonCore plugin, CommandSender sender, String[] args) {
        Player p = (Player) sender;
        long now = System.currentTimeMillis();
        if (!p.hasPermission("mooncore.bypass.cooldown") && cooldownSeconds > 0) {
            Long last = cooldowns.get(p.getUniqueId());
            if (last != null && now - last < cooldownSeconds * 1000L) {
                long rem = (cooldownSeconds * 1000L - (now - last)) / 1000L + 1;
                p.sendMessage(Text.mm("<red>Patiente <white>" + rem + "s</white> avant un nouveau /rtp."));
                return;
            }
        }
        Location dest = findSafe(p.getWorld());
        if (dest == null) {
            p.sendMessage(Text.mm("<red>Aucun point sûr trouvé, réessaie."));
            return;
        }
        cooldowns.put(p.getUniqueId(), now);
        p.sendMessage(Text.mm("<gray>Téléportation aléatoire…"));
        p.teleport(dest); // cause PLUGIN → frais EconomyBalancer éventuels
        p.sendMessage(Text.mm("<green>Arrivé en <white>" + dest.getBlockX() + ", " + dest.getBlockY()
                + ", " + dest.getBlockZ() + "</white>."));
    }

    /** Cherche une position de surface sûre (jusqu'à 24 essais) ; null si rien trouvé. */
    private Location findSafe(World w) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 24; i++) {
            int sign1 = rnd.nextBoolean() ? 1 : -1, sign2 = rnd.nextBoolean() ? 1 : -1;
            int x = sign1 * (min + rnd.nextInt(radius - min + 1));
            int z = sign2 * (min + rnd.nextInt(radius - min + 1));
            int y = w.getHighestBlockYAt(x, z);
            if (y <= w.getMinHeight() + 1 || y >= w.getMaxHeight() - 2) continue;
            Material ground = w.getBlockAt(x, y, z).getType();
            Material feet = w.getBlockAt(x, y + 1, z).getType();
            Material head = w.getBlockAt(x, y + 2, z).getType();
            if (!ground.isSolid() || ground == Material.LAVA) continue;          // sol fiable
            if (ground == Material.WATER) continue;                              // pas en pleine eau
            if (!feet.isAir() || !head.isAir()) continue;                        // espace dégagé
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }
}
