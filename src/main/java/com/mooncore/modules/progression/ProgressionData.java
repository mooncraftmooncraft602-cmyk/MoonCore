package com.mooncore.modules.progression;

import java.util.UUID;

/** État de progression d'un joueur en mémoire (XP + tier courant). */
public final class ProgressionData {

    private final UUID uuid;
    private long xp;
    private int tier;
    private volatile boolean dirty;

    public ProgressionData(UUID uuid, long xp, int tier) {
        this.uuid = uuid;
        this.xp = xp;
        this.tier = tier;
    }

    public long addXp(long amount) {
        this.xp = Math.max(0, this.xp + amount);
        this.dirty = true;
        return this.xp;
    }

    public void setXp(long xp) { this.xp = Math.max(0, xp); this.dirty = true; }
    public void setTier(int tier) { this.tier = tier; this.dirty = true; }

    public UUID uuid() { return uuid; }
    public long xp() { return xp; }
    public int tier() { return tier; }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }
}
