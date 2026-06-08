package com.mooncore.core.module;

import com.mooncore.MoonCore;
import com.mooncore.core.event.EventBus;
import com.mooncore.core.service.ServiceRegistry;
import com.mooncore.data.DataManager;
import com.mooncore.util.MoonLogger;
import com.mooncore.util.Schedulers;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;

import java.util.Objects;

/**
 * Base de tous les modules. Gère l'état, l'accès aux services noyau et le chargement
 * du fichier de configuration {@code modules/<id>.yml}.
 * <p>
 * Les modules concrets implémentent {@link #onEnable()}, {@link #onDisable()} et
 * (optionnellement) {@link #onReload()}.
 */
public abstract class AbstractModule implements MoonModule {

    private final ModuleInfo info;
    protected MoonCore plugin;
    private volatile ModuleState state = ModuleState.REGISTERED;

    protected AbstractModule() {
        ModuleInfo annotation = getClass().getAnnotation(ModuleInfo.class);
        if (annotation == null) {
            throw new IllegalStateException(
                    getClass().getName() + " doit être annoté avec @ModuleInfo");
        }
        this.info = annotation;
    }

    // ---- Lifecycle (appelé par ModuleManager uniquement) ----

    @Override
    public final void enableModule(MoonCore plugin) throws Exception {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.state = ModuleState.ENABLING;
        try {
            onEnable();
            this.state = ModuleState.ENABLED;
        } catch (Throwable t) {
            this.state = ModuleState.FAILED;
            throw t;
        }
    }

    @Override
    public final void disableModule() {
        if (state != ModuleState.ENABLED) {
            return;
        }
        this.state = ModuleState.DISABLING;
        try {
            onDisable();
        } finally {
            this.state = ModuleState.DISABLED;
        }
    }

    @Override
    public final void reloadModule() {
        if (state != ModuleState.ENABLED) {
            return;
        }
        onReload();
    }

    /** Activation : enregistrer listeners, services, tâches, charger la config. */
    protected abstract void onEnable() throws Exception;

    /** Désactivation : libérer ressources, annuler tâches, flush. */
    protected abstract void onDisable();

    /** Rechargement à chaud (par défaut : recharge la config du module). */
    protected void onReload() {
        reloadModuleConfig();
    }

    // ---- Accès rapides aux services noyau ----

    protected final MoonCore plugin() { return plugin; }
    protected final MoonLogger log() { return plugin.logger(); }
    protected final ServiceRegistry services() { return plugin.services(); }
    protected final EventBus eventBus() { return plugin.eventBus(); }
    protected final Schedulers schedulers() { return plugin.schedulers(); }
    protected final DataManager data() { return plugin.dataManager(); }

    /** Configuration de ce module ({@code modules/<id>.yml}, créée depuis les ressources si absente). */
    protected final FileConfiguration moduleConfig() {
        return plugin.configManager().moduleConfig(id());
    }

    protected final void reloadModuleConfig() {
        plugin.configManager().reloadModuleConfig(id());
    }

    /** Enregistre un listener Bukkit au nom de ce module. */
    protected final void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    // ---- Accesseurs ----

    @Override public final ModuleInfo info() { return info; }
    @Override public final String id() { return info.id(); }
    @Override public final ModuleState state() { return state; }
    @Override public final boolean isEnabled() { return state == ModuleState.ENABLED; }

    /** Réservé au ModuleManager pour marquer un échec en cascade. */
    final void markFailed() { this.state = ModuleState.FAILED; }

    /** Réservé au ModuleManager pour marquer une désactivation par config. */
    final void markDisabledByConfig() { this.state = ModuleState.DISABLED_BY_CONFIG; }
}
