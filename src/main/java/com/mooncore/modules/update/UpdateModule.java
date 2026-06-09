package com.mooncore.modules.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mooncore.core.module.AbstractModule;
import com.mooncore.core.module.ModuleInfo;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

/**
 * Auto-update depuis les Releases GitHub d'un dépôt PUBLIC : à l'activation (et
 * périodiquement) compare la version au dernier release ; si plus récent, télécharge le
 * .jar dans le dossier {@code update/} de Bukkit (appliqué au prochain redémarrage).
 * Tout est asynchrone, désactivable, et ne télécharge que depuis le dépôt officiel (HTTPS).
 */
@ModuleInfo(id = "auto-update", name = "AutoUpdater")
public final class UpdateModule extends AbstractModule implements Listener {

    private boolean enabled;
    private String repo;
    private boolean autoDownload;
    private boolean notifyAdmins;
    private volatile String pending; // version téléchargée en attente de redémarrage

    @Override
    protected void onEnable() {
        loadConfig();
        // /moon plugins reinstall|check — toujours disponible (même si l'auto-check est off).
        plugin().rootCommand().register(new com.mooncore.command.sub.PluginsSubCommand(this));
        if (!enabled) { log().info("[AutoUpdate] Auto-check désactivé (commande /moon plugins toujours dispo)."); return; }
        registerListener(this);
        // Premier check après 5 s (laisse le serveur finir de démarrer).
        schedulers().async(() -> check(false));
        int min = moduleConfig().getInt("check-interval-minutes", 180);
        if (min > 0) {
            long ticks = Math.max(1, min) * 60L * 20L;
            schedulers().asyncTimer(() -> check(false), ticks, ticks);
        }
    }

    @Override
    protected void onDisable() { /* rien à libérer (tâches gérées par le scheduler du module) */ }

    @Override
    protected void onReload() {
        reloadModuleConfig();
        loadConfig();
    }

    private void loadConfig() {
        this.enabled = moduleConfig().getBoolean("enabled", true);
        this.repo = moduleConfig().getString("repo", "mooncraftmooncraft602-cmyk/MoonCore");
        this.autoDownload = moduleConfig().getBoolean("auto-download", true);
        this.notifyAdmins = moduleConfig().getBoolean("notify-admins", true);
    }

