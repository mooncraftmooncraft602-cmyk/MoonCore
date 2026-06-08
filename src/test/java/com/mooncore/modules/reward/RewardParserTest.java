package com.mooncore.modules.reward;

import com.mooncore.api.reward.Reward;
import com.mooncore.api.reward.RewardAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardParserTest {

    @Test
    void parsesMixedActions() {
        List<Map<?, ?>> actions = List.of(
                Map.of("type", "MONEY", "amount", 250),
                Map.of("type", "XP", "amount", 50),
                Map.of("type", "ITEM", "material", "DIAMOND", "amount", 5,
                        "name", "<aqua>Éclat</aqua>", "lore", List.of("<gray>l1</gray>")),
                Map.of("type", "COMMAND", "command", "say hi %player%"),
                Map.of("type", "STAT", "key", "boss_kills", "amount", 1));

        Reward r = RewardParser.parse("test", actions);
        assertEquals("test", r.id());
        assertEquals(5, r.actions().size());

        assertEquals(RewardAction.Type.MONEY, r.actions().get(0).type());
        assertEquals(250, r.actions().get(0).amount(), 1e-9);

        RewardAction item = r.actions().get(2);
        assertEquals(RewardAction.Type.ITEM, item.type());
        assertEquals("DIAMOND", item.item().material());
        assertEquals(5, item.item().amount());
        assertEquals(1, item.item().lore().size());

        assertEquals("say hi %player%", r.actions().get(3).text());
        assertEquals("boss_kills", r.actions().get(4).text());
        assertEquals(1, r.actions().get(4).amount(), 1e-9);
    }

    @Test
    void skipsUnknownOrTypelessActions() {
        List<Map<?, ?>> actions = List.of(
                Map.of("amount", 10),                       // pas de type
                Map.of("type", "NOPE"),                     // type inconnu
                Map.of("type", "MESSAGE", "value", "hi"));  // valide

        Reward r = RewardParser.parse("x", actions);
        assertEquals(1, r.actions().size());
        assertEquals(RewardAction.Type.MESSAGE, r.actions().get(0).type());
    }

    @Test
    void itemSpecDefaultsAmountToOne() {
        var action = RewardParser.parseAction(Map.of("type", "ITEM", "material", "STONE"));
        assertTrue(action != null);
        assertEquals(1, action.item().amount());
    }

    @Test
    void typelessReturnsNull() {
        assertNull(RewardParser.parseAction(Map.of("amount", 5)));
    }
}
