package com.mooncore.modules.boss;

/**
 * Une capacité configurée dans une phase : type, cooldown (ticks), magnitude (sens selon
 * le type), nombre (ex. sbires), et rayon d'effet.
 */
public record AbilityInstance(BossAbilityType type, long cooldownTicks, double magnitude,
                              int count, double radius) {
    public AbilityInstance {
        if (cooldownTicks < 1) cooldownTicks = 20;
        if (radius <= 0) radius = 8;
        if (count < 1) count = 1;
    }
}
