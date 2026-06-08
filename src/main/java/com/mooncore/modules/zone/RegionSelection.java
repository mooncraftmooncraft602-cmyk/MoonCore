package com.mooncore.modules.zone;

import org.bukkit.Location;

/** Sélection de deux coins par un joueur (via la baguette ou des commandes). */
public final class RegionSelection {

    private Location pos1;
    private Location pos2;

    public void setPos1(Location loc) { this.pos1 = loc; }
    public void setPos2(Location loc) { this.pos2 = loc; }

    public Location pos1() { return pos1; }
    public Location pos2() { return pos2; }

    public boolean isComplete() {
        return pos1 != null && pos2 != null
                && pos1.getWorld() != null
                && pos1.getWorld().equals(pos2.getWorld());
    }
}
