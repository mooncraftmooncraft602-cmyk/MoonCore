package com.mooncore.api.zone;

import org.bukkit.Location;

import java.util.EnumMap;
import java.util.Map;

/**
 * Région cuboïde nommée portant un jeu de {@link ZoneFlag}. Données pures, sans logique
 * Bukkit lourde — l'indexation et l'application sont gérées par le module Zone.
 */
public final class Region {

    private final String name;
    private final String world;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private int priority;
    private final EnumMap<ZoneFlag, Boolean> flags = new EnumMap<>(ZoneFlag.class);

    public Region(String name, String world,
                  int x1, int y1, int z1, int x2, int y2, int z2, int priority) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.priority = priority;
    }

    public boolean contains(String world, int x, int y, int z) {
        return this.world.equals(world)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        return contains(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /** Valeur d'un flag pour cette région, ou {@code null} si non défini ici. */
    public Boolean flag(ZoneFlag flag) {
        return flags.get(flag);
    }

    public void setFlag(ZoneFlag flag, Boolean value) {
        if (value == null) flags.remove(flag);
        else flags.put(flag, value);
    }

    public Map<ZoneFlag, Boolean> flags() {
        return flags;
    }

    // ---- Accesseurs ----
    public String name() { return name; }
    public String world() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public int priority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public int minChunkX() { return minX >> 4; }
    public int maxChunkX() { return maxX >> 4; }
    public int minChunkZ() { return minZ >> 4; }
    public int maxChunkZ() { return maxZ >> 4; }
}
