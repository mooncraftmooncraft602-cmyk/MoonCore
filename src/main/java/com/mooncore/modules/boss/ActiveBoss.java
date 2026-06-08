package com.mooncore.modules.boss;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Instance vivante d'un boss : entité, définition, suivi des dégâts, barre, cooldowns. */
public final class ActiveBoss {

    private final LivingEntity entity;
    private final BossDefinition definition;
    private final BossBar bar;
    private final Map<UUID, Double> damageByPlayer = new ConcurrentHashMap<>();
    private final Map<BossAbilityType, Long> lastUseTick = new ConcurrentHashMap<>();
    private volatile String currentPhase = "";

    public ActiveBoss(LivingEntity entity, BossDefinition definition, BossBar bar) {
        this.entity = entity;
        this.definition = definition;
        this.bar = bar;
    }

    @SuppressWarnings("null")
    public void addDamage(UUID player, double amount) {
        damageByPlayer.merge(player, amount, Double::sum);
    }

    public UUID topDamager() {
        return damageByPlayer.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public boolean canUse(BossAbilityType type, long currentTick, long cooldownTicks) {
        long last = lastUseTick.getOrDefault(type, Long.MIN_VALUE);
        return currentTick - last >= cooldownTicks;
    }

    public void markUsed(BossAbilityType type, long currentTick) {
        lastUseTick.put(type, currentTick);
    }

    public String currentPhase() { return currentPhase; }
    public void setCurrentPhase(String phase) { this.currentPhase = phase; }

    public LivingEntity entity() { return entity; }
    public BossDefinition definition() { return definition; }
    public BossBar bar() { return bar; }
    public Map<UUID, Double> damageByPlayer() { return damageByPlayer; }
}
