package com.mooncore.modules.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;

/** Un home : nom + position. Données pures (pas de référence World directe). */
public record Home(String name, String world, double x, double y, double z, float yaw, float pitch) {

    public static Home of(String name, Location loc) {
        return new Home(name, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    /** Location Bukkit, ou null si le monde n'est pas chargé. */
    public Location toLocation() {
        var w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }
}
