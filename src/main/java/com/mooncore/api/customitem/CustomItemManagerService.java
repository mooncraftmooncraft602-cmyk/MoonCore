package com.mooncore.api.customitem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Set;

/**
 * Service public du gestionnaire d'objets custom data-driven (armes, armures, outils,
 * artefacts, reliques, objets d'event/boss/endgame, récompenses saisonnières).
 * <p>
 * Consommé par les autres modules (BossManager, EventManager, RewardManager,
 * Progression…) via le {@code ServiceRegistry}. Toutes les méthodes sont thread-safe
 * côté lecture ; la création d'{@link ItemStack} doit se faire sur le thread principal.
 */
public interface CustomItemManagerService {

    /** Ids de toutes les définitions chargées. */
    Set<String> ids();

    /** Toutes les définitions chargées (lecture seule). */
    Collection<? extends CustomItemView> all();

    /** Vue lecture seule d'une définition, ou {@code null} si inconnue. */
    CustomItemView definition(String id);

    /** Crée un exemplaire de l'objet ({@code null} si id inconnu). */
    ItemStack create(String id);

    /** Crée un exemplaire avec une quantité donnée ({@code null} si id inconnu). */
    ItemStack create(String id, int amount);

    /** Donne l'objet au joueur (false si id inconnu). */
    boolean give(Player player, String id, int amount);

    /** Id custom porté par un objet, ou {@code null} s'il n'en a pas. */
    String idOf(ItemStack item);

    /** {@code true} si l'objet est un objet custom de ce gestionnaire. */
    boolean isCustom(ItemStack item);

    /** Recharge toutes les définitions depuis le disque. */
    void reloadDefinitions();

    /**
     * Vue lecture seule minimale d'une définition (pour les autres modules sans
     * exposer la classe d'implémentation mutable).
     */
    interface CustomItemView {
        String id();
        String displayName();
        ItemType type();
        Rarity rarity();
    }
}
