package com.mooncore.api.reward;

/**
 * Une action élémentaire d'une récompense. Donnée pure (exécutée par le RewardManager).
 * Selon le {@link Type}, seuls certains champs sont pertinents.
 */
public record RewardAction(Type type, String text, double amount, ItemSpec item) {

    public enum Type {
        ITEM,       // item
        MONEY,      // amount (via EconomyService)
        XP,         // amount (expérience vanilla)
        COMMAND,    // text (commande console, %player% remplacé)
        MESSAGE,    // text (MiniMessage au joueur)
        BROADCAST,  // text (MiniMessage à tous)
        STAT        // text = clé, amount = quantité (via StatisticsService)
    }

    public static RewardAction money(double amount) { return new RewardAction(Type.MONEY, null, amount, null); }
    public static RewardAction xp(double amount) { return new RewardAction(Type.XP, null, amount, null); }
    public static RewardAction command(String cmd) { return new RewardAction(Type.COMMAND, cmd, 0, null); }
    public static RewardAction message(String msg) { return new RewardAction(Type.MESSAGE, msg, 0, null); }
    public static RewardAction broadcast(String msg) { return new RewardAction(Type.BROADCAST, msg, 0, null); }
    public static RewardAction stat(String key, long amount) { return new RewardAction(Type.STAT, key, amount, null); }
    public static RewardAction item(ItemSpec spec) { return new RewardAction(Type.ITEM, null, 0, spec); }
}
