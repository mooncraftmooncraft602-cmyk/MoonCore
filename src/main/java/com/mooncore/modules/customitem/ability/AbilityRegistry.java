package com.mooncore.modules.customitem.ability;

import com.mooncore.MoonCore;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catalogue des capacités intégrées (actives + passives). Extensible : appeler
 * {@link #register(Ability)} pour ajouter une capacité custom sans modifier le cœur.
 * <p>
 * Toutes les capacités actives s'exécutent sur le thread principal (déclenchées par
 * un clic droit). Les effets utilisent une API version-safe pour Paper 1.21.1 et
 * fonctionnent à l'identique pour les joueurs Java et Bedrock.
 */
public final class AbilityRegistry {

    private final MoonCore plugin;
    private final Map<String, Ability> byId = new LinkedHashMap<>();

    public AbilityRegistry(MoonCore plugin) {
        this.plugin = plugin;
        registerBuiltins();
        AbilityCatalog.register(this, plugin); // pouvoirs de magie noire + capacités d'outils
    }

    public void register(Ability ability) {
        byId.put(ability.id().toLowerCase(Locale.ROOT), ability);
    }

    public Ability get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) { return get(id) != null; }

    public java.util.Collection<Ability> all() { return byId.values(); }

    // ============================================================
    //  Capacités intégrées
    // ============================================================

    private void registerBuiltins() {
        // ---- ACTIVES ----
        register(Ability.active("dash", "Ruée", "Propulse en avant", 6_000, (p, lvl) -> {
            Vector dir = p.getLocation().getDirection().normalize().multiply(1.2 + 0.3 * lvl).setY(0.35);
            p.setVelocity(dir);
            sound(p.getLocation(), "minecraft:entity.breeze.shoot", 1f, 1.4f);
        }));

        register(Ability.active("blink", "Clignement", "Téléportation courte", 8_000, (p, lvl) -> {
            int range = 6 + 2 * lvl;
            Block target = p.getTargetBlockExact(range);
            Location dest = target != null
                    ? target.getLocation().add(0.5, 1, 0.5)
                    : p.getEyeLocation().add(p.getLocation().getDirection().multiply(range));
            dest.setPitch(p.getLocation().getPitch());
            dest.setYaw(p.getLocation().getYaw());
            sound(p.getLocation(), "minecraft:entity.enderman.teleport", 1f, 1f);
            p.teleport(dest);
            sound(dest, "minecraft:entity.enderman.teleport", 1f, 1f);
        }));

        register(Ability.active("fire_slash", "Entaille de feu", "Tranche enflammée en cône", 5_000, (p, lvl) -> {
            double dmg = 4 + 2.0 * lvl;
            for (LivingEntity le : coneTargets(p, 5, 0.5)) {
                le.damage(dmg, p);
                le.setFireTicks(60 + 20 * lvl);
            }
            spawnLine(p, "minecraft:block.fire.ambient");
        }));

        register(Ability.active("lightning_strike", "Foudre", "Frappe la cible de foudre", 10_000, (p, lvl) -> {
            Block target = p.getTargetBlockExact(30);
            Location loc = target != null ? target.getLocation() : p.getLocation();
            World w = loc.getWorld();
            if (w == null) return;
            w.strikeLightning(loc);
            for (LivingEntity le : nearby(loc, 3 + lvl)) {
                if (!le.equals(p)) le.damage(3.0 * lvl, p);
            }
        }));

        register(Ability.active("ground_smash", "Choc tellurique", "Projette les ennemis en l'air", 9_000, (p, lvl) -> {
            for (LivingEntity le : nearby(p.getLocation(), 4 + lvl)) {
                if (le.equals(p)) continue;
                le.damage(3.0 + lvl, p);
                le.setVelocity(new Vector(0, 0.8 + 0.1 * lvl, 0));
            }
            sound(p.getLocation(), "minecraft:entity.generic.explode", 1f, 0.8f);
        }));

        register(Ability.active("heal", "Soin", "Restaure des points de vie", 15_000, (p, lvl) -> {
            double max = maxHealth(p);
            p.setHealth(Math.min(max, p.getHealth() + 4.0 + 2.0 * lvl));
            applyEffect(p, "regeneration", 60, lvl);
            sound(p.getLocation(), "minecraft:block.amethyst_block.chime", 1f, 1.2f);
        }));

        register(Ability.active("shockwave", "Onde de choc", "Repousse les ennemis proches", 8_000, (p, lvl) -> {
            Vector center = p.getLocation().toVector();
            for (LivingEntity le : nearby(p.getLocation(), 5 + lvl)) {
                if (le.equals(p)) continue;
                Vector push = le.getLocation().toVector().subtract(center).normalize().multiply(1.0 + 0.2 * lvl).setY(0.4);
                le.setVelocity(push);
                le.damage(2.0 + lvl, p);
            }
            sound(p.getLocation(), "minecraft:entity.warden.sonic_boom", 1f, 1.4f);
        }));

        register(Ability.active("poison_cloud", "Nuage toxique", "Empoisonne la zone", 12_000, (p, lvl) -> {
            for (LivingEntity le : nearby(p.getLocation(), 4 + lvl)) {
                if (le.equals(p)) continue;
                applyEffect(le, "poison", 80 + 20 * lvl, lvl);
            }
            sound(p.getLocation(), "minecraft:entity.witch.throw", 1f, 0.9f);
        }));

        register(Ability.active("meteor", "Météore", "Explosion à la cible", 20_000, (p, lvl) -> {
            Block target = p.getTargetBlockExact(40);
            Location loc = (target != null ? target.getLocation() : p.getLocation()).add(0.5, 0, 0.5);
            World w = loc.getWorld();
            if (w == null) return;
            Location high = loc.clone().add(0, 12, 0);
            sound(high, "minecraft:entity.blaze.shoot", 2f, 0.6f);
            long delay = 20L;
            float power = 2.5f + 0.5f * lvl;
            plugin.schedulers().syncLater(() -> {
                w.createExplosion(loc, power, false, false, p);
                for (LivingEntity le : nearby(loc, 4 + lvl)) {
                    if (!le.equals(p)) le.setFireTicks(80);
                }
            }, delay);
        }));

        register(Ability.active("dragon_roar", "Rugissement du dragon", "Cri dévastateur en zone", 25_000, (p, lvl) -> {
            for (LivingEntity le : nearby(p.getLocation(), 6 + lvl)) {
                if (le.equals(p)) continue;
                le.damage(5.0 + 1.5 * lvl, p);
                applyEffect(le, "slowness", 60, lvl);
            }
            sound(p.getLocation(), "minecraft:entity.ender_dragon.growl", 2f, 1f);
        }));

        // ---- PASSIVES (appliquées par CustomItemListener) ----
        register(Ability.passive("regeneration", "Régénération", "Régénère lentement la vie"));
        register(Ability.passive("life_steal", "Vol de vie", "Rend des PV en attaquant"));
        register(Ability.passive("resistance", "Résistance", "Réduit les dégâts subis"));
        register(Ability.passive("fortune", "Fortune", "Augmente les drops de minage"));
        register(Ability.passive("xp_bonus", "Bonus d'XP", "Gagne plus d'expérience"));
        register(Ability.passive("loot_bonus", "Bonus de butin", "Augmente les drops de mobs"));
        register(Ability.passive("boss_damage", "Tueur de boss", "Dégâts accrus contre les boss"));
        register(Ability.passive("speed_bonus", "Célérité", "Vitesse de déplacement accrue"));
    }

    // ============================================================
    //  Helpers d'effets (version-safe 1.21.1)
    // ============================================================

    private static double maxHealth(Player p) {
        var attr = p.getAttribute(com.mooncore.util.Attrs.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    private static List<LivingEntity> nearby(Location loc, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        World w = loc.getWorld();
        if (w == null) return out;
        for (Entity e : w.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity le) out.add(le);
        }
        return out;
    }

    /** Cibles vivantes dans un cône devant le joueur. */
    private static List<LivingEntity> coneTargets(Player p, double range, double minDot) {
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

    private static void sound(Location loc, String key, float vol, float pitch) {
        if (loc.getWorld() != null) loc.getWorld().playSound(loc, key, vol, pitch);
    }

    private void spawnLine(Player p, String soundKey) {
        sound(p.getLocation(), soundKey, 1f, 1f);
    }

    /** Applique un effet de potion résolu par clé (robuste aux renommages d'enum). */
    private static void applyEffect(LivingEntity le, String key, int durationTicks, int amplifier) {
        PotionEffectType type = org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (type == null) return;
        le.addPotionEffect(new PotionEffect(type, durationTicks, Math.max(0, amplifier - 1), true, false));
    }
}
