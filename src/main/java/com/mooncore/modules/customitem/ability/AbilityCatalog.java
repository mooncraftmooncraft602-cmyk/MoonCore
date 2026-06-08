package com.mooncore.modules.customitem.ability;

import com.mooncore.MoonCore;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Catalogue des capacités « signature » : pouvoirs de magie noire <b>innés aux épées</b>
 * (actifs au clic droit + passifs au coup / en défense) et capacités d'outils
 * (minage 3x3, filon, fonte auto, abattage d'arbre, aimant, replantation…).
 * <p>
 * Toutes sont marquées {@code special} : l'IA ne les pose sur un objet QUE si l'admin les
 * demande explicitement. Implémentations Paper 1.21.1, sûres (effets via {@code Registry},
 * gardes anti-récursion via {@link AbilityGuard}), identiques Java/Bedrock.
 */
public final class AbilityCatalog {

    private AbilityCatalog() {}

    // PDC keys (état des capacités, posés sur joueurs/victimes/outils)
    private static final NamespacedKey FRENZY = NamespacedKey.fromString("mooncore:frenzy_until");
    private static final NamespacedKey SOUL_STACKS = NamespacedKey.fromString("mooncore:soul_stacks");
    private static final NamespacedKey SOUL_LAST = NamespacedKey.fromString("mooncore:soul_last");
    private static final NamespacedKey SECONDWIND = NamespacedKey.fromString("mooncore:secondwind_last");
    private static final NamespacedKey MINE_COMBO = NamespacedKey.fromString("mooncore:mine_combo");
    private static final NamespacedKey MINE_LAST = NamespacedKey.fromString("mooncore:mine_last");
    private static final NamespacedKey BLEED_STACKS = NamespacedKey.fromString("mooncore:bleed_stacks");
    private static final NamespacedKey BLEED_TICKS = NamespacedKey.fromString("mooncore:bleed_ticks");
    private static final NamespacedKey BLEED_LVL = NamespacedKey.fromString("mooncore:bleed_lvl");
    private static final NamespacedKey BLEED_SRC = NamespacedKey.fromString("mooncore:bleed_src");

    /** Cultures qu'on peut replanter sans risque (sol vérifié avant). */
    private static final Set<Material> REPLANTABLE = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    private static final Set<UUID> BLEEDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Recettes de fonte courantes (auto_smelt) — n'augmente jamais la quantité. */
    private static final Map<Material, Material> SMELT = Map.ofEntries(
            Map.entry(Material.RAW_IRON, Material.IRON_INGOT),
            Map.entry(Material.RAW_GOLD, Material.GOLD_INGOT),
            Map.entry(Material.RAW_COPPER, Material.COPPER_INGOT),
            Map.entry(Material.IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Material.IRON_INGOT),
            Map.entry(Material.GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT),
            Map.entry(Material.NETHER_GOLD_ORE, Material.GOLD_INGOT),
            Map.entry(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP),
            Map.entry(Material.SAND, Material.GLASS),
            Map.entry(Material.RED_SAND, Material.GLASS),
            Map.entry(Material.COBBLESTONE, Material.STONE),
            Map.entry(Material.NETHERRACK, Material.NETHER_BRICK),
            Map.entry(Material.CLAY_BALL, Material.BRICK));

    // ============================================================
    //  Enregistrement
    // ============================================================

    public static void register(AbilityRegistry reg, MoonCore plugin) {
        registerActives(reg, plugin);
        registerOnHit(reg, plugin);
        registerOnDefend(reg, plugin);
        registerTools(reg, plugin);
    }

    // ---------------- ACTIVES (clic droit) ----------------

    private static void registerActives(AbilityRegistry reg, MoonCore plugin) {
        Ability.Category C = Ability.Category.SWORD_ACTIVE;

        reg.register(Ability.activeSpecial("soul_reap", "Moisson des âmes",
                "Draine la vie des ennemis autour et se soigne", 9_000, C, (p, lvl) -> {
            double radius = 4 + lvl, per = 3 + 1.5 * lvl, cap = 6 + 2.0 * lvl, healed = 0;
            for (LivingEntity le : nearby(p.getLocation(), radius)) {
                if (!targetable(le, p)) continue;
                healed += Math.min(le.getHealth(), per) * 0.35;
                AbilityGuard.damage(p, le, per);
            }
            heal(p, Math.min(cap, healed));
            World w = p.getWorld();
            w.spawnParticle(Particle.SOUL, p.getLocation().add(0, 1, 0), 40, radius / 2, 1, radius / 2, 0.02);
            sound(p.getLocation(), "minecraft:entity.wither.shoot", 0.9f, 1.4f);
        }));

        reg.register(Ability.activeSpecial("shadow_step", "Pas de l'ombre",
                "Surgit derrière la cible visée et la frappe", 8_000, C, (p, lvl) -> {
            LivingEntity tgt = lookTarget(p, 16 + 2 * lvl);
            sound(p.getLocation(), "minecraft:entity.enderman.teleport", 1f, 0.8f);
            if (tgt == null) { p.setVelocity(p.getLocation().getDirection().multiply(0.9).setY(0.2)); return; }
            Vector back = tgt.getLocation().getDirection().setY(0);
            if (back.lengthSquared() < 1e-4) back = p.getLocation().getDirection().setY(0);
            Location dest = tgt.getLocation().clone().subtract(back.normalize());
            dest.setYaw(tgt.getLocation().getYaw());
            dest.setPitch(20);
            p.teleport(dest);
            AbilityGuard.damage(p, tgt, 2 + 1.0 * lvl);
            applyEffect(tgt, "weakness", 40, lvl);
            p.getWorld().spawnParticle(Particle.SMOKE, dest, 30, 0.3, 0.6, 0.3, 0.05);
            sound(dest, "minecraft:entity.enderman.teleport", 1f, 1.2f);
        }));

        reg.register(Ability.activeSpecial("void_rift", "Faille du néant",
                "Aspire les ennemis vers un point devant soi", 14_000, C, (p, lvl) -> {
            Location rift = p.getEyeLocation().add(p.getLocation().getDirection().multiply(5));
            World w = rift.getWorld();
            if (w == null) return;
            sound(rift, "minecraft:block.portal.ambient", 1.4f, 0.5f);
            for (int i = 0; i < 3; i++) plugin.schedulers().syncLater(() -> {
                w.spawnParticle(Particle.PORTAL, rift, 60, 0.4, 0.4, 0.4, 1.0);
                for (LivingEntity le : nearby(rift, 6)) {
                    if (!targetable(le, p)) continue;
                    Vector pull = rift.toVector().subtract(le.getLocation().toVector());
                    if (pull.lengthSquared() < 0.25) continue;
                    le.setVelocity(le.getVelocity().multiply(0.4)
                            .add(pull.normalize().multiply(Math.min(1.4, 0.6 + 0.15 * lvl))));
                    applyEffect(le, "slowness", 30, lvl);
                }
            }, i * 5L);
        }));

        reg.register(Ability.activeSpecial("cursed_nova", "Nova maudite",
                "Onde de putréfaction qui flétrit les ennemis (Wither)", 12_000, C, (p, lvl) -> {
            World w = p.getWorld();
            w.spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 50, (5 + lvl) / 2.0, 0.6, (5 + lvl) / 2.0, 0.05);
            sound(p.getLocation(), "minecraft:entity.wither.spawn", 0.9f, 1.6f);
            for (LivingEntity le : nearby(p.getLocation(), 5 + lvl)) {
                if (!targetable(le, p)) continue;
                AbilityGuard.damage(p, le, 2 + 1.0 * lvl);
                applyEffect(le, "wither", 60 + 20 * lvl, le instanceof Player ? Math.min(lvl, 2) : lvl);
            }
        }));

        reg.register(Ability.activeSpecial("phantom_volley", "Volée fantôme",
                "Salve de lames spectrales vers l'avant", 7_000, C, (p, lvl) -> {
            int shots = 3 + lvl;
            double dmg = 2.5 + 0.8 * lvl;
            Location eye = p.getEyeLocation();
            World w = eye.getWorld();
            if (w == null) return;
            Vector base = eye.getDirection();
            for (int s = 0; s < shots; s++) {
                double spread = Math.toRadians((s - (shots - 1) / 2.0) * 8);
                Vector dir = base.clone().rotateAroundY(spread).normalize();
                for (double t = 0; t < 14; t += 0.7)
                    w.spawnParticle(Particle.SOUL_FIRE_FLAME, eye.clone().add(dir.clone().multiply(t)), 1, 0, 0, 0, 0);
                RayTraceResult r = w.rayTraceEntities(eye, dir, 14, 0.6,
                        e -> e instanceof LivingEntity && !e.equals(p));
                if (r != null && r.getHitEntity() instanceof LivingEntity le && targetable(le, p)) AbilityGuard.damage(p, le, dmg);
            }
            sound(eye, "minecraft:entity.wither.shoot", 0.7f, 1.7f);
        }));

        reg.register(Ability.activeSpecial("doom_brand", "Sceau funeste",
                "Marque la cible : explosion de dégâts différée", 13_000, C, (p, lvl) -> {
            LivingEntity tgt = lookTarget(p, 18);
            if (tgt == null) { p.sendActionBar(Text.mm("<dark_gray>Aucune cible")); return; }
            UUID id = tgt.getUniqueId();
            sound(tgt.getLocation(), "minecraft:block.respawn_anchor.charge", 1f, 0.6f);
            plugin.schedulers().syncLater(() -> {
                Entity e = Bukkit.getEntity(id);
                if (!(e instanceof LivingEntity m) || m.isDead()) return;
                Location ml = m.getLocation();
                World mw = ml.getWorld();
                double boom = 5 + 2.0 * lvl;
                if (mw != null) {
                    mw.spawnParticle(Particle.SCULK_SOUL, ml.clone().add(0, 1, 0), 50, 1, 1, 1, 0.1);
                    mw.spawnParticle(Particle.EXPLOSION, ml, 1);
                }
                AbilityGuard.damage(p, m, boom);
                for (LivingEntity le : nearby(ml, 3)) {
                    if (le.equals(m) || !targetable(le, p)) continue;
                    AbilityGuard.damage(p, le, boom * 0.5);
                }
                sound(ml, "minecraft:entity.generic.explode", 1.2f, 0.9f);
            }, 60L);
        }));

        reg.register(Ability.activeSpecial("abyssal_chains", "Chaînes abyssales",
                "Enracine et ralentit les ennemis proches", 11_000, C, (p, lvl) -> {
            World w = p.getWorld();
            sound(p.getLocation(), "minecraft:block.chain.place", 1.2f, 0.6f);
            for (LivingEntity le : nearby(p.getLocation(), 5)) {
                if (!targetable(le, p)) continue;
                le.setVelocity(new Vector(0, -0.1, 0));
                applyEffect(le, "slowness", 50 + 15 * lvl, lvl + 2);
                applyEffect(le, "mining_fatigue", 50 + 15 * lvl, lvl);
                AbilityGuard.damage(p, le, 1 + 0.5 * lvl);
                w.spawnParticle(Particle.CRIT, le.getLocation().add(0, 0.2, 0), 15, 0.2, 0.1, 0.2, 0);
            }
        }));

        reg.register(Ability.activeSpecial("berserk_frenzy", "Frénésie sanguinaire",
                "Force + vitesse, mais on subit +15% de dégâts", 18_000, C, (p, lvl) -> {
            int dur = (6 + lvl) * 20;
            applyEffect(p, "strength", dur, lvl);
            applyEffect(p, "speed", dur, Math.min(lvl, 2));
            applyEffect(p, "haste", dur, 1);
            p.getPersistentDataContainer().set(FRENZY, PersistentDataType.LONG,
                    System.currentTimeMillis() + dur * 50L);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0,
                    new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.6f));
            sound(p.getLocation(), "minecraft:entity.ravager.roar", 1f, 1.3f);
        }));

        reg.register(Ability.activeSpecial("executioner_strike", "Coup du bourreau",
                "Coup amplifié par les PV manquants de la cible", 10_000, C, (p, lvl) -> {
            LivingEntity t = lookTarget(p, 5);
            if (t == null) { p.sendActionBar(Text.mm("<dark_gray>Aucune cible à portée")); return; }
            double tmax = maxHealth(t);
            double missing = (tmax - t.getHealth()) / tmax;
            double bonus = Math.min(0.6 * tmax, missing * missing * (6 + 2.0 * lvl));
            AbilityGuard.damage(p, t, 4 + 1.0 * lvl + bonus);
            World w = t.getWorld();
            if (w != null) w.spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.4);
            sound(t.getLocation(), "minecraft:entity.player.attack.crit", 1f, 0.6f);
        }));

        reg.register(Ability.activeSpecial("blood_pact", "Pacte de sang",
                "Sacrifie des PV pour une détonation en cône", 16_000, C, (p, lvl) -> {
            double cost = 3 + lvl;
            if (p.getHealth() <= cost + 1) { p.sendActionBar(Text.mm("<dark_red>Trop peu de vie")); return; }
            p.setHealth(p.getHealth() - cost);
            double dmg = (10 + 3.0 * lvl) + cost * 1.5;
            for (LivingEntity le : cone(p, 5, 0.4)) if (targetable(le, p)) AbilityGuard.damage(p, le, dmg);
            Location eye = p.getEyeLocation();
            World w = eye.getWorld();
            if (w != null) {
                Vector d = eye.getDirection();
                for (double t = 0; t < 5; t += 0.5)
                    w.spawnParticle(Particle.DUST, eye.clone().add(d.clone().multiply(t)), 6, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(Color.RED, 1.8f));
            }
            sound(eye, "minecraft:entity.warden.sonic_boom", 1f, 1.6f);
        }));

        reg.register(Ability.activeSpecial("reapers_harvest", "Récolte du faucheur",
                "Vague tournoyante qui balaie tout autour", 20_000, C, (p, lvl) -> {
            double maxR = 6 + lvl, per = 2 + 1.0 * lvl;
            sound(p.getLocation(), "minecraft:entity.wither.shoot", 1.2f, 0.5f);
            for (int i = 0; i < 4; i++) {
                int step = i;
                plugin.schedulers().syncLater(() -> {
                    double r = 3 + (maxR - 3) * (step / 3.0);
                    Location c = p.getLocation();
                    World w = c.getWorld();
                    if (w != null) for (double a = 0; a < 360; a += 15)
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME,
                                c.clone().add(Math.cos(Math.toRadians(a)) * r, 1, Math.sin(Math.toRadians(a)) * r), 1, 0, 0, 0, 0);
                    for (LivingEntity le : nearby(c, r)) {
                        if (!targetable(le, p)) continue;
                        if (le.getLocation().distance(c) < r - 2) continue;
                        AbilityGuard.damage(p, le, per);
                    }
                    sound(c, "minecraft:entity.player.attack.sweep", 1f, 0.7f);
                }, i * 3L);
            }
        }));

        reg.register(Ability.activeSpecial("eclipse", "Éclipse",
                "Aveugle les ennemis (Cécité + Ténèbres) et les révèle", 15_000, C, (p, lvl) -> {
            World w = p.getWorld();
            w.spawnParticle(Particle.SQUID_INK, p.getLocation().add(0, 1.2, 0), 80, (6 + lvl) / 2.0, 1, (6 + lvl) / 2.0, 0.02);
            sound(p.getLocation(), "minecraft:entity.elder_guardian.curse", 0.7f, 0.8f);
            for (LivingEntity le : nearby(p.getLocation(), 6 + lvl)) {
                if (!targetable(le, p)) continue;
                applyEffect(le, "blindness", 40 + 15 * lvl, 1);
                applyEffect(le, "darkness", 40 + 15 * lvl, lvl);
                applyEffect(le, "glowing", 40 + 15 * lvl, 1);
            }
        }));
    }

    // ---------------- PASSIFS ON-HIT (arme) ----------------

    private static void registerOnHit(AbilityRegistry reg, MoonCore plugin) {
        Ability.Category C = Ability.Category.SWORD_PASSIVE;

        reg.register(Ability.onHit("hemorrhage", "Hémorragie",
                "Vos coups font saigner la cible dans le temps", C, (att, victim, e, lvl) -> {
            var pdc = victim.getPersistentDataContainer();
            int stacks = Math.min(3 + lvl, pdc.getOrDefault(BLEED_STACKS, PersistentDataType.INTEGER, 0) + 1);
            pdc.set(BLEED_STACKS, PersistentDataType.INTEGER, stacks);
            pdc.set(BLEED_TICKS, PersistentDataType.INTEGER, 10);
            pdc.set(BLEED_LVL, PersistentDataType.INTEGER, lvl);
            pdc.set(BLEED_SRC, PersistentDataType.STRING, att.getUniqueId().toString());
            startBleed(plugin, victim);
        }));

        reg.register(Ability.onHit("execute", "Exécution",
                "Achève les proies sous 25% de vie (dégâts bonus)", C, (att, victim, e, lvl) -> {
            double ratio = victim.getHealth() / maxHealth(victim);
            if (ratio > Math.min(0.30, 0.20 + 0.02 * lvl)) return;
            double bonus = Math.min(1.20, 0.40 + 0.15 * lvl);
            e.setDamage(e.getDamage() * (1.0 + bonus));
            sound(victim.getLocation(), "minecraft:entity.player.attack.crit", 1f, 0.6f);
        }));

        reg.register(Ability.onHit("cleave", "Fendoir",
                "Chaque frappe entaille aussi les ennemis adjacents", C, (att, victim, e, lvl) -> {
            double splash = e.getDamage() * Math.min(0.60, 0.35 + 0.05 * lvl);
            AbilityGuard.setAttacking(att);
            try {
                for (LivingEntity le : nearby(victim.getLocation(), 3)) {
                    if (le.equals(victim) || !targetable(le, att)) continue;
                    le.damage(splash, att);
                }
            } finally { AbilityGuard.clearAttacking(att); }
            sound(victim.getLocation(), "minecraft:entity.player.attack.sweep", 1f, 0.8f);
        }));

        reg.register(Ability.onHit("frostbite", "Morsure du gel",
                "Ralentit, et frappe plus fort les cibles déjà gelées", C, (att, victim, e, lvl) -> {
            PotionEffectType slow = effect("slowness");
            if (slow != null && victim.hasPotionEffect(slow)) {
                e.setDamage(e.getDamage() * (1.0 + Math.min(0.75, 0.25 + 0.08 * lvl)));
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getEyeLocation(), 10, 0.2, 0.3, 0.2, 0.01);
                sound(victim.getLocation(), "minecraft:block.glass.break", 1f, 1.2f);
            }
            applyEffect(victim, "slowness", 40 + 10 * lvl, 1);
        }));

        reg.register(Ability.onHit("chain_lightning", "Chaîne d'éclairs",
                "Un arc électrique saute vers les ennemis voisins", C, (att, victim, e, lvl) -> {
            if (ThreadLocalRandom.current().nextDouble() > Math.min(0.50, 0.20 + 0.05 * lvl)) return;
            int maxJumps = Math.min(4, 1 + lvl / 2);
            double dmg = e.getDamage() * 0.30;
            Set<UUID> hit = new HashSet<>();
            hit.add(victim.getUniqueId());
            LivingEntity from = victim;
            AbilityGuard.setAttacking(att);
            try {
                for (int i = 0; i < maxJumps; i++) {
                    LivingEntity next = null;
                    double best = Double.MAX_VALUE;
                    for (LivingEntity le : nearby(from.getLocation(), 5)) {
                        if (hit.contains(le.getUniqueId()) || !targetable(le, att)) continue;
                        double d = le.getLocation().distanceSquared(from.getLocation());
                        if (d < best) { best = d; next = le; }
                    }
                    if (next == null) break;
                    next.damage(dmg, att);
                    next.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, next.getEyeLocation(), 12, 0.2, 0.3, 0.2, 0.05);
                    hit.add(next.getUniqueId());
                    from = next;
                    dmg *= 0.6;
                }
            } finally { AbilityGuard.clearAttacking(att); }
            sound(victim.getLocation(), "minecraft:entity.lightning_bolt.impact", 0.7f, 1.6f);
        }));

        reg.register(Ability.onHit("soul_harvest", "Moisson d'âmes",
                "Chaque coup empile des âmes qui amplifient vos dégâts puis se dissipent", C, (att, victim, e, lvl) -> {
            var pdc = att.getPersistentDataContainer();
            long now = System.currentTimeMillis();
            long last = pdc.getOrDefault(SOUL_LAST, PersistentDataType.LONG, 0L);
            int stacks = pdc.getOrDefault(SOUL_STACKS, PersistentDataType.INTEGER, 0);
            if (now - last > 6000) stacks = 0;
            stacks = Math.min(Math.min(10, 5 + lvl), stacks + 1);
            pdc.set(SOUL_STACKS, PersistentDataType.INTEGER, stacks);
            pdc.set(SOUL_LAST, PersistentDataType.LONG, now);
            double bonus = Math.min(0.80, stacks * (0.03 + 0.01 * lvl));
            e.setDamage(e.getDamage() * (1.0 + bonus));
            att.sendActionBar(Text.mm("<dark_purple>Âmes : <light_purple>" + stacks));
        }));

        reg.register(Ability.onHit("curse_of_weakness", "Malédiction de faiblesse",
                "Vos coups affaiblissent l'attaque de la cible", C, (att, victim, e, lvl) ->
                applyEffect(victim, "weakness", 60 + 15 * lvl, 1 + Math.min(2, lvl / 3))));

        reg.register(Ability.onHit("wither_touch", "Toucher du Wither",
                "Vos coups infligent le Wither (ignore l'armure)", C, (att, victim, e, lvl) -> {
            applyEffect(victim, "wither", 40 + 15 * lvl, Math.min(2, 1 + lvl / 4));
            victim.getWorld().spawnParticle(Particle.SMOKE, victim.getEyeLocation(), 10, 0.2, 0.2, 0.2, 0.01);
        }));

        reg.register(Ability.onHit("lifesteal_nova", "Nova vampirique",
                "Parfois, draine la vie de tous les ennemis proches pour vous soigner", C, (att, victim, e, lvl) -> {
            if (ThreadLocalRandom.current().nextDouble() > Math.min(0.40, 0.15 + 0.04 * lvl)) return;
            double per = 1.0 + 0.5 * lvl, drained = 0;
            AbilityGuard.setAttacking(att);
            try {
                for (LivingEntity le : nearby(att.getLocation(), 4)) {
                    if (!targetable(le, att)) continue;
                    le.damage(per, att);
                    drained += per;
                    le.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, le.getEyeLocation(), 3);
                }
            } finally { AbilityGuard.clearAttacking(att); }
            heal(att, drained * Math.min(0.70, 0.40 + 0.05 * lvl));
            sound(att.getLocation(), "minecraft:entity.warden.heartbeat", 0.8f, 0.7f);
        }));
    }

    // ---------------- PASSIFS ON-DEFEND (armure/arme tenue) ----------------

    private static void registerOnDefend(AbilityRegistry reg, MoonCore plugin) {
        Ability.Category C = Ability.Category.ARMOR;

        reg.register(Ability.onDefend("vampiric_counter", "Riposte vampirique",
                "Renvoie une part des dégâts reçus et vous soigne", C, (victim, e, lvl) -> {
            if (!(e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ev)) return;
            if (!(ev.getDamager() instanceof LivingEntity attacker)) return;
            double reflected = e.getDamage() * Math.min(0.50, 0.20 + 0.05 * lvl);
            AbilityGuard.setAttacking(victim);
            try { attacker.damage(reflected, victim); }
            finally { AbilityGuard.clearAttacking(victim); }
            heal(victim, reflected * 0.30);
            attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getEyeLocation(), 4);
            sound(victim.getLocation(), "minecraft:item.shield.block", 0.8f, 0.8f);
        }));

        reg.register(Ability.onDefend("void_thorns", "Épines du néant",
                "Inflige des dégâts fixes à quiconque vous touche", C, (victim, e, lvl) -> {
            if (!(e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ev)) return;
            if (!(ev.getDamager() instanceof LivingEntity attacker)) return;
            double thorns = Math.min(6.0, 1.0 + 0.75 * lvl);
            AbilityGuard.setAttacking(victim);
            try { attacker.damage(thorns, victim); }
            finally { AbilityGuard.clearAttacking(victim); }
            if (lvl >= 3) applyEffect(attacker, "blindness", 40, 1);
            attacker.getWorld().spawnParticle(Particle.PORTAL, attacker.getEyeLocation(), 15, 0.3, 0.3, 0.3, 0.4);
        }));

        reg.register(Ability.onDefend("second_wind", "Second souffle",
                "Au bord de la mort, une barrière d'énergie vous protège", C, (victim, e, lvl) -> {
            if (victim.getHealth() - e.getFinalDamage() > maxHealth(victim) * 0.20) return;
            var pdc = victim.getPersistentDataContainer();
            long now = System.currentTimeMillis();
            long cd = Math.max(12000, 20000 - 1000L * lvl);
            if (now - pdc.getOrDefault(SECONDWIND, PersistentDataType.LONG, 0L) < cd) return;
            pdc.set(SECONDWIND, PersistentDataType.LONG, now);
            // Encaisse le coup actuel (sinon le coup fatal tue avant que l'absorption ne serve)
            // puis octroie absorption + résistance pour les coups suivants.
            e.setDamage(Math.max(0, Math.min(e.getDamage(), victim.getHealth() - 1)));
            applyEffect(victim, "absorption", 120, Math.min(5, lvl + 1));
            applyEffect(victim, "resistance", 60, 2);
            victim.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, victim.getLocation().add(0, 1, 0), 30);
            sound(victim.getLocation(), "minecraft:item.totem.use", 0.6f, 1.4f);
        }));
    }

    // ---------------- PASSIFS ON-MINE (outil) ----------------

    private static void registerTools(AbilityRegistry reg, MoonCore plugin) {
        reg.register(Ability.onMine("tunnel_3x3", "Foreuse sismique",
                "Brise un tunnel 3x3 dans l'axe du regard", (p, block, e, lvl) -> {
            BlockFace face = lookFace(p);
            BlockFace into = face.getOppositeFace();
            int depth = Math.min(4, 1 + lvl), cap = 36, broken = 0;
            Set<Block> done = new HashSet<>();
            done.add(block);
            AbilityGuard.setMining(p);
            try {
                for (int d = 0; d < depth && broken < cap; d++) {
                    Block center = block.getRelative(into, d);
                    for (Block b : plane3x3(center, face)) {
                        if (broken >= cap) break;
                        if (!done.add(b)) continue;
                        if (breakExtra(p, b)) broken++;
                    }
                }
            } finally { AbilityGuard.clearMining(p); }
        }));

        reg.register(Ability.onMine("vein_miner", "Filon vivant",
                "Brise tout le filon de minerai connecté", (p, origin, e, lvl) -> {
            Material ore = origin.getType();
            if (!isOre(ore)) return;
            int cap = Math.min(64, 24 + 8 * lvl);
            Set<Block> visited = new HashSet<>();
            Deque<Block> queue = new ArrayDeque<>();
            visited.add(origin);
            for (Block n : neighbors26(origin)) if (n.getType() == ore && visited.add(n)) queue.add(n);
            List<Block> toBreak = new ArrayList<>();
            while (!queue.isEmpty() && toBreak.size() < cap) {
                Block b = queue.poll();
                toBreak.add(b);
                for (Block n : neighbors26(b))
                    if (n.getType() == ore && visited.add(n) && toBreak.size() + queue.size() < cap) queue.add(n);
            }
            AbilityGuard.setMining(p);
            try { for (Block b : toBreak) breakExtra(p, b); }
            finally { AbilityGuard.clearMining(p); }
        }));

        reg.register(Ability.onMine("auto_smelt", "Forge intérieure",
                "Les drops du bloc miné sortent déjà fondus", (p, block, e, lvl) -> {
            if (!e.isDropItems()) return; // un autre passif gère déjà les drops
            ItemStack tool = p.getInventory().getItemInMainHand();
            Collection<ItemStack> raw = block.getDrops(tool);
            if (raw.isEmpty()) return;
            e.setDropItems(false);
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            World w = loc.getWorld();
            if (w == null) return;
            for (ItemStack drop : raw) {
                Material smelted = SMELT.get(drop.getType());
                ItemStack out = smelted != null ? new ItemStack(smelted, drop.getAmount()) : drop.clone();
                w.dropItemNaturally(loc, out);
            }
        }));

        reg.register(Ability.onMine("timber", "Chute du sylve",
                "Abat l'arbre entier quand on casse une bûche", (p, origin, e, lvl) -> {
            if (!Tag.LOGS.isTagged(origin.getType())) return;
            Material log = origin.getType();
            int cap = Math.min(96, 48 + 12 * lvl);
            Set<Block> visited = new HashSet<>();
            Deque<Block> q = new ArrayDeque<>();
            visited.add(origin);
            for (Block n : neighbors26(origin))
                if (n.getY() >= origin.getY() && n.getType() == log && visited.add(n)) q.add(n);
            List<Block> logs = new ArrayList<>();
            while (!q.isEmpty() && logs.size() < cap) {
                Block b = q.poll();
                logs.add(b);
                for (Block n : neighbors26(b))
                    if (n.getY() >= origin.getY() - 1 && n.getType() == log && visited.add(n)
                            && logs.size() + q.size() < cap) q.add(n);
            }
            if (!hasLeavesNear(logs)) return; // protège les constructions en bois
            AbilityGuard.setMining(p);
            try { for (Block b : logs) breakExtra(p, b); }
            finally { AbilityGuard.clearMining(p); }
        }));

        reg.register(Ability.onMine("magnet_pickup", "Appel du métal",
                "Les drops vont directement dans l'inventaire", (p, block, e, lvl) -> {
            if (!e.isDropItems()) return;
            ItemStack tool = p.getInventory().getItemInMainHand();
            Collection<ItemStack> drops = block.getDrops(tool);
            if (drops.isEmpty()) return;
            e.setDropItems(false);
            for (ItemStack d : drops) collect(p, d);
            sound(p.getLocation(), "minecraft:entity.item.pickup", 0.4f, 1.6f);
        }));

        reg.register(Ability.onMine("telekinesis", "Emprise spectrale",
                "Drops ET XP collectés directement, rien au sol", (p, block, e, lvl) -> {
            if (e.isDropItems()) {
                ItemStack tool = p.getInventory().getItemInMainHand();
                Collection<ItemStack> drops = block.getDrops(tool);
                if (!drops.isEmpty()) {
                    e.setDropItems(false);
                    for (ItemStack d : drops) collect(p, d);
                }
            }
            int xp = e.getExpToDrop();
            if (xp > 0) { e.setExpToDrop(0); p.giveExp(xp); }
        }));

        reg.register(Ability.onMine("auto_replant", "Main nourricière",
                "Récolte les cultures mûres et les replante", (p, block, e, lvl) -> {
            if (!(block.getBlockData() instanceof Ageable age)) return;
            if (age.getAge() < age.getMaximumAge()) return;
            Material crop = block.getType();
            if (!REPLANTABLE.contains(crop)) return; // évite cocoa/canne/etc. mal replantés
            Material soilNeeded = crop == Material.NETHER_WART ? Material.SOUL_SAND : Material.FARMLAND;
            Block below = block.getRelative(0, -1, 0);
            plugin.schedulers().syncLater(() -> {
                if (!block.getType().isAir() || below.getType() != soilNeeded) return; // sol parti → on ne replante pas
                block.setType(crop, false);
                if (block.getBlockData() instanceof Ageable a2) { a2.setAge(0); block.setBlockData(a2, false); }
            }, 2L);
        }));

        reg.register(Ability.onMine("explosive_mine", "Détonation tellurique",
                "Brise les blocs voisins (sans blesser les entités)", (p, center, e, lvl) -> {
            int r = lvl >= 3 ? 2 : 1, cap = r == 1 ? 26 : 80, broken = 0;
            Set<Block> visited = new HashSet<>();
            visited.add(center);
            AbilityGuard.setMining(p);
            try {
                for (int dx = -r; dx <= r && broken < cap; dx++)
                    for (int dy = -r; dy <= r && broken < cap; dy++)
                        for (int dz = -r; dz <= r && broken < cap; dz++) {
                            if (dx * dx + dy * dy + dz * dz > r * r + 1) continue;
                            Block b = center.getRelative(dx, dy, dz);
                            if (!visited.add(b)) continue;
                            if (breakExtra(p, b)) broken++;
                        }
            } finally { AbilityGuard.clearMining(p); }
            World w = center.getWorld();
            if (w != null) {
                w.playSound(center.getLocation(), "minecraft:entity.generic.explode", 0.7f, 1.4f);
                w.spawnParticle(Particle.EXPLOSION, center.getLocation().add(0.5, 0.5, 0.5), 1);
            }
        }));

        reg.register(Ability.onMine("fortune_surge", "Avidité de la lame",
                "Chance de doubler (ou tripler) les drops de minerai", (p, block, e, lvl) -> {
            if (!isOre(block.getType()) || !e.isDropItems()) return; // drops déjà consommés ? on n'ajoute rien
            if (ThreadLocalRandom.current().nextDouble() >= Math.min(0.45, 0.08 * lvl)) return;
            int mult = ThreadLocalRandom.current().nextDouble() < Math.min(0.25, 0.05 * lvl) ? 3 : 2;
            ItemStack tool = p.getInventory().getItemInMainHand();
            Location loc = block.getLocation().add(0.5, 0.5, 0.5);
            World w = loc.getWorld();
            if (w == null) return;
            for (ItemStack d : block.getDrops(tool)) {
                ItemStack extra = d.clone();
                extra.setAmount(d.getAmount() * (mult - 1));
                w.dropItemNaturally(loc, extra);
            }
            p.sendActionBar(Text.mm(mult == 3 ? "<gold>✦✦ Filon béni !" : "<yellow>✦ Surge !"));
        }));

        reg.register(Ability.onMine("haste_on_mine", "Cadence frénétique",
                "Miner accorde une Célérité empilable temporaire", (p, block, e, lvl) -> {
            var pdc = p.getPersistentDataContainer();
            long now = System.currentTimeMillis();
            int combo = pdc.getOrDefault(MINE_COMBO, PersistentDataType.INTEGER, 0);
            if (now - pdc.getOrDefault(MINE_LAST, PersistentDataType.LONG, 0L) > 3000) combo = 0;
            combo = Math.min(combo + 1, 12);
            pdc.set(MINE_COMBO, PersistentDataType.INTEGER, combo);
            pdc.set(MINE_LAST, PersistentDataType.LONG, now);
            PotionEffectType haste = effect("haste");
            if (haste != null) p.addPotionEffect(new PotionEffect(haste, 60, Math.min(Math.min(lvl, 3), combo / 4), true, false, true));
        }));
    }

    // ============================================================
    //  Saignement (hemorrhage) — timer unique par victime
    // ============================================================

    private static void startBleed(MoonCore plugin, LivingEntity victim) {
        if (!BLEEDING.add(victim.getUniqueId())) return; // déjà en cours
        final org.bukkit.scheduler.BukkitTask[] holder = new org.bukkit.scheduler.BukkitTask[1];
        holder[0] = plugin.schedulers().syncTimer(() -> {
            var pdc = victim.getPersistentDataContainer();
            int ticks = pdc.getOrDefault(BLEED_TICKS, PersistentDataType.INTEGER, 0);
            int stacks = pdc.getOrDefault(BLEED_STACKS, PersistentDataType.INTEGER, 0);
            int lvl = pdc.getOrDefault(BLEED_LVL, PersistentDataType.INTEGER, 1);
            if (victim.isDead() || !victim.isValid() || ticks <= 0 || stacks <= 0) {
                pdc.remove(BLEED_STACKS); pdc.remove(BLEED_TICKS); pdc.remove(BLEED_LVL); pdc.remove(BLEED_SRC);
                BLEEDING.remove(victim.getUniqueId());
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            double dmg = stacks * (0.5 + 0.25 * lvl);
            // Dégâts via le pipeline (armure ignorée par le wither, mais totem/résistance/crédit
            // du tueur respectés) ; garde anti-récursion pour ne pas re-proc les passifs d'arme.
            String src = pdc.get(BLEED_SRC, PersistentDataType.STRING);
            org.bukkit.entity.Player attacker = src == null ? null : Bukkit.getPlayer(UUID.fromString(src));
            if (attacker != null) AbilityGuard.damage(attacker, victim, dmg);
            else victim.damage(dmg);
            World w = victim.getWorld();
            if (w != null) w.spawnParticle(Particle.DUST, victim.getEyeLocation(), 6, 0.2, 0.3, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.0f));
            pdc.set(BLEED_TICKS, PersistentDataType.INTEGER, ticks - 1);
        }, 10L, 10L);
    }

    // ============================================================
    //  Helpers minage
    // ============================================================

    /** Casse un bloc « bonus » en respectant les protections (event annulable), sans récursion. */
    private static boolean breakExtra(Player p, Block b) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return false; // outil cassé/parti → on arrête de miner
        if (b.getType().isAir() || b.isLiquid()) return false;
        if (b.getType().getHardness() < 0) return false; // bedrock / incassable
        if (isProtectedBlock(b)) return false;           // coffres, spawners, beacons, lits… jamais détruits
        BlockBreakEvent sub = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(sub); // AbilityGuard.mining(p) → notre listener s'abstient
        if (sub.isCancelled()) return false;
        int xp = oreXp(b, tool);
        b.breakNaturally(tool);
        if (xp > 0 && b.getWorld() != null) {
            ExperienceOrb orb = b.getWorld().spawn(b.getLocation().add(0.5, 0.5, 0.5), ExperienceOrb.class);
            orb.setExperience(xp);
        }
        damageTool(p, 1);
        return true;
    }

    /** Blocs « précieux » (tile-entities / contenants) qu'un minage de zone ne doit jamais casser. */
    private static boolean isProtectedBlock(Block b) {
        org.bukkit.block.BlockState st = b.getState();
        if (st instanceof org.bukkit.inventory.InventoryHolder) return true; // coffres, barils, fourneaux, shulkers, hoppers…
        if (st instanceof org.bukkit.block.CreatureSpawner) return true;
        Material m = b.getType();
        return m == Material.BEACON || m == Material.CONDUIT || m == Material.RESPAWN_ANCHOR
                || m == Material.ENCHANTING_TABLE || m == Material.LODESTONE
                || m == Material.SPAWNER || m.name().endsWith("_BED");
    }

    private static int oreXp(Block b, ItemStack tool) {
        if (tool != null) // silk touch → pas d'XP (parité vanilla), sans passer par un registre déprécié
            for (org.bukkit.enchantments.Enchantment en : tool.getEnchantments().keySet())
                if (en.getKey().getKey().equals("silk_touch")) return 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (b.getType()) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 1 + r.nextInt(2);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 3 + r.nextInt(5);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE, NETHER_QUARTZ_ORE -> 2 + r.nextInt(4);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 1 + r.nextInt(5);
            default -> 0;
        };
    }

    private static void damageTool(Player p, int amount) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return;
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable dmg) || meta.isUnbreakable()) return;
        short max = tool.getType().getMaxDurability();
        if (max <= 0) return;
        int nd = dmg.getDamage() + amount;
        if (nd >= max) {
            p.getInventory().setItemInMainHand(null);
            p.getWorld().playSound(p.getLocation(), "minecraft:entity.item.break", 1f, 1f);
        } else {
            dmg.setDamage(nd);
            tool.setItemMeta(meta);
            p.getInventory().setItemInMainHand(tool);
        }
    }

    private static void collect(Player p, ItemStack stack) {
        for (ItemStack rest : p.getInventory().addItem(stack.clone()).values()) {
            Location l = p.getLocation();
            if (l.getWorld() != null) l.getWorld().dropItemNaturally(l, rest);
        }
    }

    private static boolean isOre(Material m) {
        return m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS;
    }

    private static boolean hasLeavesNear(List<Block> logs) {
        for (Block b : logs)
            for (Block n : neighbors26(b))
                if (Tag.LEAVES.isTagged(n.getType())) return true;
        return false;
    }

    private static BlockFace lookFace(Player p) {
        RayTraceResult rt = p.rayTraceBlocks(6);
        if (rt != null && rt.getHitBlockFace() != null) return rt.getHitBlockFace();
        Vector d = p.getLocation().getDirection();
        double ax = Math.abs(d.getX()), ay = Math.abs(d.getY()), az = Math.abs(d.getZ());
        if (ay >= ax && ay >= az) return d.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        if (ax >= az) return d.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        return d.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    /** 9 blocs du plan perpendiculaire à {@code normal}, centrés sur {@code center}. */
    private static List<Block> plane3x3(Block center, BlockFace normal) {
        List<Block> out = new ArrayList<>(9);
        boolean nx = normal.getModX() != 0, ny = normal.getModY() != 0;
        for (int a = -1; a <= 1; a++)
            for (int b = -1; b <= 1; b++) {
                int dx, dy, dz;
                if (nx) { dx = 0; dy = a; dz = b; }
                else if (ny) { dx = a; dy = 0; dz = b; }
                else { dx = a; dy = b; dz = 0; }
                out.add(center.getRelative(dx, dy, dz));
            }
        return out;
    }

    private static List<Block> neighbors26(Block b) {
        List<Block> out = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++)
                    if (dx != 0 || dy != 0 || dz != 0) out.add(b.getRelative(dx, dy, dz));
        return out;
    }

    // ============================================================
    //  Helpers combat / effets (version-safe 1.21.x)
    // ============================================================

    private static List<LivingEntity> nearby(Location loc, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        World w = loc.getWorld();
        if (w == null) return out;
        for (Entity e : w.getNearbyEntities(loc, radius, radius, radius))
            if (e instanceof LivingEntity le) out.add(le);
        return out;
    }

    private static List<LivingEntity> cone(Player p, double range, double minDot) {
        List<LivingEntity> out = new ArrayList<>();
        Vector dir = p.getLocation().getDirection().normalize();
        Location eye = p.getEyeLocation();
        for (LivingEntity le : nearby(p.getLocation(), range)) {
            if (le.equals(p)) continue;
            Vector to = le.getLocation().toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 1e-4) { out.add(le); continue; }
            if (dir.dot(to.normalize()) >= minDot) out.add(le);
        }
        return out;
    }

    /** Cible valide d'une capacité offensive : exclut le lanceur, les armor stands et ses familiers. */
    private static boolean targetable(LivingEntity le, Player caster) {
        if (le.equals(caster)) return false;
        if (le instanceof org.bukkit.entity.ArmorStand) return false;
        if (le instanceof org.bukkit.entity.Tameable t && t.isTamed()) {
            org.bukkit.entity.AnimalTamer owner = t.getOwner();
            if (owner != null && owner.getUniqueId().equals(caster.getUniqueId())) return false;
        }
        return true;
    }

    private static LivingEntity lookTarget(Player p, double range) {
        World w = p.getWorld();
        RayTraceResult r = w.rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                range, 0.6, e -> e instanceof LivingEntity && !e.equals(p));
        if (r != null && r.getHitEntity() instanceof LivingEntity le) return le;
        // repli : la plus proche dans un cône serré
        LivingEntity best = null;
        double bestDot = 0.9;
        Vector dir = p.getLocation().getDirection().normalize();
        for (LivingEntity le : nearby(p.getEyeLocation(), range)) {
            if (le.equals(p)) continue;
            Vector to = le.getLocation().toVector().subtract(p.getEyeLocation().toVector());
            if (to.lengthSquared() < 1e-4) continue;
            double dot = dir.dot(to.normalize());
            if (dot > bestDot) { bestDot = dot; best = le; }
        }
        return best;
    }

    private static void heal(LivingEntity le, double amount) {
        if (amount <= 0) return;
        le.setHealth(Math.min(maxHealth(le), le.getHealth() + amount));
    }

    private static double maxHealth(LivingEntity le) {
        var a = le.getAttribute(com.mooncore.util.Attrs.MAX_HEALTH);
        return a != null ? a.getValue() : 20.0;
    }

    private static PotionEffectType effect(String key) {
        return org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }

    /** Applique un effet par clé de registre ({@code level} 1-based → amplificateur 0-based). */
    private static void applyEffect(LivingEntity le, String key, int durationTicks, int level) {
        PotionEffectType t = effect(key);
        if (t != null) le.addPotionEffect(new PotionEffect(t, durationTicks, Math.max(0, level - 1), true, false));
    }

    private static void sound(Location loc, String key, float vol, float pitch) {
        if (loc.getWorld() != null) loc.getWorld().playSound(loc, key, vol, pitch);
    }
}
