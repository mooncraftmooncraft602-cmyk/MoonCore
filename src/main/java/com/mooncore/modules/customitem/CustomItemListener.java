package com.mooncore.modules.customitem;

import com.mooncore.api.customitem.ItemStats;
import com.mooncore.modules.customitem.ability.Ability;
import com.mooncore.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Logique de jeu des objets custom. Conçu pour Java <b>et</b> Bedrock : le déclencheur
 * des capacités actives est le clic droit (relayé par Geyser), et tous les effets
 * passent par l'API serveur (donc identiques quel que soit le client).
 */
public final class CustomItemListener implements Listener {

    private final CustomItemManagerModule module;

    public CustomItemListener(CustomItemManagerModule module) {
        this.module = module;
    }

    // ============================================================
    //  Capacités actives — clic droit
    // ============================================================

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return; // évite le double-déclenchement

        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        CustomItemDef def = defOf(item);
        if (def == null) return;

        // Consommable custom (potion/nourriture) : applique les effets puis consomme 1 exemplaire.
        if (def.type() == com.mooncore.api.customitem.ItemType.CONSUMABLE && !def.consumeEffects().isEmpty()) {
            consume(e, p, item, def);
            return;
        }
        if (def.abilities().isEmpty()) return;

        double cdr = stat(def, ItemStats.COOLDOWN_REDUCTION);
        boolean any = false;
        for (CustomItemDef.AbilityRef ref : def.abilities()) {
            Ability ab = module.abilities().get(ref.id());
            if (ab == null || !ab.isActive()) continue;
            any = true;
            String key = p.getUniqueId() + ":" + ref.id();
            long now = System.currentTimeMillis();
            long cd = ab.cooldownMs(cdr);
            long remaining = module.abilityCooldowns().remaining(key, now, cd);
            if (remaining > 0) {
                p.sendActionBar(Text.mm("<red>" + ab.displayName() + " — "
                        + String.format(java.util.Locale.ROOT, "%.1f", remaining / 1000.0) + "s"));
                continue;
            }
            module.abilityCooldowns().tryAcquire(key, now, cd);
            try {
                ab.cast(p, ref.level());
            } catch (Throwable t) {
                module.mc().logger().error("Erreur capacité " + ref.id() + " sur " + def.id(), t);
            }
        }
        if (any && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Empêche l'ouverture/placement parasite quand l'objet sert de focus magique.
            // (On ne cancel pas pour les outils/armes afin de garder le comportement vanilla.)
        }
    }

    /** Consomme un objet « consommable » : applique ses effets de potion et retire 1 exemplaire. */
    private void consume(PlayerInteractEvent e, Player p, ItemStack item, CustomItemDef def) {
        long now = System.currentTimeMillis();
        String key = p.getUniqueId() + ":consume:" + def.id();
        if (module.abilityCooldowns().remaining(key, now, 500) > 0) return; // anti-spam 0,5 s
        module.abilityCooldowns().tryAcquire(key, now, 500);
        for (CustomItemDef.ConsumeEffect ce : def.consumeEffects()) {
            var type = org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(ce.key()));
            if (type != null) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(type, ce.duration(), Math.max(0, ce.amplifier()), true, true));
            }
        }
        if (item.getAmount() <= 1) p.getInventory().setItemInMainHand(null);
        else item.setAmount(item.getAmount() - 1);
        p.getWorld().playSound(p.getLocation(), "minecraft:entity.generic.drink", 1f, 1f);
        e.setCancelled(true);
    }

    // ============================================================
    //  Menu GUI des objets custom
    // ============================================================

    @EventHandler
    public void onMenuClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CustomItemMenu menu)) return;
        e.setCancelled(true); // empêche de prendre les items d'affichage
        if (e.getClickedInventory() == null || !(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        menu.onClick(p, e.getRawSlot());
    }

    @EventHandler
    public void onPixelClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof PixelEditor ed)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        ed.onClick(p, e.getRawSlot(), e.isRightClick());
    }

    // ============================================================
    //  Attaque — crit, vol de vie, multiplicateurs PvE/PvP/Boss
    // ============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        // Coup secondaire d'une capacité (fendoir, chaîne d'éclairs, riposte…) → on n'applique
        // PAS à nouveau crit/multiplicateurs/on-hit pour éviter toute récursion.
        if (com.mooncore.modules.customitem.ability.AbilityGuard.attacking(p)) return;
        ItemStack weapon = p.getInventory().getItemInMainHand();
        CustomItemDef def = defOf(weapon);
        if (def == null) return;

        double damage = e.getDamage();

        // Critique.
        double critChance = stat(def, ItemStats.CRIT_CHANCE);
        if (critChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) < critChance) {
            double critDmg = stat(def, ItemStats.CRIT_DAMAGE);
            if (critDmg <= 0) critDmg = 50.0; // défaut +50%
            damage *= (1.0 + critDmg / 100.0);
            p.sendActionBar(Text.mm("<red>✦ Critique !"));
        }

        // Multiplicateurs contextuels.
        boolean boss = isBoss(victim);
        boolean pvp = victim instanceof Player;
        double mult = 1.0;
        if (pvp) mult += stat(def, ItemStats.PVP_DAMAGE) / 100.0;
        else mult += stat(def, ItemStats.PVE_DAMAGE) / 100.0;
        if (boss) {
            mult += stat(def, ItemStats.BOSS_DAMAGE) / 100.0;
            mult += passiveLevel(def, "boss_damage") * 0.10; // +10%/niveau
        }
        damage *= mult;

        e.setDamage(damage);

        // Vol de vie (stat + passif).
        double lifeStealPct = stat(def, ItemStats.LIFE_STEAL) + passiveLevel(def, "life_steal") * 5.0;
        if (lifeStealPct > 0) {
            double heal = damage * lifeStealPct / 100.0;
            double max = maxHealth(p);
            p.setHealth(Math.min(max, p.getHealth() + heal));
        }

        // Capacités passives « on-hit » (hémorragie, exécution, fendoir, wither…).
        for (CustomItemDef.AbilityRef ref : def.abilities()) {
            Ability ab = module.abilities().get(ref.id());
            if (ab == null || ab.hitHandler() == null) continue;
            try { ab.hitHandler().onHit(p, victim, e, ref.level()); }
            catch (Throwable t) { module.mc().logger().error("Erreur on-hit " + ref.id(), t); }
        }
    }

    // ============================================================
    //  Défense — résistance passive (armures custom portées)
    // ============================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDefend(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        // Vulnérabilité « frénésie » (capacité active berserk_frenzy) : +15% de dégâts subis.
        long frenzy = p.getPersistentDataContainer().getOrDefault(
                org.bukkit.NamespacedKey.fromString("mooncore:frenzy_until"),
                org.bukkit.persistence.PersistentDataType.LONG, 0L);
        if (frenzy > System.currentTimeMillis()) e.setDamage(e.getDamage() * 1.15);

        int resistLevels = 0;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            CustomItemDef def = defOf(armor);
            if (def != null) resistLevels += passiveLevel(def, "resistance");
        }
        if (resistLevels > 0) {
            double reduction = Math.min(0.6, resistLevels * 0.05); // 5%/niveau, plafond 60%
            e.setDamage(e.getDamage() * (1.0 - reduction));
        }

        // Capacités passives « on-defend » (riposte, épines, second souffle) — armure + arme tenue.
        // Garde : si CE joueur est déjà en train de riposter, on ne re-déclenche pas.
        if (com.mooncore.modules.customitem.ability.AbilityGuard.defending(p)) return;
        java.util.List<ItemStack> worn = new java.util.ArrayList<>(java.util.Arrays.asList(p.getInventory().getArmorContents()));
        worn.add(p.getInventory().getItemInMainHand());
        com.mooncore.modules.customitem.ability.AbilityGuard.setDefending(p);
        try {
            java.util.Set<String> fired = new java.util.HashSet<>(); // une capacité ne s'applique qu'une fois
            for (ItemStack piece : worn) {
                CustomItemDef def = defOf(piece);
                if (def == null) continue;
                for (CustomItemDef.AbilityRef ref : def.abilities()) {
                    Ability ab = module.abilities().get(ref.id());
                    if (ab == null || ab.defendHandler() == null || !fired.add(ref.id())) continue;
                    try { ab.defendHandler().onDefend(p, e, ref.level()); }
                    catch (Throwable t) { module.mc().logger().error("Erreur on-defend " + ref.id(), t); }
                }
            }
        } finally {
            com.mooncore.modules.customitem.ability.AbilityGuard.clearDefending(p);
        }
    }

    // ============================================================
    //  Minage — fortune / harvest (outils custom)
    // ============================================================

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        // Cassure en chaîne d'un outil (3x3, filon, abattage…) → on n'enchaîne pas récursivement.
        if (com.mooncore.modules.customitem.ability.AbilityGuard.mining(p)) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        CustomItemDef def = defOf(tool);
        if (def == null) return;

        // Capacités passives « on-mine » (3x3, filon, fonte, abattage, aimant…).
        for (CustomItemDef.AbilityRef ref : def.abilities()) {
            Ability ab = module.abilities().get(ref.id());
            if (ab == null || ab.mineHandler() == null) continue;
            try { ab.mineHandler().onMine(p, e.getBlock(), e, ref.level()); }
            catch (Throwable t) { module.mc().logger().error("Erreur on-mine " + ref.id(), t); }
        }

        // Si une capacité d'outil a déjà consommé/redirigé les drops (fonte/aimant/télékinésie),
        // on n'ajoute pas de bonus fortune au sol (sinon duplication / items éparpillés).
        if (!e.isDropItems()) return;
        int fortune = passiveLevel(def, "fortune");
        int harvest = passiveLevel(def, "harvest");
        double harvestBonus = stat(def, ItemStats.HARVEST_BONUS);
        if (fortune <= 0 && harvest <= 0 && harvestBonus <= 0) return;

        double chance = Math.min(0.9, fortune * 0.15 + harvest * 0.10 + harvestBonus / 100.0);
        if (chance <= 0) return;
        if (ThreadLocalRandom.current().nextDouble() >= chance) return;

        var drops = e.getBlock().getDrops(tool);
        var loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (ItemStack drop : drops) {
            if (loc.getWorld() != null) loc.getWorld().dropItemNaturally(loc, drop.clone());
        }
    }

    // ============================================================
    //  Mort de créature — loot_bonus + drops custom (boss/mob)
    // ============================================================

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();

        // Source de drop : boss:<id> si taggé, sinon mob:<TYPE>.
        String bossId = dead.getPersistentDataContainer().get(module.bossKey(), PersistentDataType.STRING);
        String mobSource = "mob:" + dead.getType().name();
        String bossSource = bossId != null ? "boss:" + bossId : null;

        var killer = dead.getKiller();
        for (CustomItemDef def : module.rawDefs().values()) {
            for (CustomItemDef.DropRule rule : def.drops()) {
                boolean match = rule.source().equalsIgnoreCase(mobSource)
                        || (bossSource != null && rule.source().equalsIgnoreCase(bossSource))
                        || (bossId != null && rule.source().equalsIgnoreCase("boss:*"));
                if (!match) continue;
                double chance = rule.chance();
                // Bonus de butin du tueur.
                if (killer != null) {
                    CustomItemDef weapon = defOf(killer.getInventory().getItemInMainHand());
                    if (weapon != null) chance *= 1.0 + passiveLevel(weapon, "loot_bonus") * 0.15;
                }
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    int amount = rule.min() >= rule.max() ? rule.min()
                            : ThreadLocalRandom.current().nextInt(rule.min(), rule.max() + 1);
                    ItemStack drop = module.buildItem(def, Math.max(1, amount));
                    if (dead.getWorld() != null) dead.getWorld().dropItemNaturally(dead.getLocation(), drop);
                }
            }
        }
    }

    // ============================================================
    //  XP — xp_bonus
    // ============================================================

    @EventHandler
    public void onExp(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        int levels = passiveLevel(defOf(p.getInventory().getItemInMainHand()), "xp_bonus");
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            levels += passiveLevel(defOf(armor), "xp_bonus");
        }
        if (levels <= 0) return;
        e.setAmount((int) Math.round(e.getAmount() * (1.0 + levels * 0.10)));
    }

    // ============================================================
    //  Tick des effets passifs continus (régén, célérité)
    // ============================================================

    public void tickPassives() {
        for (Player p : module.server().getOnlinePlayers()) {
            int regen = 0, speed = 0;
            regen += passiveLevel(defOf(p.getInventory().getItemInMainHand()), "regeneration");
            speed += passiveLevel(defOf(p.getInventory().getItemInMainHand()), "speed_bonus");
            for (ItemStack armor : p.getInventory().getArmorContents()) {
                regen += passiveLevel(defOf(armor), "regeneration");
                speed += passiveLevel(defOf(armor), "speed_bonus");
            }
            if (regen > 0) applyEffect(p, "regeneration", regen);
            if (speed > 0) applyEffect(p, "speed", speed);
        }
    }

    private static void applyEffect(Player p, String key, int amplifier) {
        var type = org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(key));
        if (type == null) return;
        // Durée 60 ticks > intervalle de tick (40) → effet sans clignotement.
        p.addPotionEffect(new org.bukkit.potion.PotionEffect(type, 60, Math.max(0, amplifier - 1), true, false, false));
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private CustomItemDef defOf(ItemStack item) {
        if (CustomItemManagerModule.isAir(item)) return null;
        return module.rawDef(module.idOf(item));
    }

    private static double stat(CustomItemDef def, String key) {
        return def == null ? 0.0 : def.stats().getOrDefault(key, 0.0);
    }

    private int passiveLevel(CustomItemDef def, String abilityId) {
        if (def == null) return 0;
        for (CustomItemDef.AbilityRef ref : def.abilities()) {
            if (ref.id().equalsIgnoreCase(abilityId)) return ref.level();
        }
        return 0;
    }

    private boolean isBoss(LivingEntity le) {
        return le.getPersistentDataContainer().has(module.bossKey(), PersistentDataType.STRING);
    }

    private static double maxHealth(Player p) {
        var attr = p.getAttribute(com.mooncore.util.Attrs.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
