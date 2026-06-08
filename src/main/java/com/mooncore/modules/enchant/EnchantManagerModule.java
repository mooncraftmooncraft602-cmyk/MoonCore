package com.mooncore.modules.enchant;

import com.mooncore.api.economy.EconomyService;
import com.mooncore.command.sub.EnchantSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CustomEnchantManager : moteur des 30 enchantements custom. Stockage sur l'objet via PDC,
 * lore régénérée, application par commande ou enclume (livre), dispatch des effets et boucle
 * passive pour les enchantements d'équipement.
 */
@ModuleInfo(id = "custom-enchant", name = "CustomEnchantManager",
        softDepends = {"economy-balancer", "progression"})
public final class EnchantManagerModule extends AbstractModule {

    private final Map<String, CustomEnchant> registry = new LinkedHashMap<>();
    private NamespacedKey bossKey;
    private BukkitTask equipTask;

    @Override
    protected void onEnable() {
        this.bossKey = new NamespacedKey(plugin(), "boss");
        for (CustomEnchant e : EnchantRegistry.build(this)) {
            registry.put(e.id(), e);
        }
        log().info("CustomEnchantManager : " + registry.size() + " enchantement(s) enregistré(s).");

        registerListener(new EnchantListener(this));
        plugin().rootCommand().register(new EnchantSubCommand(this));

        equipTask = schedulers().syncTimer(this::tickEquip, 40L, 20L);
    }

    @Override
    protected void onDisable() {
        if (equipTask != null) equipTask.cancel();
        registry.clear();
    }

    // ---- Registre ----

    public CustomEnchant byId(String id) { return registry.get(id.toLowerCase(java.util.Locale.ROOT)); }
    public java.util.Collection<CustomEnchant> all() { return registry.values(); }

    public NamespacedKey key(CustomEnchant e) {
        return new NamespacedKey(plugin(), "ench_" + e.id());
    }

    // ---- Lecture / écriture sur l'objet ----

    public int getLevel(ItemStack item, CustomEnchant e) {
        if (item == null || !item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer lvl = pdc.get(key(e), PersistentDataType.INTEGER);
        return lvl == null ? 0 : lvl;
    }

    /** Applique un niveau (0 = retire). Retourne false si l'objet n'accepte pas l'enchant. */
    public boolean apply(ItemStack item, CustomEnchant e, int level) {
        if (item == null) return false;
        if (level > 0 && !e.target().matches(item.getType())) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (level <= 0) {
            pdc.remove(key(e));
        } else {
            pdc.set(key(e), PersistentDataType.INTEGER, Math.min(level, e.maxLevel()));
        }
        item.setItemMeta(meta);
        rebuildLore(item);
        return true;
    }

    public Map<CustomEnchant, Integer> enchantsOn(ItemStack item) {
        Map<CustomEnchant, Integer> out = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) return out;
        for (CustomEnchant e : registry.values()) {
            int lvl = getLevel(item, e);
            if (lvl > 0) out.put(e, lvl);
        }
        return out;
    }

    /** Régénère la portion de lore correspondant aux enchants custom (préfixée d'un marqueur). */
    public void rebuildLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = new ArrayList<>();
        for (Map.Entry<CustomEnchant, Integer> en : enchantsOn(item).entrySet()) {
            lore.add(Text.mm("<gray>" + en.getKey().displayName() + " "
                    + roman(en.getValue()) + "</gray>"));
        }
        // Remplace entièrement la lore (gear custom) — simple et déterministe.
        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    // ---- Dispatch des effets (appelé par le listener) ----

    public void dispatchMelee(Player attacker, LivingEntity victim, EntityDamageByEntityEvent event) {
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        for (CustomEnchant e : registry.values()) {
            if (e.melee() == null) continue;
            int lvl = getLevel(hand, e);
            if (lvl > 0) e.melee().onHit(attacker, victim, lvl, event);
        }
    }

    public void dispatchDefense(Player defender, EntityDamageEvent event) {
        for (ItemStack piece : defender.getInventory().getArmorContents()) {
            if (piece == null) continue;
            for (CustomEnchant e : registry.values()) {
                if (e.defense() == null) continue;
                int lvl = getLevel(piece, e);
                if (lvl > 0) e.defense().onDamaged(defender, lvl, event);
            }
        }
    }

    public void dispatchMining(Player miner, org.bukkit.block.Block block, BlockBreakEvent event) {
        ItemStack hand = miner.getInventory().getItemInMainHand();
        for (CustomEnchant e : registry.values()) {
            if (e.mining() == null) continue;
            int lvl = getLevel(hand, e);
            if (lvl > 0) e.mining().onMine(miner, block, lvl, event);
        }
    }

    private void tickEquip() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Pré-pass : réinitialise les états gérés par enchant (retrait propre quand déséquipé).
            var kb = p.getAttribute(com.mooncore.util.Attrs.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(0);
            var gm = p.getGameMode();
            if ((gm == org.bukkit.GameMode.SURVIVAL || gm == org.bukkit.GameMode.ADVENTURE) && !p.isGliding()) {
                p.setAllowFlight(false);
            }

            List<ItemStack> equipped = new ArrayList<>();
            for (ItemStack a : p.getInventory().getArmorContents()) if (a != null) equipped.add(a);
            equipped.add(p.getInventory().getItemInMainHand());
            for (ItemStack item : equipped) {
                if (item == null) continue;
                for (CustomEnchant e : registry.values()) {
                    if (e.equip() == null) continue;
                    int lvl = getLevel(item, e);
                    if (lvl > 0) e.equip().onTick(p, lvl);
                }
            }
        }
    }

    // ---- Helpers exposés aux effets ----

    public boolean isBoss(Entity entity) {
        return entity.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
    }

    /** Niveau total d'un enchant sur l'armure (somme des pièces). */
    public int armorLevel(Player p, String enchantId) {
        CustomEnchant e = byId(enchantId);
        if (e == null) return 0;
        int total = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            total += getLevel(piece, e);
        }
        return total;
    }

    public int handLevel(Player p, String enchantId) {
        CustomEnchant e = byId(enchantId);
        return e == null ? 0 : getLevel(p.getInventory().getItemInMainHand(), e);
    }

    public void depositMoney(java.util.UUID uuid, double amount, String reason) {
        services().get(EconomyService.class).ifPresent(eco -> eco.deposit(uuid, amount, reason));
    }

    // Phoenix : renaissance avec cooldown.
    private final Map<java.util.UUID, Long> phoenixCooldown = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PHOENIX_COOLDOWN_MS = 300_000;

    public boolean tryConsumePhoenix(java.util.UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = phoenixCooldown.get(uuid);
        if (last != null && now - last < PHOENIX_COOLDOWN_MS) return false;
        phoenixCooldown.put(uuid, now);
        return true;
    }

    /** Saignement : inflige des dégâts répétés à la victime sur plusieurs ticks. */
    public void applyBleed(LivingEntity victim, double perTick, int times, long intervalTicks) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = times;
            @Override public void run() {
                if (remaining-- <= 0 || victim.isDead() || !victim.isValid()) {
                    cancel();
                    return;
                }
                double newHealth = victim.getHealth() - perTick;
                if (newHealth <= 0) { victim.setHealth(0); cancel(); }
                else victim.setHealth(newHealth);
            }
        }.runTaskTimer(plugin(), intervalTicks, intervalTicks);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
