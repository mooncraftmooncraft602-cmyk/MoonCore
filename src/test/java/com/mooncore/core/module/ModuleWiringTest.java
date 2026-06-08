package com.mooncore.core.module;

import com.mooncore.modules.admin.AdminToolsModule;
import com.mooncore.modules.antiafk.AntiAfkModule;
import com.mooncore.modules.antifarm.AntiFarmModule;
import com.mooncore.modules.audio.AudioManagerModule;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.modules.economy.EconomyBalancerModule;
import com.mooncore.modules.enchant.EnchantManagerModule;
import com.mooncore.modules.home.HomeManagerModule;
import com.mooncore.modules.enditems.EndgameItemsModule;
import com.mooncore.modules.event.EventManagerModule;
import com.mooncore.modules.leaderboard.LeaderboardManagerModule;
import com.mooncore.modules.missions.MissionModule;
import com.mooncore.modules.placeholder.PlaceholderModule;
import com.mooncore.modules.progression.ProgressionModule;
import com.mooncore.modules.quest.QuestManagerModule;
import com.mooncore.modules.reward.RewardManagerModule;
import com.mooncore.modules.season.SeasonManagerModule;
import com.mooncore.modules.stats.StatisticsModule;
import com.mooncore.modules.team.TeamManagerModule;
import com.mooncore.modules.zone.ZoneModule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garde-fou : vérifie que les dépendances DURES de chaque module enregistré référencent
 * uniquement d'autres modules enregistrés (et non des services noyau comme data/config),
 * et que l'ordre de chargement est résoluble. Aurait détecté le bug depends={"data","config"}.
 */
class ModuleWiringTest {

    private List<MoonModule> registeredModules() {
        // Mêmes modules que MoonCore#registerModules (constructeurs sans Bukkit).
        return List.of(new SeasonManagerModule(), new TeamManagerModule(), new StatisticsModule(), new RewardManagerModule(), new ProgressionModule(),
                new MissionModule(), new QuestManagerModule(), new LeaderboardManagerModule(), new BossManagerModule(),
                new EnchantManagerModule(), new EndgameItemsModule(), new EventManagerModule(),
                new ZoneModule(), new AntiAfkModule(), new EconomyBalancerModule(),
                new AntiFarmModule(), new AudioManagerModule(), new HomeManagerModule(),
                new PlaceholderModule(), new AdminToolsModule());
    }

    @Test
    void hardDependenciesReferenceOnlyRegisteredModules() {
        List<MoonModule> modules = registeredModules();
        Set<String> ids = new LinkedHashSet<>();
        modules.forEach(m -> ids.add(m.id()));

        for (MoonModule m : modules) {
            for (String dep : m.info().depends()) {
                assertTrue(ids.contains(dep),
                        "Le module '" + m.id() + "' a une dépendance dure inconnue : '" + dep
                                + "' (data/config sont des services noyau, pas des modules).");
            }
        }
    }

    @Test
    void loadOrderIsResolvable() {
        List<MoonModule> modules = registeredModules();
        Set<String> ids = new LinkedHashSet<>();
        Map<String, List<String>> hard = new HashMap<>();
        Map<String, List<String>> soft = new HashMap<>();
        for (MoonModule m : modules) {
            ids.add(m.id());
            hard.put(m.id(), List.of(m.info().depends()));
            soft.put(m.id(), List.of(m.info().softDepends()));
        }
        assertDoesNotThrow(() -> DependencyResolver.resolve(ids, hard, soft));
    }
}
