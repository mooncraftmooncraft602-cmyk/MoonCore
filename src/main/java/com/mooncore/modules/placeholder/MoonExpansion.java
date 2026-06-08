package com.mooncore.modules.placeholder;

import com.mooncore.MoonCore;
import com.mooncore.api.afk.AntiAfkService;
import com.mooncore.api.economy.EconomyService;
import com.mooncore.api.leaderboard.LeaderboardEntry;
import com.mooncore.api.leaderboard.LeaderboardService;
import com.mooncore.api.progression.ProgressionService;
import com.mooncore.api.season.SeasonService;
import com.mooncore.api.stats.StatKeys;
import com.mooncore.api.stats.StatisticsService;
import com.mooncore.api.team.TeamService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Expansion PlaceholderAPI : expose les données MoonCore en {@code %mooncore_...%}.
 * Lit les services via le ServiceRegistry (dépendances molles : renvoie une valeur vide
 * si le service correspondant est absent).
 */
public final class MoonExpansion extends PlaceholderExpansion {

    private final MoonCore plugin;

    public MoonExpansion(MoonCore plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "mooncore"; }
    @Override public @NotNull String getAuthor() { return "MoonTeam"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        UUID id = player == null ? null : player.getUniqueId();
        String p = params.toLowerCase(Locale.ROOT);
        var s = plugin.services();

        // --- Classements (indépendants du joueur) ---
        if (p.startsWith("leaderboard_")) {
            Placeholders.LeaderboardRef ref = Placeholders.parseLeaderboard(p);
            if (ref == null) return "";
            return s.get(LeaderboardService.class).map(lb -> {
                List<LeaderboardEntry> top = lb.top(ref.board());
                if (ref.rank() > top.size()) return "—";
                LeaderboardEntry e = top.get(ref.rank() - 1);
                return ref.field().equals("name") ? e.name() : String.valueOf(e.value());
            }).orElse("");
        }

        // --- Saison ---
        if (p.equals("season")) {
            return s.get(SeasonService.class).map(se -> se.current() != null ? se.current().seasonId() : "").orElse("");
        }
        if (p.equals("season_days_left")) {
            return s.get(SeasonService.class).map(se -> String.valueOf(se.daysRemaining())).orElse("");
        }

        if (id == null) return "";

        // --- Joueur ---
        switch (p) {
            case "tier":
                return s.get(ProgressionService.class).map(pr -> String.valueOf(pr.tier(id))).orElse("");
            case "xp":
                return s.get(ProgressionService.class).map(pr -> String.valueOf(pr.xp(id))).orElse("");
            case "next_tier_xp":
                return s.get(ProgressionService.class).map(pr -> {
                    long n = pr.nextTierXp(id);
                    return n < 0 ? "MAX" : String.valueOf(n);
                }).orElse("");
            case "balance":
                return s.get(EconomyService.class)
                        .map(eco -> String.format(Locale.ROOT, "%.2f", eco.balance(id))).orElse("");
            case "afk":
                return s.get(AntiAfkService.class).map(a -> a.isAfk(id) ? "oui" : "non").orElse("non");
            case "team":
                return s.get(TeamService.class).flatMap(t -> t.teamId(id)).orElse("—");
            case "kills", "mob_kills":
                return stat(id, StatKeys.MOB_KILLS);
            case "pvp", "player_kills":
                return stat(id, StatKeys.PLAYER_KILLS);
            case "deaths":
                return stat(id, StatKeys.DEATHS);
            case "boss_kills":
                return stat(id, StatKeys.BOSS_KILLS);
            case "blocks_mined":
                return stat(id, StatKeys.BLOCKS_MINED);
            case "missions_completed":
                return stat(id, StatKeys.MISSIONS_COMPLETED);
            default:
                if (p.startsWith("stat_")) {
                    return stat(id, p.substring("stat_".length()));
                }
                return null; // placeholder inconnu
        }
    }

    private String stat(UUID id, String key) {
        return plugin.services().get(StatisticsService.class)
                .map(st -> String.valueOf(st.get(id, key))).orElse("0");
    }
}
