package com.mooncore.modules.audio;

import com.mooncore.util.MoonLogger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère l'état de lecture par joueur : applique la piste résolue, évite les doublons (ne
 * rejoue que si la piste change), gère la relance des boucles et coupe l'ancienne piste
 * avant la nouvelle (anti-superposition). La "transition" reste une coupe nette suivie d'une
 * relance (Minecraft n'expose pas de fondu réel sur un son en cours — voir REVIEW audio).
 */
public final class AudioStateManager {

    private record Playing(ResolvedAudio resolved, long startMs, String soundKey) {}

    private final TrackManager tracks;
    private final MoonLogger logger;
    private final Map<UUID, Playing> current = new ConcurrentHashMap<>();
    private volatile float masterVolume = 1.0f;

    public AudioStateManager(TrackManager tracks, MoonLogger logger) {
        this.tracks = tracks;
        this.logger = logger;
    }

    public void setMasterVolume(float v) {
        this.masterVolume = Math.max(0f, Math.min(1f, v));
    }

    public float masterVolume() {
        return masterVolume;
    }

    /** Détection heuristique Bedrock (UUID Floodgate : bits de poids fort à 0). */
    public static boolean isBedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0L;
    }

    /** Applique la piste résolue à un joueur (idempotent : ne rejoue que si nécessaire). */
    public void apply(Player p, ResolvedAudio resolved, long nowMs) {
        UUID id = p.getUniqueId();
        if (resolved == null) {
            stop(p);
            return;
        }
        Track track = tracks.get(resolved.trackId());
        if (track == null) return;

        Playing cur = current.get(id);
        boolean sameTrack = cur != null && cur.resolved().trackId().equals(resolved.trackId());

        if (!sameTrack) {
            if (cur != null) stopKey(p, cur.soundKey());           // anti-superposition
            String key = play(p, track);
            current.put(id, new Playing(resolved, nowMs, key));
        } else if (cur != null && track.loop() && nowMs - cur.startMs() >= track.lengthSeconds() * 1000L) {
            // Relance de boucle (pas de boucle native MC) : on coupe l'instance précédente
            // avant de rejouer pour éviter toute superposition si la durée configurée diffère.
            stopKey(p, cur.soundKey());
            String nkey = play(p, track);
            current.put(id, new Playing(resolved, nowMs, nkey));
        }
    }

    /** Lecture directe d'une piste (aperçu, non suivie par l'état). */
    public void oneShot(Player p, Track track) {
        play(p, track);
    }

    private String play(Player p, Track track) {
        String key = track.soundFor(isBedrock(p));
        try {
            Sound sound = Sound.sound(Key.key(key), Sound.Source.MUSIC,
                    track.volume() * masterVolume, track.pitch());
            p.playSound(sound);
        } catch (Exception e) {
            logger.warn("Clé sonore invalide pour la piste " + track.id() + " : " + key);
        }
        return key;
    }

    public void stop(Player p) {
        Playing cur = current.remove(p.getUniqueId());
        if (cur != null) stopKey(p, cur.soundKey());
    }

    private void stopKey(Player p, String key) {
        try {
            p.stopSound(SoundStop.named(Key.key(key)));
        } catch (Exception ignored) {
            p.stopSound(SoundStop.source(Sound.Source.MUSIC));
        }
    }

    public void remove(UUID uuid) {
        current.remove(uuid);
    }

    public ResolvedAudio currentOf(UUID uuid) {
        Playing pl = current.get(uuid);
        return pl == null ? null : pl.resolved();
    }

    public void clear() {
        current.clear();
    }
}
