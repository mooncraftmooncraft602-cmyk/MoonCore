package com.mooncore.modules.boss;

import java.util.List;

/**
 * Une phase de boss, active quand les PV passent sous {@code fromPercent} % (et tant qu'une
 * phase plus basse n'est pas atteinte). Active un jeu de capacités.
 */
public record BossPhase(String name, double fromPercent, List<AbilityInstance> abilities) {
    public BossPhase {
        if (abilities == null) abilities = List.of();
    }
}
