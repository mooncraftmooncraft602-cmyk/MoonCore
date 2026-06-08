package com.mooncore.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logger fin pour MoonCore : encapsule le {@link Logger} du plugin et ajoute un
 * niveau {@code debug} contrôlé par {@code config.yml > core.debug}.
 */
public final class MoonLogger {

    private final Logger handle;
    private volatile boolean debug;

    public MoonLogger(Logger handle, boolean debug) {
        this.handle = handle;
        this.debug = debug;
    }

    public void setDebug(boolean debug) { this.debug = debug; }
    public boolean isDebug() { return debug; }

    public Logger handle() { return handle; }

    public void info(String message) { handle.info(message); }
    public void warn(String message) { handle.warning(message); }
    public void severe(String message) { handle.severe(message); }

    public void error(String message, Throwable t) {
        handle.log(Level.SEVERE, message, t);
    }

    public void debug(String message) {
        if (debug) handle.info("[DEBUG] " + message);
    }

    public void debug(java.util.function.Supplier<String> message) {
        if (debug) handle.info("[DEBUG] " + message.get());
    }
}
