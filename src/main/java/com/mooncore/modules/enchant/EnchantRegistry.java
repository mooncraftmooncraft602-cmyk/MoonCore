package com.mooncore.modules.enchant;

import com.mooncore.api.enchant.EnchantTarget;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Définit les 30 enchantements custom et leurs effets. Les valeurs d'équilibrage viennent
 * de {@link EnchantMath} ; les déclencheurs spéciaux (Dash, Double Saut) sont gérés par
 * {@link EnchantListener}.
 */
public final class EnchantRegistry {

    private EnchantRegistry() {}

    public static List<CustomEnchant> build(EnchantManagerModule mgr) {
        List<CustomEnchant> list = new ArrayList<>();

        // ===== Armes de mêlée =====
        list.add(CustomEnchant.builder("vampirisme", "Vampirisme", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Soigne en infligeant des dégâts")
                .melee((att, vic, lvl, e) -> heal(att, EnchantMath.vampHeal(lvl, e.getFinalDamage()))).build());

        list.add(CustomEnchant.builder("execution", "Exécution", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Dégâts accrus sur cible affaiblie")
                .melee((att, vic, lvl, e) -> e.setDamage(e.getDamage()
                        + EnchantMath.executeBonus(lvl, vic.getHealth() / maxHp(vic), e.getDamage()))).build());

        list.add(CustomEnchant.builder("berserker", "Berserker", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Plus puissant à bas PV")
                .melee((att, vic, lvl, e) -> e.setDamage(e.getDamage()
                        + EnchantMath.berserkBonus(lvl, att.getHealth() / maxHp(att), e.getDamage()))).build());

        list.add(CustomEnchant.builder("brise-armure", "Brise-Armure", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Ignore une partie de l'armure")
                .melee((att, vic, lvl, e) -> e.setDamage(e.getDamage()
                        + EnchantMath.flatBonus(lvl, e.getDamage(), 0.10))).build());

        list.add(CustomEnchant.builder("saignement", "Saignement", EnchantTarget.MELEE_WEAPON)
                .maxLevel(3).description("Inflige des dégâts dans le temps")
                .melee((att, vic, lvl, e) -> mgr.applyBleed(vic, EnchantMath.bleedPerTick(lvl), 3 + lvl, 20)).build());

        list.add(CustomEnchant.builder("poison", "Poison", EnchantTarget.MELEE_WEAPON)
                .maxLevel(3).description("Empoisonne la cible")
                .melee((att, vic, lvl, e) -> vic.addPotionEffect(
                        new PotionEffect(PotionEffectType.POISON, 40 + 20 * lvl, lvl - 1))).build());

        list.add(CustomEnchant.builder("coup-critique", "Coup Critique", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Chance de coup critique")
                .melee((att, vic, lvl, e) -> {
                    if (ThreadLocalRandom.current().nextDouble() < EnchantMath.critChance(lvl)) {
                        e.setDamage(e.getDamage() * EnchantMath.CRIT_MULTIPLIER);
                    }
                }).build());

        list.add(CustomEnchant.builder("chasseur-de-boss", "Chasseur de Boss", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Dégâts accrus contre les boss")
                .melee((att, vic, lvl, e) -> {
                    if (mgr.isBoss(vic)) e.setDamage(e.getDamage() + EnchantMath.flatBonus(lvl, e.getDamage(), 0.20));
                }).build());

        list.add(CustomEnchant.builder("dragon-slayer", "Dragon Slayer", EnchantTarget.MELEE_WEAPON)
                .maxLevel(5).description("Dégâts accrus contre les créatures de l'End")
                .melee((att, vic, lvl, e) -> {
                    if (isEndCreature(vic.getType())) {
                        e.setDamage(e.getDamage() + EnchantMath.flatBonus(lvl, e.getDamage(), 0.20));
                    }
                }).build());

        // ===== Armure : défense =====
        list.add(CustomEnchant.builder("resilience", "Résilience", EnchantTarget.ARMOR)
                .maxLevel(5).description("Réduit les dégâts subis")
                .defense((def, lvl, e) -> e.setDamage(EnchantMath.applyReduction(e.getDamage(),
                        EnchantMath.resilienceReduction(lvl)))).build());

        list.add(CustomEnchant.builder("epines-avancees", "Épines Avancées", EnchantTarget.ARMOR)
                .maxLevel(3).description("Renvoie des dégâts à l'attaquant")
                .defense((def, lvl, e) -> {
                    if (e instanceof EntityDamageByEntityEvent edbe
                            && edbe.getDamager() instanceof LivingEntity att && !att.equals(def)) {
                        att.damage(EnchantMath.thornsReflect(lvl, e.getDamage()), def);
                    }
                }).build());

        list.add(CustomEnchant.builder("anti-chute", "Anti-Chute", EnchantTarget.ARMOR)
                .maxLevel(1).description("Annule les dégâts de chute")
                .defense((def, lvl, e) -> {
                    if (e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true);
                }).build());

        list.add(CustomEnchant.builder("phoenix", "Phoenix", EnchantTarget.ARMOR)
                .maxLevel(1).description("Renaît une fois après un coup fatal")
                .defense((def, lvl, e) -> {
                    if (def.getHealth() - e.getFinalDamage() <= 0 && mgr.tryConsumePhoenix(def.getUniqueId())) {
                        e.setCancelled(true);
                        def.setHealth(Math.min(maxHp(def), maxHp(def) * 0.5));
                        def.setFireTicks(0);
                        def.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                        def.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
                        def.getWorld().strikeLightningEffect(def.getLocation());
                    }
                }).build());

        // ===== Armure : passifs (potions rafraîchies) =====
        list.add(equipPotion("regeneration", "Régénération", EnchantTarget.ARMOR, 3,
                "Régénération continue", PotionEffectType.REGENERATION));
        list.add(equipPotion("endurance", "Endurance", EnchantTarget.ARMOR, 5,
                "Augmente les PV maximum", PotionEffectType.HEALTH_BOOST));
        list.add(equipPotion("absorption", "Absorption", EnchantTarget.ARMOR, 3,
                "Cœurs d'absorption", PotionEffectType.ABSORPTION));

        list.add(CustomEnchant.builder("anti-knockback", "Anti-Knockback", EnchantTarget.ARMOR)
                .maxLevel(4).description("Réduit le recul")
                .equip((p, lvl) -> {
                    var attr = p.getAttribute(com.mooncore.util.Attrs.KNOCKBACK_RESISTANCE);
                    if (attr != null) attr.setBaseValue(Math.min(1.0, 0.25 * lvl));
                }).build());

        list.add(CustomEnchant.builder("aura-de-protection", "Aura de Protection", EnchantTarget.ARMOR)
                .maxLevel(3).description("Protège les alliés proches")
                .equip((p, lvl) -> {
                    for (Player ally : p.getLocation().getNearbyPlayers(6)) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, lvl - 1, true, false, false));
                    }
                }).build());

        list.add(CustomEnchant.builder("titan", "Titan", EnchantTarget.ARMOR)
                .maxLevel(3).description("Buffs massifs d'armure lourde")
                .equip((p, lvl) -> {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 60, lvl, true, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false, false));
                }).build());

        // ===== Bottes : mouvement =====
        list.add(equipPotion("agilite", "Agilité", EnchantTarget.BOOTS, 3,
                "Vitesse accrue", PotionEffectType.SPEED));

        list.add(CustomEnchant.builder("double-saut", "Double Saut", EnchantTarget.BOOTS)
                .maxLevel(1).description("Permet un second saut")
                .equip((p, lvl) -> {
                    // Ne ré-autorise le saut qu'au sol → un seul saut supplémentaire en l'air.
                    if (onSolidGround(p) && (p.getGameMode() == org.bukkit.GameMode.SURVIVAL
                            || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)) {
                        p.setAllowFlight(true);
                    }
                }).build());

        list.add(CustomEnchant.builder("dash", "Dash", EnchantTarget.BOOTS)
                .maxLevel(3).description("Bond en avant (touche échange de main)").build());

        // ===== Casque : ultime =====
        list.add(CustomEnchant.builder("eclipse", "Éclipse", EnchantTarget.HELMET)
                .maxLevel(3).description("Aura d'obscurité dévastatrice")
                .equip((p, lvl) -> {
                    for (var ent : p.getNearbyEntities(5, 5, 5)) {
                        if (ent instanceof Monster m) {
                            m.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
                            m.damage(lvl * 2.0, p);
                        }
                    }
                }).build());

        // ===== Outils : minage =====
        list.add(CustomEnchant.builder("excavation", "Excavation", EnchantTarget.MINING_TOOL)
                .maxLevel(3).description("Minage accéléré")
                .equip((p, lvl) -> p.addPotionEffect(
                        new PotionEffect(PotionEffectType.HASTE, 40, lvl - 1, true, false, false))).build());

        list.add(CustomEnchant.builder("prospection", "Prospection", EnchantTarget.PICKAXE)
                .maxLevel(5).description("Chance de minerais bonus")
                .mining((m, b, lvl, e) -> {
                    if (isOre(b.getType()) && ThreadLocalRandom.current().nextDouble() < EnchantMath.prospectingChance(lvl)) {
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(b.getType()));
                    }
                }).build());

        list.add(CustomEnchant.builder("bucheron", "Bûcheron", EnchantTarget.AXE)
                .maxLevel(1).description("Abat l'arbre entier")
                .mining((m, b, lvl, e) -> {
                    if (isLog(b.getType())) chainBreak(b, EnchantRegistry::isLog, 80);
                }).build());

        list.add(CustomEnchant.builder("vein-miner", "Vein Miner", EnchantTarget.PICKAXE)
                .maxLevel(1).description("Mine la veine entière")
                .mining((m, b, lvl, e) -> {
                    if (isOre(b.getType())) {
                        Material type = b.getType();
                        chainBreak(b, mat -> mat == type, 64);
                    }
                }).build());

        list.add(CustomEnchant.builder("tresor-cache", "Trésor Caché", EnchantTarget.MINING_TOOL)
                .maxLevel(5).description("Chance de trouver de l'argent")
                .mining((m, b, lvl, e) -> {
                    if (ThreadLocalRandom.current().nextDouble() < EnchantMath.treasureChance(lvl)) {
                        mgr.depositMoney(m.getUniqueId(), EnchantMath.treasureMoney(lvl), "enchant:tresor-cache");
                    }
                }).build());

        list.add(CustomEnchant.builder("super-fortune", "Super Fortune", EnchantTarget.PICKAXE)
                .maxLevel(5).description("Multiplie les drops de minerais")
                .mining((m, b, lvl, e) -> {
                    if (isOre(b.getType())) {
                        for (int i = 0; i < EnchantMath.superFortuneExtra(lvl); i++) {
                            b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(b.getType()));
                        }
                    }
                }).build());

