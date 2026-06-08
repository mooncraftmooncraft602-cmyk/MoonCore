package com.mooncore.modules.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AudioPriorityResolverTest {

    @Test
    void globalLoopHasMaxPriority() {
        ResolvedAudio r = AudioPriorityResolver.resolve("g", "p", "e", "z", "d");
        assertEquals("g", r.trackId());
        assertEquals(AudioSource.GLOBAL_LOOP, r.source());
    }

    @Test
    void playerLoopBeatsEventZoneDefault() {
        ResolvedAudio r = AudioPriorityResolver.resolve(null, "p", "e", "z", "d");
        assertEquals("p", r.trackId());
        assertEquals(AudioSource.PLAYER_LOOP, r.source());
    }

    @Test
    void eventBeatsZoneAndDefault() {
        ResolvedAudio r = AudioPriorityResolver.resolve(null, null, "e", "z", "d");
        assertEquals(AudioSource.EVENT, r.source());
    }

    @Test
    void zoneBeatsDefault() {
        ResolvedAudio r = AudioPriorityResolver.resolve(null, null, null, "z", "d");
        assertEquals(AudioSource.ZONE, r.source());
    }

    @Test
    void defaultWhenNothingElse() {
        ResolvedAudio r = AudioPriorityResolver.resolve(null, null, null, null, "d");
        assertEquals(AudioSource.DEFAULT, r.source());
    }

    @Test
    void nullWhenAllInactiveOrBlank() {
        assertNull(AudioPriorityResolver.resolve(null, "", "  ", null, ""));
    }
}
