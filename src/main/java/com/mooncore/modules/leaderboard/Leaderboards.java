package com.mooncore.modules.leaderboard;

import com.mooncore.api.leaderboard.LeaderboardEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Outils purs de construction de classements (testables sans DB). */
public final class Leaderboards {

    private Leaderboards() {}

    /** Une ligne brute issue de la base, avant attribution du rang. */
    public record RawRow(UUID uuid, String name, long value) {}

    /**
     * Attribue les rangs (1..N) à des lignes <b>déjà triées</b> par valeur décroissante.
     * Les noms nuls sont remplacés par un libellé court de l'UUID.
     */
    public static List<LeaderboardEntry> rank(List<RawRow> rows) {
        List<LeaderboardEntry> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (RawRow r : rows) {
            String name = r.name() != null ? r.name()
                    : r.uuid().toString().substring(0, 8);
            out.add(new LeaderboardEntry(rank++, r.uuid(), name, r.value()));
        }
        return out;
    }
}
