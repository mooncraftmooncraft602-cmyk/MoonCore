package com.mooncore.modules.audio;

/**
 * Résout la piste audio active d'un joueur selon l'ordre de priorité obligatoire :
 * <ol>
 *   <li>LOOP GLOBAL</li>
 *   <li>LOOP PLAYER</li>
 *   <li>EVENT (events + boss)</li>
 *   <li>ZONE</li>
 *   <li>DEFAULT</li>
 * </ol>
 * Logique pure et testable.
 */
public final class AudioPriorityResolver {

    private AudioPriorityResolver() {}

    /** Chaque paramètre est l'id de piste de cette source, ou {@code null} si inactive. */
    public static ResolvedAudio resolve(String globalLoop, String playerLoop,
                                        String event, String zone, String def) {
        if (notBlank(globalLoop)) return new ResolvedAudio(globalLoop, AudioSource.GLOBAL_LOOP);
        if (notBlank(playerLoop)) return new ResolvedAudio(playerLoop, AudioSource.PLAYER_LOOP);
        if (notBlank(event)) return new ResolvedAudio(event, AudioSource.EVENT);
        if (notBlank(zone)) return new ResolvedAudio(zone, AudioSource.ZONE);
        if (notBlank(def)) return new ResolvedAudio(def, AudioSource.DEFAULT);
        return null;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
