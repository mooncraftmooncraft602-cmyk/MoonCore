package com.mooncore.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownsTest {

    @Test
    void acquireThenBlockedUntilExpiry() {
        Cooldowns<UUID> cd = new Cooldowns<>();
        UUID k = UUID.randomUUID();

        assertTrue(cd.tryAcquire(k, 1000, 5000));   // premier usage : ok
        assertFalse(cd.tryAcquire(k, 2000, 5000));  // encore en cooldown
        assertEquals(4000, cd.remaining(k, 2000, 5000));
        assertTrue(cd.tryAcquire(k, 6000, 5000));   // expiré → ok
    }

    @Test
    void unknownKeyIsReady() {
        Cooldowns<String> cd = new Cooldowns<>();
        assertEquals(0, cd.remaining("x", 0, 1000));
    }
}
