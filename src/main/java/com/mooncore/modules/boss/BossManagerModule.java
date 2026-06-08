package com.mooncore.modules.boss;

import com.mooncore.api.boss.BossDefeatedEvent;
import com.mooncore.api.boss.BossPhaseChangeEvent;
import com.mooncore.api.boss.BossSpawnEvent;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.reward.RewardService;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.command.sub.BossSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Attrs;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossManager : boss data-driven (YAML), multi-phases, capacités (dash, téléport, explosion,
 * invocation, poison, feu, régén, bouclier, AoE), barre de boss, suivi des dégâts et loot.
 */
@ModuleInfo(id = "boss", name = "BossManager", softDepends = {"reward", "progression", "statistics", "zone"})
public final class BossManagerModule extends AbstractModule {

    private final Map<String, BossDefinition> registry = new ConcurrentHashMap<>();
    private final Map<String, File> definitionFiles = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveBoss> active = new ConcurrentHashMap<>();
    private NamespacedKey bossKey;
    private BukkitTask tickTask;

    @Override
    protected void onEnable() {
        this.bossKey = new NamespacedKey(plugin(), "boss");
        loadBosses();
        registerListener(new BossListener(this));
        plugin().rootCommand().register(new BossSubCommand(this));
        // Boucle d'IA throttlée (toutes les 10 ticks = 0,5 s).
        tickTask = schedulers().syncTimer(this::tickAll, 20L, 10L);
    }

    @Override
    protected void onDisable() {
        if (tickTask != null) tickTask.cancel();
        // Retire les barres et les boss encore vivants.
        for (ActiveBoss ab : active.values()) {
            ab.bar().removeAll();
            if (!ab.entity().isDead()) ab.entity().remove();
        }
        active.clear();
        definitionFiles.clear();
    }

    @Override
    protected void onReload() {
        registry.clear();
        definitionFiles.clear();
        loadBosses();
    }

