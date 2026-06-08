package com.mooncore.modules.resourcepack;

import com.mooncore.util.MoonLogger;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

/**
 * Serveur HTTP minimal (JDK {@link HttpServer}) servant le resource pack zip sur
 * {@code /pack.zip}. Permet un pack forcé sans hébergement externe. Le binaire est
 * relu à chaque requête (toujours à jour après un rebuild).
 */
public final class HttpPackServer {

    private final MoonLogger log;
    private final File packFile;
    private final int basePort;
    private int boundPort = -1;
    private HttpServer server;

    public HttpPackServer(MoonLogger log, File packFile, int port) {
        this.log = log;
        this.packFile = packFile;
        this.basePort = port;
    }

    /** Démarre en essayant le port configuré puis jusqu'à +20 s'il est déjà pris. */
    public void start() throws Exception {
        Exception last = null;
        for (int p = basePort; p <= basePort + 20; p++) {
            try {
                server = HttpServer.create(new InetSocketAddress(p), 0);
                boundPort = p;
                break;
            } catch (java.io.IOException e) {
                last = e; // port pris, on tente le suivant
            }
        }
        if (server == null) throw last != null ? last : new java.io.IOException("Aucun port libre");
        if (boundPort != basePort) {
            log.warn("[ResourcePack] Port " + basePort + " occupé → bascule sur " + boundPort + ".");
        }
        server.createContext("/pack.zip", exchange -> {
            try {
                if (!packFile.isFile()) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] data = Files.readAllBytes(packFile.toPath());
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } catch (Exception e) {
                log.warn("[ResourcePack] Erreur HTTP : " + e.getMessage());
                try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(null); // exécuteur par défaut (thread interne)
        server.start();
        log.info("[ResourcePack] Serveur HTTP démarré sur le port " + boundPort + " (/pack.zip).");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() { return server != null; }

    /** Port réellement utilisé (peut différer du port configuré si celui-ci était pris). */
    public int boundPort() { return boundPort; }
}
