package com.mooncore.modules.resourcepack;

import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Compat;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.InetAddress;
import java.util.Map;

/**
 * Resource pack serveur <b>forcé</b> : assemble (modèles + textures d'items + sons et
 * autres assets de {@code pack-sources/}), le sert via un serveur HTTP embarqué, et
 * l'impose à chaque joueur Java à la connexion (kick optionnel si refus), à la manière
 * des serveurs « pack obligatoire ».
 * <p>
 * Bedrock : les packs Java ne s'appliquent pas via Geyser → les joueurs Bedrock ne
 * reçoivent pas l'envoi forcé (pas de kick). Pour eux, un .mcpack Geyser séparé est
 * nécessaire ; le gameplay reste identique (cf. compat Bedrock).
 */
@ModuleInfo(id = "resource-pack", name = "ResourcePackForce", softDepends = {"custom-item", "audio"})
public final class ResourcePackModule extends AbstractModule implements ResourcePackService {

    private HttpPackServer http;
    private File packZip;
    private File buildDir;
    private File packSources;
    private byte[] sha1;
    private String url;

    private boolean force;
    private boolean kickOnDecline;
    private String prompt;
    private int port;
    private String host;

    @Override
    protected void onEnable() throws Exception {
        if (!moduleConfig().getBoolean("enabled", true)) {
            log().info("[ResourcePack] Désactivé par config (enabled: false).");
            return;
        }
        loadConfig();

        File data = plugin().getDataFolder();
        this.packZip = new File(data, "resourcepack-dist/pack.zip");
        this.buildDir = new File(data, "resourcepack-build");
        this.packSources = new File(data, "pack-sources");
        if (!packSources.exists()) packSources.mkdirs();

        rebuild();

        this.http = new HttpPackServer(log(), packZip, port);
        try {
            http.start();
        } catch (Exception e) {
            log().error("[ResourcePack] Impossible de démarrer le serveur HTTP (port " + port + ")", e);
        }
        computeUrl();

        registerListener(new ResourcePackListener(this));
        services().register(ResourcePackService.class, this);

        log().info("[ResourcePack] Prêt. URL=" + url + " forcé=" + force);
    }

    @Override
    protected void onDisable() {
        if (http != null) http.stop();
        services().unregister(ResourcePackService.class);
    }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
        rebuild();
        computeUrl();
        resendAll();
    }

    private void loadConfig() {
        this.force = moduleConfig().getBoolean("force", true);
        this.kickOnDecline = moduleConfig().getBoolean("kick-on-decline", false);
        this.prompt = moduleConfig().getString("prompt", "<gradient:#8a2be2:#c77dff>Resource pack MoonCore requis</gradient>");
        this.port = moduleConfig().getInt("port", 8765);
        this.host = moduleConfig().getString("host", "");
    }

    private void computeUrl() {
        String h = host;
        if (h == null || h.isBlank()) h = detectIp();
        int actualPort = (http != null) ? http.boundPort() : port;
        this.url = (http != null && http.isRunning()) ? "http://" + h + ":" + actualPort + "/pack.zip" : null;
    }

    /** IP de l'interface sortante (plus fiable que getLocalHost). Repli 127.0.0.1. */
    private static String detectIp() {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = s.getLocalAddress().getHostAddress();
            if (ip != null && !ip.equals("0.0.0.0")) return ip;
        } catch (Exception ignored) { /* repli ci-dessous */ }
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    // ---- ResourcePackService ----

    @Override
    public void rebuild() {
        try {
            CustomItemManagerModule ci = plugin().moduleManager().get(CustomItemManagerModule.class);
            Map<String, com.mooncore.modules.customitem.CustomItemDef> defs =
                    ci != null ? ci.rawDefs() : Map.of();
            File texturesSrc = ci != null ? ci.texturesFolder() : null;

            var cb = plugin().moduleManager().get(com.mooncore.modules.customblock.CustomBlockManagerModule.class);
            Map<String, com.mooncore.modules.customblock.CustomBlockDef> blockDefs =
                    cb != null ? cb.rawDefs() : Map.of();
            File blockTex = cb != null ? cb.store().texturesFolder() : null;

            backupTextures(texturesSrc, blockTex); // filet de sécurité avant ré-assemblage

            PackAssembler.Built built = new PackAssembler(log())
                    .assemble(defs, buildDir, texturesSrc, packSources, packZip, blockDefs, blockTex);
            this.sha1 = built.sha1();
            log().info("[ResourcePack] Pack assemblé : " + built.models() + " modèle(s), "
                    + (packZip.length() / 1024) + " Ko, SHA-1=" + PackAssembler.hex(built.sha1()).substring(0, 12) + "…");
        } catch (Exception e) {
            log().error("[ResourcePack] Échec d'assemblage du pack", e);
        }
    }

    private static final int MAX_BACKUPS = 12;

    /** Snapshot des textures sources (items + blocs) avant chaque rebuild → resourcepack-backups/. */
    private void backupTextures(File itemsSrc, File blockSrc) {
        try {
            int items = pngCount(itemsSrc), blocks = pngCount(blockSrc);
            if (items + blocks == 0) return; // rien à sauvegarder
            File root = new File(plugin().getDataFolder(), "resourcepack-backups");
            root.mkdirs();
            File snap = new File(root, String.valueOf(System.currentTimeMillis()));
            if (itemsSrc != null && itemsSrc.isDirectory()) copyPngs(itemsSrc, new File(snap, "items"));
            if (blockSrc != null && blockSrc.isDirectory()) copyPngs(blockSrc, new File(snap, "blocks"));
            rotateBackups(root);
        } catch (Exception e) {
            log().warn("[ResourcePack] Backup des textures échoué : " + e.getMessage());
        }
    }

    private static int pngCount(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] f = dir.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
        return f == null ? 0 : f.length;
    }

    private static void copyPngs(File src, File dst) throws java.io.IOException {
        File[] files = src.listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
        if (files == null || files.length == 0) return;
        dst.mkdirs();
        for (File f : files) {
            java.nio.file.Files.copy(f.toPath(), new File(dst, f.getName()).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Ne garde que les {@value #MAX_BACKUPS} backups les plus récents. */
    private void rotateBackups(File root) {
        File[] snaps = root.listFiles(File::isDirectory);
        if (snaps == null || snaps.length <= MAX_BACKUPS) return;
        java.util.Arrays.sort(snaps, java.util.Comparator.comparing(File::getName)); // timestamps croissants
        for (int i = 0; i < snaps.length - MAX_BACKUPS; i++) deleteRecursive(snaps[i]);
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) { File[] c = f.listFiles(); if (c != null) for (File x : c) deleteRecursive(x); }
        f.delete();
    }

    @Override
    public void resendAll() {
        for (Player p : Bukkit.getOnlinePlayers()) send(p);
    }

    @Override
    public String url() { return url; }

    // ---- envoi ----

    /** Envoie le pack forcé à un joueur Java (ignore Bedrock). */
    @SuppressWarnings("deprecation") // overload (url,hash,force) = le plus portable entre versions
    public void send(Player p) {
        if (url == null || sha1 == null) return;
        if (Compat.isBedrock(p)) return; // pack Java non applicable via Geyser
        try {
            p.setResourcePack(url, sha1, force);
        } catch (Throwable t) {
            log().warn("[ResourcePack] Envoi échoué à " + p.getName() + " : " + t.getMessage());
        }
    }

    public boolean force() { return force; }
    public boolean kickOnDecline() { return kickOnDecline; }
    public net.kyori.adventure.text.Component promptComponent() { return Text.mm(prompt); }
}
