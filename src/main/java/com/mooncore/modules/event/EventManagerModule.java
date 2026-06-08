package com.mooncore.modules.event;

import com.mooncore.api.event.EventStateEvent;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.reward.RewardService;
import com.mooncore.api.zone.ZoneFlag;
import com.mooncore.command.sub.EventSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.zone.ZoneModule;
import com.mooncore.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EventManager : framework d'événements data-driven (boss, PvP, chasse au trésor,
 * temporaires/saisonniers). Les admins composent des actions en YAML ; démarrage manuel
 * ou automatique périodique. Intègre Boss, Reward, Zone et Progression.
 */
@ModuleInfo(id = "event", name = "EventManager",
        softDepends = {"boss", "reward", "zone", "progression"})
public final class EventManagerModule extends AbstractModule {

    private final Map<String, GameEvent> registry = new LinkedHashMap<>();
    private final Map<String, BukkitTask> activeEndTasks = new ConcurrentHashMap<>();
    private final List<BukkitTask> autoTasks = new ArrayList<>();

    @Override
    protected void onEnable() {
        loadEvents();
        plugin().rootCommand().register(new EventSubCommand(this));
        scheduleAuto();
        log().info("EventManager : " + registry.size() + " événement(s).");
    }

    @Override
    protected void onDisable() {
        autoTasks.forEach(BukkitTask::cancel);
        autoTasks.clear();
        activeEndTasks.values().forEach(BukkitTask::cancel);
        activeEndTasks.clear();
    }

    @Override
    protected void onReload() {
        // Termine proprement les événements en cours AVANT de vider : exécute leurs end-actions
        // (ex. retrait d'un flag de zone) et poste EventStateEvent(false) (sinon musique/ambiance
        // d'événement et flags restent bloqués jusqu'au prochain cycle complet).
        new java.util.ArrayList<>(activeIds()).forEach(this::stop);
        onDisable();
        registry.clear();
        loadEvents();
        scheduleAuto();
    }

    private void loadEvents() {
        File dir = new File(plugin().getDataFolder(), "content/events");
        if (!dir.exists()) {
            dir.mkdirs();
            if (plugin().getResource("content/events/example.yml") != null) {
                plugin().saveResource("content/events/example.yml", false);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = yml.getConfigurationSection("events");
            if (sec == null) continue;
            for (String id : sec.getKeys(false)) {
                ConfigurationSection ev = sec.getConfigurationSection(id);
                if (ev == null) continue;
                registry.put(id, new GameEvent(id,
                        ev.getString("display-name", id),
                        ev.getString("type", "GENERIC"),
                        ev.getLong("duration-seconds", 300),
                        ev.getLong("auto-interval-seconds", 0),
                        EventActionParser.parse(ev.getMapList("start-actions")),
                        EventActionParser.parse(ev.getMapList("end-actions"))));
            }
        }
    }

    private void scheduleAuto() {
        for (GameEvent ev : registry.values()) {
            if (ev.autoIntervalSeconds() > 0) {
                long t = ev.autoIntervalSeconds() * 20L;
                autoTasks.add(schedulers().syncTimer(() -> start(ev.id()), t, t));
            }
        }
    }

    // ---- API ----

    public Set<String> eventIds() { return registry.keySet(); }
    public boolean exists(String id) { return registry.containsKey(id); }
    public boolean isActive(String id) { return activeEndTasks.containsKey(id); }
    public Set<String> activeIds() { return activeEndTasks.keySet(); }

    public boolean start(String id) {
        GameEvent ev = registry.get(id);
        if (ev == null || activeEndTasks.containsKey(id)) return false;

        executeActions(ev.startActions());
        eventBus().post(new EventStateEvent(id, true));

        BukkitTask end = schedulers().syncLater(() -> finish(id), ev.durationSeconds() * 20L);
        activeEndTasks.put(id, end);
        return true;
    }

    public boolean stop(String id) {
        BukkitTask task = activeEndTasks.remove(id);
        if (task == null) return false;
        task.cancel();
        finishActions(id);
        return true;
    }

    private void finish(String id) {
        activeEndTasks.remove(id);
        finishActions(id);
    }

    private void finishActions(String id) {
        GameEvent ev = registry.get(id);
        if (ev != null) executeActions(ev.endActions());
        eventBus().post(new EventStateEvent(id, false));
    }

    // ---- Exécution des actions ----

    private void executeActions(List<EventAction> actions) {
        for (EventAction a : actions) {
            try {
                execute(a);
            } catch (Exception e) {
                log().error("Action d'événement en erreur : " + a.type(), e);
            }
        }
    }

    private void execute(EventAction a) {
        switch (a.type()) {
            case BROADCAST -> Bukkit.broadcast(Text.mm(a.str("value", "")));
            case TITLE -> {
                Title title = Title.title(Text.mm(a.str("title", "")), Text.mm(a.str("subtitle", "")));
                Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
            }
            case SOUND -> {
                org.bukkit.NamespacedKey sk = org.bukkit.NamespacedKey.minecraft(
                        a.str("sound", "").toLowerCase(Locale.ROOT));
                Sound sound = org.bukkit.Registry.SOUNDS.get(sk);
                if (sound != null) {
                    Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), sound, 1f, 1f));
                }
            }
            case COMMAND -> {
                String cmd = a.str("command", "");
                if (cmd.contains("%player%")) {
                    Bukkit.getOnlinePlayers().forEach(p ->
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName())));
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
            case SPAWN_BOSS -> {
                BossManagerModule boss = plugin().moduleManager().get(BossManagerModule.class);
                World world = Bukkit.getWorld(a.str("world", "world"));
                if (boss != null && world != null) {
                    Location loc = new Location(world, a.num("x", 0), a.num("y", 64), a.num("z", 0));
                    boss.spawn(a.str("boss", ""), loc);
                }
            }
            case REWARD_ALL -> services().get(RewardService.class).ifPresent(r -> {
                String reward = a.str("reward", "");
                Bukkit.getOnlinePlayers().forEach(p -> r.give(p, reward));
            });
            case XP_ALL -> services().get(ProgressionService.class).ifPresent(pr -> {
                long amount = (long) a.num("amount", 0);
                Bukkit.getOnlinePlayers().forEach(p -> pr.addXp(p.getUniqueId(), amount, "event"));
            });
            case ZONE_FLAG -> {
                ZoneModule zone = plugin().moduleManager().get(ZoneModule.class);
                if (zone != null) {
                    ZoneFlag.byKey(a.str("flag", "")).ifPresent(flag ->
                            zone.setFlag(a.str("region", ""), flag, a.bool("flag-value", true)));
                }
            }
        }
    }
}
