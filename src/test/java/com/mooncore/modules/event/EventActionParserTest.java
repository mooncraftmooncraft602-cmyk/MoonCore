package com.mooncore.modules.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventActionParserTest {

    @Test
    void parsesActionsAndParams() {
        List<Map<?, ?>> raw = List.of(
                Map.of("type", "BROADCAST", "value", "hello"),
                Map.of("type", "SPAWN_BOSS", "boss", "warlord", "world", "world", "x", 10, "y", 70, "z", -5),
                Map.of("type", "XP_ALL", "amount", 500));

        List<EventAction> actions = EventActionParser.parse(raw);
        assertEquals(3, actions.size());

        assertEquals(EventActionType.BROADCAST, actions.get(0).type());
        assertEquals("hello", actions.get(0).str("value", ""));

        EventAction boss = actions.get(1);
        assertEquals("warlord", boss.str("boss", ""));
        assertEquals(10, boss.num("x", 0), 1e-9);
        assertEquals(-5, boss.num("z", 0), 1e-9);

        assertEquals(500, actions.get(2).num("amount", 0), 1e-9);
    }

    @Test
    void skipsUnknownAndTypeless() {
        List<Map<?, ?>> raw = List.of(
                Map.of("value", "x"),          // pas de type
                Map.of("type", "NOPE"),        // type inconnu
                Map.of("type", "TITLE", "title", "t"));
        List<EventAction> actions = EventActionParser.parse(raw);
        assertEquals(1, actions.size());
        assertEquals(EventActionType.TITLE, actions.get(0).type());
    }

    @Test
    void typelessReturnsNull() {
        assertNull(EventActionParser.parseOne(Map.of("value", "x")));
    }
}
