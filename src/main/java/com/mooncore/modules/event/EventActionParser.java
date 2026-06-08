package com.mooncore.modules.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parse les actions d'événement depuis le YAML (liste de maps). Logique pure. */
public final class EventActionParser {

    private EventActionParser() {}

    public static List<EventAction> parse(List<? extends Map<?, ?>> raw) {
        List<EventAction> out = new ArrayList<>();
        if (raw == null) return out;
        for (Map<?, ?> m : raw) {
            EventAction a = parseOne(m);
            if (a != null) out.add(a);
        }
        return out;
    }

    static EventAction parseOne(Map<?, ?> m) {
        Object t = m.get("type");
        if (t == null) return null;
        EventActionType type;
        try {
            type = EventActionType.valueOf(t.toString().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!"type".equals(e.getKey())) {
                params.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return new EventAction(type, params);
    }
}
