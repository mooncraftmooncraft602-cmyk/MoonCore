package com.mooncore.modules.audio;

import com.mooncore.util.Schedulers;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Pilote la piste audio "EVENT" (events + boss) selon les déclencheurs reçus de l'EventBus :
 * start d'event, spawn de boss, changement de phase (intensification), victoire. Une seule
 * piste EVENT active à la fois (dernier déclencheur gagnant).
 */
public final class EventAudioManager {

    private final Schedulers schedulers;
    private final Runnable reapplyAll;

    private String eventDefaultTrack = "";
    private final Map<String, String> perEvent = new HashMap<>();
    private String bossSpawnTrack = "";
    private String bossPhaseTrack = "";
    private String victoryTrack = "";
    private int victorySeconds = 8;

    private volatile String currentTrack;
    private BukkitTask victoryTask;

    public EventAudioManager(Schedulers schedulers, Runnable reapplyAll) {
        this.schedulers = schedulers;
        this.reapplyAll = reapplyAll;
    }

    public void configure(ConfigurationSection cfg) {
        perEvent.clear();
        if (cfg == null) return;
        this.eventDefaultTrack = cfg.getString("events.default-track", "");
        ConfigurationSection per = cfg.getConfigurationSection("events.per-event");
        if (per != null) {
            for (String id : per.getKeys(false)) perEvent.put(id, per.getString(id));
        }
        this.bossSpawnTrack = cfg.getString("boss.spawn-track", "");
        this.bossPhaseTrack = cfg.getString("boss.phase-track", "");
        this.victoryTrack = cfg.getString("boss.victory-track", "");
        this.victorySeconds = cfg.getInt("boss.victory-seconds", 8);
    }

    public String currentTrack() {
        return currentTrack;
    }

    // ---- Déclencheurs ----

    public void onEventStarted(String eventId) {
        String track = perEvent.getOrDefault(eventId, eventDefaultTrack);
        if (notBlank(track)) setCurrent(track);
    }

    public void onEventEnded(String eventId) {
        setCurrent(null);
    }

    public void onBossSpawn() {
        if (notBlank(bossSpawnTrack)) setCurrent(bossSpawnTrack);
    }

    public void onBossPhase() {
        if (notBlank(bossPhaseTrack)) setCurrent(bossPhaseTrack);
    }

    public void onBossDefeated() {
        if (!notBlank(victoryTrack)) { setCurrent(null); return; }
        setCurrent(victoryTrack);
        if (victoryTask != null) victoryTask.cancel();
        victoryTask = schedulers.syncLater(() -> setCurrent(null), victorySeconds * 20L);
    }

    private void setCurrent(String track) {
        this.currentTrack = (track == null || track.isBlank()) ? null : track;
        reapplyAll.run();
    }

    public void clear() {
        if (victoryTask != null) victoryTask.cancel();
        currentTrack = null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
