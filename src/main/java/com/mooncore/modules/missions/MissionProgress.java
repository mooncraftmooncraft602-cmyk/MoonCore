package com.mooncore.modules.missions;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Progression des missions d'un joueur pour la période courante. Pure et testable.
 */
public final class MissionProgress {

    private final Map<String, Integer> counts = new ConcurrentHashMap<>();
    private final Set<String> claimed = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;

    public int count(String missionId) {
        return counts.getOrDefault(missionId, 0);
    }

    /** Incrémente la progression sans dépasser la cible. Retourne la nouvelle valeur. */
    @SuppressWarnings("null")
    public int add(String missionId, int amount, int target) {
        int updated = counts.merge(missionId, amount, Integer::sum);
        if (updated > target) {
            updated = target;
            counts.put(missionId, target);
        }
        dirty = true;
        return updated;
    }

    public void set(String missionId, int value) {
        counts.put(missionId, value);
        dirty = true;
    }

    public boolean isClaimed(String missionId) {
        return claimed.contains(missionId);
    }

    public void setClaimed(String missionId) {
        claimed.add(missionId);
        dirty = true;
    }

    public Map<String, Integer> counts() { return counts; }
    public Set<String> claimedSet() { return claimed; }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }
}