    private void loadBosses() {
        File dir = new File(plugin().getDataFolder(), "content/bosses");
        if (!dir.exists()) {
            dir.mkdirs();
            if (plugin().getResource("content/bosses/example.yml") != null) {
                plugin().saveResource("content/bosses/example.yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = yml.getConfigurationSection("bosses");
            if (sec == null) continue;
            for (String id : sec.getKeys(false)) {
                String norm = id.toLowerCase(Locale.ROOT);
                ConfigurationSection b = sec.getConfigurationSection(id);
                if (b == null) continue;
                BossDefinition def = parseBoss(norm, b);
                if (def != null) {
                    registry.put(norm, def);
                    definitionFiles.put(norm, f);
                }
            }
        }
        log().info("BossManager : " + registry.size() + " boss chargé(s).");
    }

    /**
     * Crée/écrase un boss à partir de données (généralement produites par l'IA), avec
     * bornage anti-cheat, persiste en YAML ({@code content/bosses/ai-<id>.yml}) et
     * l'enregistre immédiatement. Retourne false si invalide.
     */
    public boolean createBoss(String id, java.util.Map<String, Object> fields) {
        String safeId = id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (safeId.isBlank()) return false;
        // Bornage anti-cheat des valeurs numériques clés.
        clampField(fields, "max-health", 10, 5000);
        clampField(fields, "damage", 1, 40);
        clampField(fields, "speed", 0.1, 0.6);
        clampField(fields, "armor", 0, 30);

        File dir = new File(plugin().getDataFolder(), "content/bosses");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "ai-" + safeId + ".yml");
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        yml.set("bosses." + safeId, null);
        yml.createSection("bosses." + safeId, fields);
        try {
            yml.save(f);
        } catch (java.io.IOException e) {
            log().error("Échec d'écriture du boss IA " + safeId, e);
            return false;
        }
        BossDefinition def = parseBoss(safeId, yml.getConfigurationSection("bosses." + safeId));
        if (def == null) return false;
        registry.put(safeId, def);
        definitionFiles.put(safeId, f);
        return true;
    }

    private static void clampField(java.util.Map<String, Object> m, String key, double min, double max) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            m.put(key, Math.max(min, Math.min(max, n.doubleValue())));
        }
    }

    private BossDefinition parseBoss(String id, ConfigurationSection b) {
        try {
            EntityType type = EntityType.valueOf(b.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT));
            List<BossPhase> phases = new ArrayList<>();
            ConfigurationSection ph = b.getConfigurationSection("phases");
            if (ph != null) {
                for (String pkey : ph.getKeys(false)) {
                    ConfigurationSection p = ph.getConfigurationSection(pkey);
                    if (p == null) continue;
                    List<AbilityInstance> abilities = new ArrayList<>();
                    for (Map<?, ?> am : p.getMapList("abilities")) {
                        AbilityInstance ai = parseAbility(am);
                        if (ai != null) abilities.add(ai);
                    }
                    phases.add(new BossPhase(pkey, p.getDouble("from-percent", 100), abilities));
                }
            }
            return new BossDefinition(id,
                    b.getString("display-name", id),
                    type,
                    b.getDouble("max-health", 200),
                    b.getDouble("damage", 8),
                    b.getDouble("speed", 0.25),
                    b.getDouble("armor", 0),
                    phases,
                    emptyToNull(b.getString("loot-reward", "")),
                    b.getLong("progression-xp", 0),
                    b.getString("bar-color", "PURPLE"),
                    emptyToNull(b.getString("texture-key", "")),
                    b.getInt("texture-custom-model-data", textureModelData(id)));
        } catch (IllegalArgumentException e) {
            log().warn("Boss invalide ignoré : " + id + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private AbilityInstance parseAbility(Map<?, ?> m) {
        Object t = m.get("type");
        if (t == null) return null;
        try {
            BossAbilityType type = BossAbilityType.valueOf(t.toString().toUpperCase(Locale.ROOT));
            return new AbilityInstance(type,
                    toLong(m.get("cooldown-ticks"), 60),
                    toDouble(m.get("magnitude"), 1),
                    (int) toLong(m.get("count"), 1),
                    toDouble(m.get("radius"), 8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- Spawn ----

    public boolean spawn(String bossId, Location loc) {
        bossId = bossId == null ? "" : bossId.toLowerCase(Locale.ROOT);
        BossDefinition def = registry.get(bossId);
        if (def == null || loc.getWorld() == null) return false;

        Location safe = safeSpawn(loc);                         // jamais en cave / dans un bloc
        org.bukkit.entity.Entity spawned;
        try {
            spawned = safe.getWorld().spawnEntity(safe, def.entityType());
        } catch (IllegalArgumentException ex) {
            log().warn("Boss " + bossId + " : type d'entité « " + def.entityType()
                    + " » non invocable (non-spawnable), spawn ignoré.");
            return false;
        }
        if (!(spawned instanceof LivingEntity entity)) {
            spawned.remove();
            return false;
        }
        entity.customName(Text.mm(def.displayName()));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setGlowing(true);                                // visible à travers les blocs
        entity.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, bossId);

        setAttr(entity, Attrs.MAX_HEALTH, def.maxHealth());
        entity.setHealth(def.maxHealth());
        setAttr(entity, Attrs.ATTACK_DAMAGE, def.damage());
        setAttr(entity, Attrs.MOVEMENT_SPEED, def.speed());
        if (def.armor() > 0) setAttr(entity, Attrs.ARMOR, def.armor());
        applyTextureCosmetic(entity, def);

        BarColor color;
        try { color = BarColor.valueOf(def.barColor().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { color = BarColor.PURPLE; }
        BossBar bar = Bukkit.createBossBar(Text.strip(def.displayName()), color, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        loc.getWorld().getPlayers().forEach(bar::addPlayer);

        active.put(entity.getUniqueId(), new ActiveBoss(entity, def, bar));
        eventBus().post(new BossSpawnEvent(bossId, entity.getUniqueId()));
        Bukkit.broadcast(plugin().configManager().message("boss-spawned", "name", def.displayName()));
        return true;
    }

    private void setAttr(LivingEntity e, Attribute attr, double value) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private void applyTextureCosmetic(LivingEntity entity, BossDefinition def) {
        if (def.textureKey() == null) return;
        File png = new File(texturesFolder(), def.textureKey() + ".png");
        if (!png.isFile() || entity.getEquipment() == null) return;
        ItemStack hat = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = hat.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(def.displayName())
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.setCustomModelData(def.textureCustomModelData() > 0
                    ? def.textureCustomModelData() : textureModelData(def.id()));
            meta.addItemFlags(ItemFlag.values());
            hat.setItemMeta(meta);
        }
        entity.getEquipment().setHelmet(hat);
        entity.getEquipment().setHelmetDropChance(0f);
    }

    /**
     * Retourne un point de spawn sûr : si la position est sous terre (cave) ou obstruée
     * (dans un bloc), on remonte à la surface (plus haut bloc + 1). Évite les boss
     * coincés dans la roche ou invisibles dans une grotte.
     */
    private Location safeSpawn(Location loc) {
        var w = loc.getWorld();
        if (w == null) return loc;
        int x = loc.getBlockX(), z = loc.getBlockZ(), y = loc.getBlockY();
        int surface = w.getHighestBlockYAt(x, z);
        boolean underground = y <= surface;                       // des blocs au-dessus → cave/sous-sol
        boolean obstructed = !w.getBlockAt(x, y, z).isPassable()
                || !w.getBlockAt(x, y + 1, z).isPassable();
        if (underground || obstructed) {
            return new Location(w, x + 0.5, surface + 1, z + 0.5, loc.getYaw(), loc.getPitch());
        }
        return loc;
    }

    // ---- Boucle d'IA ----

    private void tickAll() {
        if (active.isEmpty()) return;
        long now = Bukkit.getCurrentTick();
        for (var it = active.values().iterator(); it.hasNext(); ) {
            ActiveBoss ab = it.next();
            LivingEntity e = ab.entity();
            if (e.isDead() || !e.isValid()) {       // boss retiré sans EntityDeathEvent (kill externe, unload du monde…)
                ab.bar().removeAll();               // évite la barre fantôme persistante + la fuite de l'entrée
                it.remove();
                continue;
            }
            // Barre live : tout joueur entré dans le monde après le spawn voit la barre (addPlayer est idempotent).
            e.getWorld().getPlayers().forEach(ab.bar()::addPlayer);
            double max = attrValue(e, Attrs.MAX_HEALTH, ab.definition().maxHealth());
            double pct = (e.getHealth() / max) * 100.0;
            ab.bar().setProgress(Math.max(0, Math.min(1, e.getHealth() / max)));

            // Coordonnées + monde affichés dans le titre de la barre (live).
            Location bl = e.getLocation();
            ab.bar().setTitle(Text.strip(ab.definition().displayName())
                    + " §7§o" + bl.getBlockX() + ", " + bl.getBlockY() + ", " + bl.getBlockZ()
                    + " §8(" + bl.getWorld().getName() + ")");

            BossPhase phase = PhaseSelector.select(pct, ab.definition().phases());
            if (phase == null) continue;
            if (!phase.name().equals(ab.currentPhase())) {
                ab.setCurrentPhase(phase.name());
                eventBus().post(new BossPhaseChangeEvent(ab.definition().id(), e.getUniqueId(), phase.name()));
            }
            for (AbilityInstance ai : phase.abilities()) {
                if (ab.canUse(ai.type(), now, ai.cooldownTicks())) {
                    executeAbility(ab, ai);
                    ab.markUsed(ai.type(), now);
                }
            }
        }
    }

    private void executeAbility(ActiveBoss ab, AbilityInstance ai) {
        LivingEntity boss = ab.entity();
        Location loc = boss.getLocation();
        Collection<Player> nearby = loc.getNearbyPlayers(ai.radius());
        switch (ai.type()) {
            case SUMMON -> {
                for (int i = 0; i < ai.count(); i++) {
                    loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
                }
            }
            case HEAL -> {
                double max = attrValue(boss, Attrs.MAX_HEALTH, ab.definition().maxHealth());
                boss.setHealth(Math.min(max, boss.getHealth() + ai.magnitude()));
            }
            case POISON -> nearby.forEach(p -> p.addPotionEffect(
                    new PotionEffect(PotionEffectType.POISON, (int) ai.magnitude(), 1)));
            case IGNITE -> nearby.forEach(p -> p.setFireTicks((int) ai.magnitude()));
            case EXPLODE -> loc.getWorld().createExplosion(loc, (float) ai.magnitude(), false, false, boss);
            case AOE_DAMAGE -> nearby.forEach(p -> p.damage(ai.magnitude(), boss));
            case TELEPORT -> {
                Player target = firstOrNull(nearby);
                if (target != null) boss.teleport(target.getLocation());
            }
            case DASH -> {
                Player target = nearest(boss, nearby);
                if (target != null) {
                    Vector dir = target.getLocation().toVector().subtract(loc.toVector());
                    if (dir.lengthSquared() > 0) boss.setVelocity(dir.normalize().multiply(ai.magnitude()));
                }
            }
            case SHIELD -> boss.addPotionEffect(
                    new PotionEffect(PotionEffectType.RESISTANCE, (int) ai.magnitude(), 2));
        }
    }

    // ---- Dégâts / mort (appelé par le listener) ----

    public boolean isBoss(org.bukkit.entity.Entity e) {
        return active.containsKey(e.getUniqueId());
    }

    public void recordDamage(UUID entityId, UUID player, double amount) {
        ActiveBoss ab = active.get(entityId);
        if (ab != null) ab.addDamage(player, amount);
    }

    public void updateBarViewers(UUID entityId, Player player, boolean add) {
        ActiveBoss ab = active.get(entityId);
        if (ab == null) return;
        if (add) ab.bar().addPlayer(player); else ab.bar().removePlayer(player);
    }

    public void handleDeath(UUID entityId) {
        ActiveBoss ab = active.remove(entityId);
        if (ab == null) return;
        ab.bar().removeAll();
        BossDefinition def = ab.definition();
        UUID top = ab.topDamager();

        eventBus().post(new BossDefeatedEvent(def.id(), top, Map.copyOf(ab.damageByPlayer())));
        Bukkit.broadcast(plugin().configManager().message("boss-defeated", "name", def.displayName()));

        if (top != null) {
            Player killer = Bukkit.getPlayer(top);
            if (killer != null) {
                if (def.lootRewardId() != null) {
                    services().get(RewardService.class).ifPresent(r -> r.give(killer, def.lootRewardId()));
                }
                if (def.progressionXp() > 0) {
                    services().get(ProgressionService.class)
                            .ifPresent(p -> p.addXp(top, def.progressionXp(), "boss:" + def.id()));
                }
            }
            services().get(StatisticsService.class)
                    .ifPresent(s -> s.increment(top, StatKeys.BOSS_KILLS, 1, "boss:" + def.id()));
        }
    }

    // ---- Helpers ----

    public Collection<String> bossIds() { return registry.keySet(); }
    public boolean exists(String id) { return id != null && registry.containsKey(id.toLowerCase(Locale.ROOT)); }
    public int activeCount() { return active.size(); }
    public Map<String, BossDefinition> rawDefs() { return Map.copyOf(registry); }

    public File texturesFolder() {
        File f = new File(plugin().getDataFolder(), "boss-textures");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public BossDefinition definition(String id) {
        return id == null ? null : registry.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean setTexture(String bossId, String textureKey, int customModelData) {
        String id = bossId == null ? "" : bossId.toLowerCase(Locale.ROOT);
        BossDefinition current = registry.get(id);
        if (current == null) return false;
        String key = sanitizeTextureKey(textureKey == null || textureKey.isBlank() ? id : textureKey);
        File src = definitionFiles.get(id);
        if (src == null) return false;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(src);
        String path = "bosses." + id;
        if (!yml.contains(path)) return false;
        yml.set(path + ".texture-key", key);
        yml.set(path + ".texture-custom-model-data", customModelData > 0 ? customModelData : textureModelData(id));
        try {
            yml.save(src);
        } catch (java.io.IOException e) {
            log().error("Échec d'écriture de la texture du boss " + id, e);
            return false;
        }
        BossDefinition updated = parseBoss(id, yml.getConfigurationSection(path));
        if (updated != null) registry.put(id, updated);
        return updated != null;
    }

    public static int textureModelData(String bossId) {
        int h = Math.floorMod((bossId == null ? "boss" : bossId.toLowerCase(Locale.ROOT)).hashCode(), 100000);
        return 780000 + h;
    }

    private static String sanitizeTextureKey(String key) {
        String s = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return s.isBlank() ? "boss" : s;
    }

    private double attrValue(LivingEntity e, Attribute attr, double def) {
        AttributeInstance inst = e.getAttribute(attr);
        return inst != null ? inst.getValue() : def;
    }

    private static Player firstOrNull(Collection<Player> players) {
        return players.isEmpty() ? null : players.iterator().next();
    }

    private static Player nearest(LivingEntity from, Collection<Player> players) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(from.getLocation());
            if (d < bestDist) { bestDist = d; best = p; }
        }
        return best;
    }

    private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
    private static long toLong(Object o, long def) { return (o instanceof Number n) ? n.longValue() : def; }
    private static double toDouble(Object o, double def) { return (o instanceof Number n) ? n.doubleValue() : def; }
}
