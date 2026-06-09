package com.mooncore.modules.integrations;

import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import org.bukkit.Bukkit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Détecte les plugins tiers <b>déjà installés</b> sur le serveur et expose leur présence aux
 * autres modules MoonCore. Philosophie : pour les gros plugins (WorldGuard, CoreProtect,
 * WorldEdit, GrimAC, Shopkeepers…), on NE réécrit PAS leur code — on s'y <b>branche</b> via
 * leur API si présents (leur code éprouvé continue de tourner). Ce module est le point central
 * de détection ; les hooks concrets sont ajoutés au cas par cas par les modules concernés.
 */
@ModuleInfo(id = "integrations", name = "Integrations")
public final class IntegrationsModule extends AbstractModule {

    /** Plugins « intégrables » connus (nom tel que déclaré dans leur plugin.yml). */
    private static final String[] KNOWN = {
            "Vault", "PlaceholderAPI", "LuckPerms", "WorldGuard", "WorldEdit", "FastAsyncWorldEdit",
            "CoreProtect", "ProtocolLib", "Geyser-Spigot", "floodgate", "Shopkeepers", "GrimAC",
            "Essentials", "TAB", "SkinsRestorer", "Citizens", "MythicMobs"
    };

    private final Map<String, Boolean> present = new LinkedHashMap<>();

    @Override
    protected void onEnable() {
        StringBuilder found = new StringBuilder();
        int n = 0;
        for (String name : KNOWN) {
            boolean has = Bukkit.getPluginManager().getPlugin(name) != null;
            present.put(name, has);
            if (has) { found.append(found.isEmpty() ? "" : ", ").append(name); n++; }
        }
        log().info("Intégrations détectées (" + n + ") : " + (n == 0 ? "aucune" : found)
                + ". MoonCore s'y branchera sans les réécrire.");
    }

    @Override protected void onDisable() { present.clear(); }

    /** Le plugin tiers nommé est-il présent et activé ? */
    public boolean isPresent(String pluginName) {
        return present.getOrDefault(pluginName, false)
                && Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }
}
