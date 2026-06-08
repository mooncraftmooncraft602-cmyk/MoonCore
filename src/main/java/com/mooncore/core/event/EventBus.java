package com.mooncore.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bus d'événements interne, typé et léger. Publication synchrone sur le thread appelant
 * (les handlers doivent rester rapides ; déléguer en async si besoin).
 * <p>
 * Les exceptions d'un handler sont isolées : elles n'empêchent pas les autres de s'exécuter.
 */
public final class EventBus {

    /** Handle permettant de se désabonner. */
    public interface Subscription extends AutoCloseable {
        @Override void close();
    }

    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();
    private final java.util.logging.Logger logger;

    public EventBus(java.util.logging.Logger logger) {
        this.logger = logger;
    }

    public <T extends MoonEvent> Subscription subscribe(Class<T> type, Consumer<T> handler) {
        List<Consumer<?>> list = handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>());
        list.add(handler);
        return () -> list.remove(handler);
    }

    @SuppressWarnings("unchecked")
    public <T extends MoonEvent> T post(T event) {
        List<Consumer<?>> list = handlers.get(event.getClass());
        if (list != null) {
            for (Consumer<?> raw : list) {
                try {
                    ((Consumer<T>) raw).accept(event);
                } catch (Throwable t) {
                    logger.log(java.util.logging.Level.SEVERE,
                            "Handler en erreur pour l'événement " + event.getClass().getSimpleName(), t);
                }
            }
        }
        return event;
    }

    public void clear() {
        handlers.clear();
    }
}
