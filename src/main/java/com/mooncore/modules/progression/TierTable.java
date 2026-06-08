package com.mooncore.modules.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Barème de tiers : seuils d'XP cumulés, déblocages et récompense par palier. Logique pure
 * et testable (aucune dépendance Bukkit).
 */
public final class TierTable {

    /** Un palier : {@code xpRequired} = XP total cumulé pour atteindre ce tier. */
    public record Tier(int level, long xpRequired, Set<String> unlocks, String rewardId) {}

    private final List<Tier> tiers; // triés par niveau croissant

    public TierTable(List<Tier> tiers) {
        List<Tier> sorted = new ArrayList<>(tiers);
        sorted.sort(Comparator.comparingInt(Tier::level));
        if (sorted.isEmpty()) {
            sorted.add(new Tier(1, 0, Set.of(), null));
        }
        this.tiers = List.copyOf(sorted);
    }

    public int maxLevel() {
        return tiers.get(tiers.size() - 1).level();
    }

    public int minLevel() {
        return tiers.get(0).level();
    }

    /** Tier correspondant à un total d'XP (le plus haut palier atteint). */
    public int tierForXp(long totalXp) {
        int result = tiers.get(0).level();
        for (Tier t : tiers) {
            if (totalXp >= t.xpRequired()) result = t.level();
            else break;
        }
        return result;
    }

    /** Seuil d'XP du tier suivant celui donné, ou -1 si déjà au maximum. */
    public long nextTierXp(int currentLevel) {
        for (Tier t : tiers) {
            if (t.level() > currentLevel) return t.xpRequired();
        }
        return -1;
    }

    public Tier tier(int level) {
        for (Tier t : tiers) if (t.level() == level) return t;
        return null;
    }

    /** Ensemble des fonctionnalités débloquées jusqu'au tier donné (inclus). */
    public Set<String> unlocksUpTo(int level) {
        Set<String> all = new HashSet<>();
        for (Tier t : tiers) {
            if (t.level() <= level) all.addAll(t.unlocks());
        }
        return all;
    }

    public boolean isUnlocked(int level, String feature) {
        for (Tier t : tiers) {
            if (t.level() <= level && t.unlocks().contains(feature)) return true;
        }
        return false;
    }

    public List<Tier> tiers() {
        return tiers;
    }
}
