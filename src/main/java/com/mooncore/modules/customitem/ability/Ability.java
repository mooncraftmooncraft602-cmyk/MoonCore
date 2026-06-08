package com.mooncore.modules.customitem.ability;

import com.mooncore.api.customitem.AbilityKind;
import org.bukkit.entity.Player;

/**
 * Capacité d'objet. Une capacité ACTIVE possède un handler déclenché au clic droit
 * (soumis à un cooldown). Une capacité PASSIVE n'a pas de handler : son effet est
 * appliqué en continu par {@code CustomItemListener} tant que l'objet est tenu/porté.
 * <p>
 * Compat Bedrock : le déclencheur est {@code PlayerInteractEvent} (clic droit), qui
 * est correctement relayé par Geyser. Aucune capacité ne dépend du visuel client.
 */
public final class Ability {

    @FunctionalInterface
    public interface ActiveHandler {
        /** Exécuté sur le thread principal. */
        void cast(Player caster, int level);
    }

    private final String id;
    private final String displayName;
    private final AbilityKind kind;
    private final String description;
    private final long baseCooldownMs;
    private final ActiveHandler handler; // null pour les passives

    private Ability(String id, String displayName, AbilityKind kind, String description,
                    long baseCooldownMs, ActiveHandler handler) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
        this.description = description;
        this.baseCooldownMs = baseCooldownMs;
        this.handler = handler;
    }

    public static Ability active(String id, String displayName, String description,
                                 long cooldownMs, ActiveHandler handler) {
        return new Ability(id, displayName, AbilityKind.ACTIVE, description, cooldownMs, handler);
    }

    public static Ability passive(String id, String displayName, String description) {
        return new Ability(id, displayName, AbilityKind.PASSIVE, description, 0L, null);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public AbilityKind kind() { return kind; }
    public String description() { return description; }
    public long baseCooldownMs() { return baseCooldownMs; }
    public boolean isActive() { return kind == AbilityKind.ACTIVE; }

    public void cast(Player caster, int level) {
        if (handler != null) handler.cast(caster, level);
    }

    /** Cooldown effectif après réduction (cooldown-reduction stat, en %). */
    public long cooldownMs(double cdrPercent) {
        double factor = Math.max(0.0, 1.0 - cdrPercent / 100.0);
        return (long) (baseCooldownMs * factor);
    }
}
