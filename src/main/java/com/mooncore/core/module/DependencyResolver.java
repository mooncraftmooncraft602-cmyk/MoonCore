package com.mooncore.core.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tri topologique des modules selon leurs dépendances déclarées.
 * <p>
 * Logique pure (sans Bukkit) pour être testable unitairement.
 */
public final class DependencyResolver {

    private DependencyResolver() {}

    /** Levée si une dépendance est introuvable ou si un cycle est détecté. */
    public static final class ResolutionException extends RuntimeException {
        public ResolutionException(String message) { super(message); }
    }

    /**
     * Retourne les ids triés tels qu'une dépendance apparaît toujours avant le module qui en dépend.
     *
     * @param ids           ids des modules présents (activés)
     * @param hardDepends   id → dépendances dures (doivent exister parmi {@code ids})
     * @param softDepends   id → dépendances molles (ignorées si absentes)
     */
    @SuppressWarnings("null")
    public static List<String> resolve(Set<String> ids,
                                       Map<String, List<String>> hardDepends,
                                       Map<String, List<String>> softDepends) {
        // Construit la liste d'adjacence effective (dépendance -> dépendant).
        Map<String, List<String>> edges = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String id : ids) {
            edges.computeIfAbsent(id, k -> new ArrayList<>());
            inDegree.putIfAbsent(id, 0);
        }

        for (String id : ids) {
            for (String dep : hardDepends.getOrDefault(id, List.of())) {
                if (!ids.contains(dep)) {
                    throw new ResolutionException(
                            "Module '" + id + "' dépend de '" + dep + "' qui est absent ou désactivé.");
                }
                addEdge(edges, inDegree, dep, id);
            }
            for (String dep : softDepends.getOrDefault(id, List.of())) {
                if (ids.contains(dep)) {
                    addEdge(edges, inDegree, dep, id);
                }
            }
        }

        // Kahn — ordre déterministe (file triée par insertion d'ids triés).
        Deque<String> queue = new ArrayDeque<>();
        // Ordre stable : on insère les ids sans dépendance dans l'ordre de la collection.
        for (String id : ids) {
            if (inDegree.get(id) == 0) {
                queue.add(id);
            }
        }

        List<String> sorted = new ArrayList<>(ids.size());
        Set<String> processed = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!processed.add(current)) {
                continue;
            }
            sorted.add(current);
            for (String dependant : edges.getOrDefault(current, List.of())) {
                int deg = inDegree.merge(dependant, -1, Integer::sum);
                if (deg == 0) {
                    queue.add(dependant);
                }
            }
        }

        if (sorted.size() != ids.size()) {
            Set<String> remaining = new LinkedHashSet<>(ids);
            remaining.removeAll(sorted);
            throw new ResolutionException("Cycle de dépendances détecté impliquant : " + remaining);
        }
        return sorted;
    }

    @SuppressWarnings("null")
    private static void addEdge(Map<String, List<String>> edges,
                                Map<String, Integer> inDegree,
                                String from, String to) {
        edges.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
        inDegree.merge(to, 1, Integer::sum);
    }
}
