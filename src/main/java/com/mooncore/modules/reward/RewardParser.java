package com.mooncore.modules.reward;

import com.mooncore.api.reward.ItemSpec;
import com.mooncore.api.reward.Reward;
import com.mooncore.api.reward.RewardAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transforme une définition YAML (liste de maps) en {@link Reward}. Logique pure (aucune
 * dépendance Bukkit) afin d'être testable directement.
 */
public final class RewardParser {

    private RewardParser() {}

    public static Reward parse(String id, List<? extends Map<?, ?>> actionMaps) {
        List<RewardAction> actions = new ArrayList<>();
        if (actionMaps != null) {
            for (Map<?, ?> map : actionMaps) {
                RewardAction action = parseAction(map);
                if (action != null) actions.add(action);
            }
        }
        return new Reward(id, actions);
    }

    static RewardAction parseAction(Map<?, ?> map) {
        String typeStr = str(map, "type", null);
        if (typeStr == null) return null;
        RewardAction.Type type;
        try {
            type = RewardAction.Type.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return switch (type) {
            case ITEM -> RewardAction.item(new ItemSpec(
                    str(map, "material", "STONE"),
                    (int) num(map, "amount", 1),
                    str(map, "name", null),
                    strList(map, "lore")));
            case MONEY -> RewardAction.money(num(map, "amount", 0));
            case XP -> RewardAction.xp(num(map, "amount", 0));
            case COMMAND -> RewardAction.command(firstStr(map, "command", "value", "text"));
            case MESSAGE -> RewardAction.message(firstStr(map, "message", "value", "text"));
            case BROADCAST -> RewardAction.broadcast(firstStr(map, "broadcast", "value", "text"));
            case STAT -> RewardAction.stat(str(map, "key", "unknown"), (long) num(map, "amount", 0));
        };
    }

    // ---- helpers ----

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v != null ? v.toString() : def;
    }

    private static String firstStr(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v.toString();
        }
        return "";
    }

    private static double num(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        return (v instanceof Number n) ? n.doubleValue() : def;
    }

    private static List<String> strList(Map<?, ?> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) out.add(String.valueOf(o));
            return out;
        }
        return List.of();
    }
}
