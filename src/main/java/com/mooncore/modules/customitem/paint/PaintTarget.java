package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Cible d'édition de l'éditeur de texture : un objet custom OU un bloc custom.
 * Découple {@link PaintSession} du type édité (où écrire le PNG, comment l'appliquer).
 */
public interface PaintTarget {

    String id();

    /** Fichier PNG source de la texture (lu à l'ouverture, écrit à la sauvegarde). */
    File textureFile();

    /** Après écriture du PNG : assigner le modèle, persister, reconstruire le pack. */
    void onSaved(MoonCore plugin, Player editor);
}
