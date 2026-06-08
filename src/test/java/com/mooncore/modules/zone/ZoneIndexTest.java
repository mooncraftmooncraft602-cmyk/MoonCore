package com.mooncore.modules.zone;

import com.mooncore.api.zone.Region;
import com.mooncore.api.zone.ZoneFlag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneIndexTest {

    private Region region(String name, int x1, int z1, int x2, int z2, int prio) {
        return new Region(name, "world", x1, 0, z1, x2, 255, z2, prio);
    }

    @Test
    void containsRespectsBounds() {
        Region r = region("a", 0, 0, 16, 16, 0);
        assertTrue(r.contains("world", 8, 64, 8));
        assertFalse(r.contains("world", 100, 64, 8));
        assertFalse(r.contains("nether", 8, 64, 8));
    }

    @Test
    void flagSerializationRoundTrip() {
        Region r = region("a", 0, 0, 16, 16, 0);
        r.setFlag(ZoneFlag.NO_BLOCK_BREAK, true);
        r.setFlag(ZoneFlag.NO_PVP, false);
        String raw = ZoneStore.serializeFlags(r);

        Region copy = region("a", 0, 0, 16, 16, 0);
        ZoneStore.deserializeFlags(raw, copy);
        assertEquals(Boolean.TRUE, copy.flag(ZoneFlag.NO_BLOCK_BREAK));
        assertEquals(Boolean.FALSE, copy.flag(ZoneFlag.NO_PVP));
        assertNull(copy.flag(ZoneFlag.NO_TPA));
    }

    @Test
    void flagKeysAreResolvable() {
        assertEquals(ZoneFlag.NO_BLOCK_BREAK, ZoneFlag.byKey("noblockbreak").orElseThrow());
        assertEquals(ZoneFlag.NO_TPA, ZoneFlag.byKey("notpa").orElseThrow());
        assertEquals(ZoneFlag.FORCE_PVP, ZoneFlag.byKey("forcepvp").orElseThrow());
        assertTrue(ZoneFlag.byKey("doesnotexist").isEmpty());
    }
}
