package com.mooncore.modules.enchant;

/**
 * Formules d'équilibrage des enchantements. Logique pure et testable (aucune dépendance
 * Bukkit), pour valider l'échelonnage par niveau sans serveur.
 */
public final class EnchantMath {

    private EnchantMath() {}

    /** Vampirisme : soin = 10 % des dégâts par niveau. */
    public static double vampHeal(int level, double damage) {
        return Math.max(0, damage) * 0.10 * level;
    }

    /** Exécution : +50 % de dégâts par niveau si la cible est sous 25 % de PV. */
    public static double executeBonus(int level, double victimHpFraction, double baseDamage) {
        return victimHpFraction < 0.25 ? baseDamage * 0.5 * level : 0;
    }

    /** Berserker : +15 % par niveau quand l'attaquant est sous 40 % de PV. */
    public static double berserkBonus(int level, double attackerHpFraction, double baseDamage) {
        return attackerHpFraction < 0.40 ? baseDamage * 0.15 * level : 0;
    }

    /** Coup critique : 10 % de chance par niveau (plafonné à 90 %). */
    public static double critChance(int level) {
        return Math.min(0.90, 0.10 * level);
    }

    public static final double CRIT_MULTIPLIER = 1.5;

    /** Brise-Armure / Chasseur de Boss / Dragon Slayer : bonus de dégâts proportionnel. */
    public static double flatBonus(int level, double baseDamage, double perLevel) {
        return baseDamage * perLevel * level;
    }

    /** Résilience : réduction des dégâts (6 % par niveau, plafonnée à 60 %). */
    public static double resilienceReduction(int level) {
        return Math.min(0.60, 0.06 * level);
    }

    /** Épines Avancées : dégâts renvoyés (25 % des dégâts subis par niveau). */
    public static double thornsReflect(int level, double damage) {
        return Math.max(0, damage) * 0.25 * level;
    }

    /** Saignement : dégâts par tick d'effet. */
    public static double bleedPerTick(int level) {
        return 1.0 + 0.5 * level;
    }

    /** Prospection : probabilité de drop bonus par niveau. */
    public static double prospectingChance(int level) {
        return Math.min(1.0, 0.10 * level);
    }

    /** Super Fortune : nombre de drops supplémentaires. */
    public static int superFortuneExtra(int level) {
        return level;
    }

    /** Trésor Caché : probabilité de trouver un trésor. */
    public static double treasureChance(int level) {
        return Math.min(1.0, 0.02 * level);
    }

    /** Trésor Caché : montant trouvé. */
    public static double treasureMoney(int level) {
        return 50.0 * level;
    }

    /** Applique une réduction (fraction 0..1) à un montant de dégâts. */
    public static double applyReduction(double damage, double reductionFraction) {
        return damage * (1.0 - Math.max(0, Math.min(1, reductionFraction)));
    }
}
