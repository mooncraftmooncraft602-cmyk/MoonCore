package com.mooncore.modules.audio;

import com.mooncore.api.boss.BossDefeatedEvent;
import com.mooncore.api.boss.BossPhaseChangeEvent;
import com.mooncore.api.boss.BossSpawnEvent;
import com.mooncore.api.event.EventStateEvent;
import com.mooncore.command.sub.AudioLoopSubCommand;
import com.mooncore.command.sub.AudioSubCommand;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * AudioManager : musiques de zones/events/boss/victoire, boucles forcées (globale/joueur),
 * résolution par priorité et transitions. Event-driven + une seule tâche légère (1 s) pour la
 * relance des boucles et le rafraîchissement des zones. S'intègre à Zone/Event/Boss via leurs
 * classes publiques et l'EventBus, sans modifier leur logique.
 */
@ModuleInfo(id = "audio", name = "AudioManager", softDepends = {"zone", "event", "boss"})
public final class AudioManagerModule extends AbstractModule {

    private TrackManager tracks;
    private AudioData data;
    private LoopManager loops;
    private ZoneAudioManager zones;
    private EventAudioManager events;
    private AudioStateManager state;
    private BukkitTask tickTask;

    @Override
    protected void onEnable() {
        this.tracks = new TrackManager(plugin());
        tracks.load();
        this.data = new AudioData(plugin());
        this.loops = new LoopManager(data);
        loops.load();
        this.zones = new ZoneAudioManager(data);
        zones.load();
        this.state = new AudioStateManager(tracks, log());
        state.setMasterVolume((float) moduleConfig().getDouble("master-volume", 1.0));
        this.events = new EventAudioManager(schedulers(), this::applyAll);
        events.configure(moduleConfig());

        registerListener(new AudioListener(this));

        // Intégration découplée via l'EventBus.
        eventBus().subscribe(EventStateEvent.class, e -> {
            if (e.started()) events.onEventStarted(e.eventId()); else events.onEventEnded(e.eventId());
        });
        eventBus().subscribe(BossSpawnEvent.class, e -> events.onBossSpawn());
        eventBus().subscribe(BossPhaseChangeEvent.class, e -> events.onBossPhase());
        eventBus().subscribe(BossDefeatedEvent.class, e -> events.onBossDefeated());

        plugin().rootCommand().register(new AudioSubCommand(this));
        plugin().rootCommand().register(new AudioLoopSubCommand(this));

        // Tâche légère unique : relance des boucles + rafraîchissement zone (1 s).
        tickTask = schedulers().syncTimer(this::applyAll, 20L, 20L);
        applyAll();
    }

    @Override
    protected void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (events != null) events.clear();
        if (state != null) {
            Bukkit.getOnlinePlayers().forEach(state::stop);
            state.clear();
        }
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        reloadAudio();
    }

    public void reloadAudio() {
        tracks.load();
        data.reload();
        loops.load();
        zones.load();
        events.configure(moduleConfig());
        state.setMasterVolume((float) moduleConfig().getDouble("master-volume", 1.0));
        applyAll();
    }

    // ---- Résolution & application ----

    public ResolvedAudio resolve(Player p) {
        return AudioPriorityResolver.resolve(
                loops.global(),
                loops.player(p.getUniqueId()),
                events.currentTrack(),
                zones.trackAt(p.getLocation()),
                moduleConfig().getString("default-track", ""));
    }

    public void applyPlayer(Player p) {
        state.apply(p, resolve(p), System.currentTimeMillis());
    }

    public void applyAll() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            state.apply(p, resolve(p), now);
        }
    }

    /** Réapplique au tick suivant (utile après un téléport, pour une position à jour). */
    public void applyPlayerSoon(Player p) {
        schedulers().syncLater(() -> {
            if (p.isOnline()) applyPlayer(p);
        }, 1L);
    }

    // ---- Accès commandes ----

    public TrackManager tracks() { return tracks; }
    public LoopManager loops() { return loops; }
    public ZoneAudioManager zones() { return zones; }
    public AudioStateManager audioState() { return state; }
    public EventAudioManager events() { return events; }

    /** Lecture directe d'une piste (aperçu admin, non persistant). */
    public boolean playOneShot(Player target, String trackId) {
        Track t = tracks.get(trackId);
        if (t == null) return false;
        state.oneShot(target, t);
        return true;
    }

    public void setMasterVolumePersistent(float v) {
        state.setMasterVolume(v);
        moduleConfig().set("master-volume", (double) v);
        plugin().configManager().saveModuleConfig(id());
        applyAll();
    }
}
