package com.mooncore.core.module;

import com.mooncore.MoonCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enregistre les modules métier, résout l'ordre de chargement par dépendances,
 * puis gère leur cycle de vie (enable/disable/reload).
 * <p>
 * Un module désactivé dans {@code config.yml > modules} n'est jamais chargé.
 * Si un module échoue à s'activer, les modules qui en dépendent (dur) sont
 * marqués {@link ModuleState#FAILED} en cascade et non activés.
 */
public final class ModuleManager {

    private final MoonCore plugin;
    private final Map<String, MoonModule> modules = new LinkedHashMap<>();
    private List<String> enableOrder = List.of();

    public ModuleManager(MoonCore plugin) {
        this.plugin = plugin;
    }

    /** Enregistre un module (avant {@link #enableAll()}). */
    public void register(MoonModule module) {
        if (modules.containsKey(module.id())) {
            throw new IllegalStateException("Module déjà enregistré : " + module.id());
        }
        modules.put(module.id(), module);
    }

    /** Active tous les modules enregistrés et autorisés par config, dans l'ordre des dépendances. */
    public void enableAll() {
        // 1) Filtre les modules activés en config.
        Set<String> active = new java.util.LinkedHashSet<>();
        for (MoonModule m : modules.values()) {
            if (plugin.configManager().isModuleEnabled(m.id())) {
                active.add(m.id());
            } else if (m instanceof AbstractModule am) {
                am.markDisabledByConfig();
                plugin.logger().info("Module désactivé par config : " + m.id());
            }
        }

        if (active.isEmpty()) {
            plugin.logger().info("Aucun module métier à activer.");
            return;
        }

        // 2) Résout l'ordre.
        Map<String, List<String>> hard = new HashMap<>();
        Map<String, List<String>> soft = new HashMap<>();
        for (String id : active) {
            ModuleInfo info = modules.get(id).info();
            hard.put(id, List.of(info.depends()));
            soft.put(id, List.of(info.softDepends()));
        }

        try {
            enableOrder = DependencyResolver.resolve(active, hard, soft);
        } catch (DependencyResolver.ResolutionException ex) {
            plugin.logger().severe("Impossible de résoudre les dépendances des modules : " + ex.getMessage());
            return;
        }

        // 3) Active dans l'ordre, en propageant les échecs en cascade.
        Set<String> failed = new java.util.HashSet<>();
        for (String id : enableOrder) {
            MoonModule module = modules.get(id);

            // Une dépendance dure a-t-elle échoué ?
            String brokenDep = firstFailedHardDependency(module, failed);
            if (brokenDep != null) {
                if (module instanceof AbstractModule am) am.markFailed();
                failed.add(id);
                plugin.logger().severe("Module '" + id + "' non activé : dépendance en échec '" + brokenDep + "'.");
                continue;
            }

            long start = System.nanoTime();
            try {
                module.enableModule(plugin);
                double ms = (System.nanoTime() - start) / 1_000_000.0;
                plugin.logger().info(String.format("Module activé : %s (%.1f ms)", id, ms));
            } catch (Throwable t) {
                failed.add(id);
                plugin.logger().severe("Échec d'activation du module '" + id + "' : " + t.getMessage());
                plugin.logger().error("Détail de l'échec du module " + id, t);
            }
        }
    }

    /** Désactive tous les modules dans l'ordre inverse d'activation. */
    public void disableAll() {
        List<String> reverse = new ArrayList<>(enableOrder);
        Collections.reverse(reverse);
        for (String id : reverse) {
            MoonModule module = modules.get(id);
            if (module == null || !module.isEnabled()) {
                continue;
            }
            try {
                module.disableModule();
                plugin.logger().info("Module désactivé : " + id);
            } catch (Throwable t) {
                plugin.logger().error("Erreur lors de la désactivation du module " + id, t);
            }
        }
    }

    /** Recharge la config de tous les modules actifs (ou d'un seul si {@code id} fourni). */
    public boolean reload(String id) {
        if (id == null) {
            for (String mid : enableOrder) {
                MoonModule m = modules.get(mid);
                if (m != null && m.isEnabled()) m.reloadModule();
            }
            return true;
        }
        MoonModule m = modules.get(id);
        if (m == null || !m.isEnabled()) return false;
        m.reloadModule();
        return true;
    }

    private String firstFailedHardDependency(MoonModule module, Set<String> failed) {
        for (String dep : module.info().depends()) {
            if (failed.contains(dep)) return dep;
        }
        return null;
    }

    // ---- Accès ----

    public MoonModule get(String id) { return modules.get(id); }

    @SuppressWarnings("unchecked")
    public <T extends MoonModule> T get(Class<T> type) {
        for (MoonModule m : modules.values()) {
            if (type.isInstance(m)) return (T) m;
        }
        return null;
    }

    public Collection<MoonModule> all() { return Collections.unmodifiableCollection(modules.values()); }

    public List<String> enableOrder() { return Collections.unmodifiableList(enableOrder); }
}
