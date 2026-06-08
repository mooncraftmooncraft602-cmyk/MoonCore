package com.mooncore.modules.customitem.ability;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/**
 * Garde-fous anti-récursion partagés entre les hooks de capacités et le
 * {@code CustomItemListener}. Quand une capacité inflige des dégâts secondaires
 * (fendoir, chaîne d'éclairs, riposte…) ou casse des blocs en chaîne (3x3, filon…),
 * elle pose un drapeau sur l'entité concernée ; le listener teste ce drapeau en tête de
 * ses handlers et s'arrête immédiatement → aucun effet d'arme/outil ne se ré-applique en
 * boucle. Les drapeaux vivent dans la PDC (mono-thread, posés/retirés dans le même tick).
 */
public final class AbilityGuard {

    private static final NamespacedKey ATTACK = NamespacedKey.fromString("mooncore:atk_reentry");
    private static final NamespacedKey DEFEND = NamespacedKey.fromString("mooncore:def_reentry");
    private static final NamespacedKey MINE = NamespacedKey.fromString("mooncore:mine_reentry");

    private AbilityGuard() {}

    public static boolean attacking(Entity e) { return has(e, ATTACK); }
    public static void setAttacking(Entity e) { set(e, ATTACK); }
    public static void clearAttacking(Entity e) { clear(e, ATTACK); }

    public static boolean defending(Entity e) { return has(e, DEFEND); }
    public static void setDefending(Entity e) { set(e, DEFEND); }
    public static void clearDefending(Entity e) { clear(e, DEFEND); }

    public static boolean mining(Entity e) { return has(e, MINE); }
    public static void setMining(Entity e) { set(e, MINE); }
    public static void clearMining(Entity e) { clear(e, MINE); }

    /**
     * Inflige des dégâts d'une CAPACITÉ en posant le drapeau « attaque » : le coup passe par
     * le pipeline de dégâts (armure, résistance, totem, crédit du tueur) MAIS sans re-déclencher
     * les passifs d'arme (crit, multiplicateurs, on-hit) — sinon une capacité re-multiplierait
     * ses propres dégâts. À utiliser pour TOUTE attaque issue d'une capacité (active ou DoT).
     */
    public static void damage(org.bukkit.entity.Player source,
                              org.bukkit.entity.LivingEntity target, double amount) {
        setAttacking(source);
        try { target.damage(amount, source); }
        finally { clearAttacking(source); }
    }

    private static boolean has(Entity e, NamespacedKey k) {
        return e.getPersistentDataContainer().has(k, PersistentDataType.BYTE);
    }
    private static void set(Entity e, NamespacedKey k) {
        e.getPersistentDataContainer().set(k, PersistentDataType.BYTE, (byte) 1);
    }
    private static void clear(Entity e, NamespacedKey k) {
        e.getPersistentDataContainer().remove(k);
    }
}
