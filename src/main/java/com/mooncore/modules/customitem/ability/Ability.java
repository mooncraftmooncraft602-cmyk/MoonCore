package com.mooncore.modules.customitem.ability;

import com.mooncore.api.customitem.AbilityKind;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Capacité d'objet. Une capacité ACTIVE possède un handler déclenché au clic droit
 * (soumis à un cooldown). Une capacité PASSIVE peut soit n'avoir aucun handler (effet
 * « tag » appliqué en dur par {@code CustomItemListener}, ex. {@code fortune}), soit
 * porter un ou plusieurs <b>hooks</b> déclenchés par des événements :
 * <ul>
 *   <li>{@link HitHandler} — quand le porteur frappe une entité (arme) ;</li>
 *   <li>{@link DefendHandler} — quand le porteur subit des dégâts (armure) ;</li>
 *   <li>{@link MineHandler} — quand le porteur casse un bloc (outil).</li>
 * </ul>
 * Les hooks rendent le système extensible sans toucher au cœur du listener.
 * <p>
 * Compat Bedrock : tous les déclencheurs sont des événements serveur (clic droit, dégâts,
 * minage) relayés par Geyser ; aucune capacité ne dépend du rendu client.
 */
public final class Ability {

    @FunctionalInterface
    public interface ActiveHandler {
        /** Exécuté sur le thread principal au clic droit. */
        void cast(Player caster, int level);
    }

    @FunctionalInterface
    public interface HitHandler {
        /** Le porteur (arme) frappe {@code victim}. Peut muter {@code e.setDamage(...)}. */
        void onHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, int level);
    }

    @FunctionalInterface
    public interface DefendHandler {
        /** Le porteur (armure/arme tenue) subit des dégâts. */
        void onDefend(Player victim, EntityDamageEvent e, int level);
    }

    @FunctionalInterface
    public interface MineHandler {
        /** Le porteur (outil) casse {@code block}. */
        void onMine(Player miner, Block block, BlockBreakEvent e, int level);
    }

    /** Catégorie pour le tri/affichage et les prompts IA. */
    public enum Category { BASIC, SWORD_ACTIVE, SWORD_PASSIVE, ARMOR, TOOL }

    private final String id;
    private final String displayName;
    private final AbilityKind kind;
    private final String description;
    private final long baseCooldownMs;
    private final Category category;
    /** Si vrai, l'IA ne l'ajoute QUE si l'admin le demande explicitement (puissant/opt-in). */
    private final boolean special;
    private final ActiveHandler handler;     // null sauf ACTIVE
    private final HitHandler hitHandler;     // null sauf passif on-hit
    private final DefendHandler defendHandler; // null sauf passif on-defend
    private final MineHandler mineHandler;   // null sauf passif on-mine

    private Ability(String id, String displayName, AbilityKind kind, String description,
                    long baseCooldownMs, Category category, boolean special,
                    ActiveHandler handler, HitHandler hitHandler,
                    DefendHandler defendHandler, MineHandler mineHandler) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
        this.description = description;
        this.baseCooldownMs = baseCooldownMs;
        this.category = category;
        this.special = special;
        this.handler = handler;
        this.hitHandler = hitHandler;
        this.defendHandler = defendHandler;
        this.mineHandler = mineHandler;
    }

    // ---- Fabriques de base (rétro-compatibles) ----

    public static Ability active(String id, String displayName, String description,
                                 long cooldownMs, ActiveHandler handler) {
        return new Ability(id, displayName, AbilityKind.ACTIVE, description, cooldownMs,
                Category.BASIC, false, handler, null, null, null);
    }

    public static Ability passive(String id, String displayName, String description) {
        return new Ability(id, displayName, AbilityKind.PASSIVE, description, 0L,
                Category.BASIC, false, null, null, null, null);
    }

    // ---- Fabriques « signature » (puissantes, opt-in) ----

    public static Ability activeSpecial(String id, String displayName, String description,
                                        long cooldownMs, Category category, ActiveHandler handler) {
        return new Ability(id, displayName, AbilityKind.ACTIVE, description, cooldownMs,
                category, true, handler, null, null, null);
    }

    public static Ability onHit(String id, String displayName, String description,
                                Category category, HitHandler hitHandler) {
        return new Ability(id, displayName, AbilityKind.PASSIVE, description, 0L,
                category, true, null, hitHandler, null, null);
    }

    public static Ability onDefend(String id, String displayName, String description,
                                   Category category, DefendHandler defendHandler) {
        return new Ability(id, displayName, AbilityKind.PASSIVE, description, 0L,
                category, true, null, null, defendHandler, null);
    }

    public static Ability onMine(String id, String displayName, String description,
                                 MineHandler mineHandler) {
        return new Ability(id, displayName, AbilityKind.PASSIVE, description, 0L,
                Category.TOOL, true, null, null, null, mineHandler);
    }

    // ---- Accès ----

    public String id() { return id; }
    public String displayName() { return displayName; }
    public AbilityKind kind() { return kind; }
    public String description() { return description; }
    public long baseCooldownMs() { return baseCooldownMs; }
    public Category category() { return category; }
    public boolean special() { return special; }
    public boolean isActive() { return kind == AbilityKind.ACTIVE; }

    public HitHandler hitHandler() { return hitHandler; }
    public DefendHandler defendHandler() { return defendHandler; }
    public MineHandler mineHandler() { return mineHandler; }

    public void cast(Player caster, int level) {
        if (handler != null) handler.cast(caster, level);
    }

    /** Cooldown effectif après réduction (cooldown-reduction stat, en %). */
    public long cooldownMs(double cdrPercent) {
        double factor = Math.max(0.0, 1.0 - cdrPercent / 100.0);
        return (long) (baseCooldownMs * factor);
    }
}
