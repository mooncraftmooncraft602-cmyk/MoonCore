package com.mooncore.api.zone;

import org.bukkit.Location;

import java.util.List;
import java.util.Optional;

/**
 * Service public du module Zone, exposé via le ServiceRegistry. Les autres modules
 * (téléport, AntiFarm, claim…) interrogent les flags sans dépendre de l'implémentation.
 */
public interface ZoneService {

    /** Régions contenant {@code loc}, triées par priorité décroissante. */
    List<Region> regionsAt(Location loc);

    /** Région de plus haute priorité contenant {@code loc}, le cas échéant. */
    Optional<Region> highestAt(Location loc);

    /**
     * Valeur effective d'un flag à un emplacement : la région de plus haute priorité
     * qui définit ce flag l'emporte ; sinon la valeur par défaut du flag.
     */
    boolean flag(Location loc, ZoneFlag flag);
}
