package com.mooncore.modules.event;

import java.util.Map;

/** Une action d'événement : un type + des paramètres libres (lus selon le type). */
public record EventAction(EventActionType type, Map<String, Object> params) {

    public String str(String key, String def) {
        Object v = params.get(key);
        return v != null ? v.toString() : def;
    }

    public double num(String key, double def) {
        Object v = params.get(key);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    public boolean bool(String key, boolean def) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return Boolean.parseBoolean(v.toString());
        return def;
    }
}
