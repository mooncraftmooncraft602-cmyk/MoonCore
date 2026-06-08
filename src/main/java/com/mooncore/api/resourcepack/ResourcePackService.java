package com.mooncore.api.resourcepack;

/**
 * Service du resource pack serveur (forcé). Permet aux autres modules (CustomItem, IA)
 * de déclencher une reconstruction du pack après ajout d'une texture, et de connaître
 * l'URL de distribution.
 */
public interface ResourcePackService {

    /** Reconstruit le pack (modèles + textures + sons) et recalcule le SHA-1. */
    void rebuild();

    /** (Re)pousse le pack forcé à tous les joueurs Java en ligne. */
    void resendAll();

    /** URL de téléchargement du pack, ou {@code null} si le serveur HTTP est arrêté. */
    String url();
}