    /** Vérifie le dernier release. {@code manual} = déclenché par commande (messages plus verbeux). */
    public void check(boolean manual) {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "MoonCore-Updater")
                    .header("Accept", "application/vnd.github+json")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) { log().info("[AutoUpdate] Aucun release publié sur " + repo + "."); return; }
            if (resp.statusCode() >= 400) { log().warn("[AutoUpdate] GitHub HTTP " + resp.statusCode()); return; }

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : null;
            if (tag == null) return;
            String current = plugin().getPluginMeta().getVersion();
            if (compare(tag, current) <= 0) {
                log().info("[AutoUpdate] À jour (" + current + ", dernier release " + tag + ").");
                return;
            }
            log().info("[AutoUpdate] Nouvelle version disponible : " + tag + " (actuelle " + current + ").");

            if (!autoDownload) { pending = tag; notify(tag, false); return; }

            String url = jarAssetUrl(root);
            if (url == null) { log().warn("[AutoUpdate] Release " + tag + " sans asset .jar."); return; }
            downloadToUpdateFolder(http, url);
            pending = tag;
            log().info("[AutoUpdate] " + tag + " téléchargé dans le dossier update/ — appliqué au prochain redémarrage.");
            notify(tag, true);
        } catch (Exception e) {
            log().warn("[AutoUpdate] Vérification échouée : " + e.getMessage());
        }
    }

    private String jarAssetUrl(JsonObject release) {
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) return null;
        for (var el : assets) {
            JsonObject a = el.getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                return a.get("browser_download_url").getAsString();
        }
        return null;
    }

    private java.io.File downloadToUpdateFolder(HttpClient http, String url) throws Exception {
        java.io.File updateDir = plugin().getServer().getUpdateFolderFile();
        if (!updateDir.exists()) updateDir.mkdirs();
        // Le fichier doit porter le MÊME nom que le jar en cours pour être appliqué.
        java.io.File out = new java.io.File(updateDir, plugin().jarFile().getName());
        HttpResponse<byte[]> r = http.send(
                HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "MoonCore-Updater").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() >= 400 || r.body().length < 1000) throw new Exception("téléchargement invalide (HTTP " + r.statusCode() + ")");
        Files.write(out.toPath(), r.body());
        return out;
    }

    public String repo() { return repo; }

    /**
     * {@code /moon plugins reinstall} : télécharge la dernière release GitHub et l'applique.
     * Le téléchargement (vers le dossier update/) est fiable ; le rechargement à chaud est
     * tenté au mieux (souvent impossible sous Windows car le .jar chargé est verrouillé) — dans
     * ce cas la mise à jour est appliquée AUTOMATIQUEMENT au prochain redémarrage.
     */
    public void reinstall(org.bukkit.command.CommandSender s) {
        s.sendMessage(Text.mm("<gray>[Plugins] Téléchargement de la dernière version depuis <white>" + repo + "</white>…"));
        schedulers().async(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "MoonCore-Updater")
                        .header("Accept", "application/vnd.github+json").GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() >= 400) throw new Exception("GitHub HTTP " + resp.statusCode());
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : "?";
                String url = jarAssetUrl(root);
                if (url == null) { sync(s, "<red>[Plugins] Release " + tag + " sans asset .jar."); return; }
                java.io.File staged = downloadToUpdateFolder(http, url);
                schedulers().sync(() -> applyStaged(s, staged, tag));
            } catch (Exception e) {
                sync(s, "<red>[Plugins] Échec : " + e.getMessage());
            }
        });
    }

    private void sync(org.bukkit.command.CommandSender s, String mm) {
        schedulers().sync(() -> s.sendMessage(Text.mm(mm)));
    }

    private void applyStaged(org.bukkit.command.CommandSender s, java.io.File staged, String tag) {
        pending = tag;
        if (tryHotReload(staged)) {
            s.sendMessage(Text.mm("<green>[Plugins] MoonCore <white>" + tag + "</white> réinstallé À CHAUD (sans redémarrage)."));
        } else {
            s.sendMessage(Text.mm("<yellow>[Plugins] <white>" + tag + "</white> téléchargé et mis en attente — "
                    + "appliqué AUTOMATIQUEMENT au prochain redémarrage (hot-reload indisponible : jar verrouillé)."));
        }
    }

    /** Rechargement à chaud best-effort (PlugMan-like). False si non supporté → appliqué au redémarrage. */
    @SuppressWarnings({"unchecked", "removal", "deprecation"})
    private boolean tryHotReload(java.io.File staged) {
        org.bukkit.plugin.PluginManager pm = plugin().getServer().getPluginManager();
        if (!(pm instanceof org.bukkit.plugin.SimplePluginManager spm) || staged == null || !staged.isFile()) return false;
        org.bukkit.plugin.Plugin self = plugin();
        try {
            // Déréférence l'ancien plugin des registres internes (sinon loadPlugin refuse le doublon).
            java.lang.reflect.Field fp = org.bukkit.plugin.SimplePluginManager.class.getDeclaredField("plugins");
            java.lang.reflect.Field fl = org.bukkit.plugin.SimplePluginManager.class.getDeclaredField("lookupNames");
            fp.setAccessible(true); fl.setAccessible(true);
            java.util.List<org.bukkit.plugin.Plugin> plugins = (java.util.List<org.bukkit.plugin.Plugin>) fp.get(spm);
            java.util.Map<String, org.bukkit.plugin.Plugin> lookup = (java.util.Map<String, org.bukkit.plugin.Plugin>) fl.get(spm);

            pm.disablePlugin(self);
            plugins.remove(self);
            lookup.remove(self.getName());
            lookup.remove(self.getName().toLowerCase(java.util.Locale.ROOT));

            org.bukkit.plugin.Plugin np = pm.loadPlugin(staged); // charge depuis le jar (non verrouillé) du dossier update/
            if (np == null) throw new IllegalStateException("loadPlugin a renvoyé null");
            np.onLoad();
            pm.enablePlugin(np);
            return true;
        } catch (Throwable t) {
            log().warn("[Plugins] Hot-reload impossible (" + t + ") → sera appliqué au redémarrage.");
            // Filet de sécurité : s'assurer que MoonCore reste actif si on l'avait désactivé.
            try { if (!self.isEnabled()) pm.enablePlugin(self); } catch (Throwable ignored) { }
            return false;
        }
    }

    private void notify(String version, boolean downloaded) {
        if (!notifyAdmins) return;
        String msg = downloaded
                ? "<green>[MoonCore] Mise à jour <white>" + version + "</white> téléchargée — redémarre le serveur pour l'appliquer."
                : "<yellow>[MoonCore] Mise à jour <white>" + version + "</white> disponible sur GitHub.";
        schedulers().sync(() -> {
            for (Player p : plugin().getServer().getOnlinePlayers())
                if (p.hasPermission("mooncore.admin")) p.sendMessage(Text.mm(msg));
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (pending != null && e.getPlayer().hasPermission("mooncore.admin")) {
            e.getPlayer().sendMessage(Text.mm("<green>[MoonCore] Mise à jour <white>" + pending
                    + "</white> en attente — redémarre pour l'appliquer."));
        }
    }

    /** Compare deux versions « a.b.c » (préfixe v et suffixes ignorés). >0 si v1 plus récent. */
    static int compare(String v1, String v2) {
        String[] a = clean(v1).split("\\."), b = clean(v2).split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? num(a[i]) : 0, y = i < b.length ? num(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static String clean(String v) {
        int cut = v.indexOf('-'); // retire -SNAPSHOT, -beta, etc.
        if (cut >= 0) v = v.substring(0, cut);
        return v.replaceAll("[^0-9.]", "");
    }

    private static int num(String s) {
        try { return s.isBlank() ? 0 : Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