        list.add(CustomEnchant.builder("auto-smelt", "Auto Smelt", EnchantTarget.PICKAXE)
                .maxLevel(1).description("Fond automatiquement les minerais")
                .mining((m, b, lvl, e) -> {
                    Material smelted = smelt(b.getType());
                    if (smelted != null) {
                        e.setDropItems(false);
                        b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(smelted));
                    }
                }).build());

        return list;
    }

    // ---- Effets passifs génériques ----

    private static CustomEnchant equipPotion(String id, String name, EnchantTarget target, int maxLevel,
                                             String desc, PotionEffectType type) {
        return CustomEnchant.builder(id, name, target).maxLevel(maxLevel).description(desc)
                .equip((p, lvl) -> p.addPotionEffect(
                        new PotionEffect(type, 60, lvl - 1, true, false, false)))
                .build();
    }

    // ---- Helpers ----

    private static void heal(LivingEntity e, double amount) {
        e.setHealth(Math.min(maxHp(e), e.getHealth() + amount));
    }

    /** Vérifie côté serveur que le joueur repose sur un bloc solide (remplace isOnGround). */
    private static boolean onSolidGround(Player p) {
        return p.getLocation().clone().subtract(0, 0.1, 0).getBlock().getType().isSolid();
    }

    private static double maxHp(LivingEntity e) {
        var attr = e.getAttribute(com.mooncore.util.Attrs.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    private static boolean isEndCreature(EntityType t) {
        return t == EntityType.ENDER_DRAGON || t == EntityType.SHULKER
                || t == EntityType.ENDERMAN || t == EntityType.ENDERMITE;
    }

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private static boolean isLog(Material m) {
        String n = m.name();
        return n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE");
    }

    private static Material smelt(Material ore) {
        return switch (ore) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    /** Casse en chaîne les blocs connectés validant {@code accept}, jusqu'à {@code max}. */
    private static void chainBreak(Block origin, java.util.function.Predicate<Material> accept, int max) {
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);
        int broken = 0;
        while (!queue.isEmpty() && broken < max) {
            Block b = queue.poll();
            if (b != origin) {
                b.breakNaturally();
                broken++;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block rel = b.getRelative(dx, dy, dz);
                        if (!visited.contains(rel) && accept.test(rel.getType())) {
                            visited.add(rel);
                            queue.add(rel);
                        }
                    }
                }
            }
        }
    }
}
