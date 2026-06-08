package com.mooncore.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyResolverTest {

    @Test
    void hardDependencyComesBeforeDependant() {
        Set<String> ids = new java.util.LinkedHashSet<>(List.of("anti-farm", "data", "config"));
        Map<String, List<String>> hard = Map.of(
                "anti-farm", List.of("data", "config"),
                "data", List.of("config"),
                "config", List.of());

        List<String> order = DependencyResolver.resolve(ids, hard, Map.of());

        assertTrue(order.indexOf("config") < order.indexOf("data"), "config avant data");
        assertTrue(order.indexOf("data") < order.indexOf("anti-farm"), "data avant anti-farm");
    }

    @Test
    void softDependencyIsIgnoredWhenAbsent() {
        Set<String> ids = Set.of("progression");
        Map<String, List<String>> soft = Map.of("progression", List.of("reward")); // reward absent

        List<String> order = DependencyResolver.resolve(ids, Map.of(), soft);

        assertTrue(order.contains("progression"));
    }

    @Test
    void missingHardDependencyThrows() {
        Set<String> ids = Set.of("anti-farm");
        Map<String, List<String>> hard = Map.of("anti-farm", List.of("data")); // data absent

        assertThrows(DependencyResolver.ResolutionException.class,
                () -> DependencyResolver.resolve(ids, hard, Map.of()));
    }

    @Test
    void cycleThrows() {
        Set<String> ids = Set.of("a", "b");
        Map<String, List<String>> hard = Map.of(
                "a", List.of("b"),
                "b", List.of("a"));

        assertThrows(DependencyResolver.ResolutionException.class,
                () -> DependencyResolver.resolve(ids, hard, Map.of()));
    }
}
