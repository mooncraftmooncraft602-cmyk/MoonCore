package com.mooncore.modules.placeholder;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;

/**
 * Enregistre l'expansion PlaceholderAPI si le plugin PlaceholderAPI est présent (dépendance
 * molle). Sans lui, le module se charge sans effet (aucune erreur).
 */
@ModuleInfo(id = "placeholders", name = "Placeholders")
public final class PlaceholderModule extends AbstractModule {

    private MoonExpansion expansion;

    @Override
    protected void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            log().info("PlaceholderAPI absent : placeholders MoonCore non enregistrés.");
            return;
        }
        this.expansion = new MoonExpansion(plugin());
        if (expansion.register()) {
            log().info("Placeholders MoonCore enregistrés (%mooncore_...%).");
        } else {
            log().warn("Échec de l'enregistrement de l'expansion PlaceholderAPI.");
        }
    }

    @Override
    protected void onDisable() {
        if (expansion != null && expansion.isRegistered()) {
            expansion.unregister();
        }
        expansion = null;
    }
}
