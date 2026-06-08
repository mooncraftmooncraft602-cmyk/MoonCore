package com.mooncore.core.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registre de services typés. Les modules publient une <b>interface</b> de service
 * et les autres la consomment sans connaître l'implémentation concrète : c'est le
 * point de découplage central entre modules.
 *
 * <pre>{@code
 * // côté producteur
 * services.register(EconomyService.class, new VaultEconomyService(...));
 * // côté consommateur
 * services.get(EconomyService.class).ifPresent(eco -> eco.withdraw(uuid, 10));
 * }</pre>
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T implementation) {
        services.put(type, implementation);
    }

    public void unregister(Class<?> type) {
        services.remove(type);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(Class<T> type) {
        return Optional.ofNullable((T) services.get(type));
    }

    /** Récupère un service obligatoire ; lève si absent. */
    @SuppressWarnings("unchecked")
    public <T> T require(Class<T> type) {
        T service = (T) services.get(type);
        if (service == null) {
            throw new IllegalStateException("Service requis introuvable : " + type.getName());
        }
        return service;
    }

    public boolean isRegistered(Class<?> type) {
        return services.containsKey(type);
    }

    public void clear() {
        services.clear();
    }
}
