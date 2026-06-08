package com.mooncore.modules.ai.command;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.ai.AiActionValidator;
import com.mooncore.modules.ai.AiAdminModule;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Commande {@code /moon ai ...} : assistant IA admin. Tous les appels sont asynchrones
 * (jamais de blocage du thread principal), validés avant application, et audités.
 * 100% texte → compatible Bedrock.
 */
public final class AiSubCommand implements SubCommand {

    private final AiAdminModule module;

    public AiSubCommand(AiAdminModule module) {
        this.module = module;
    }

    @Override public String name() { return "ai"; }
    @Override public String permission() { return "mooncore.admin.ai"; }
    @Override public String description() { return "Assistant IA (admin)"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "model" -> model(s, a);
            case "set" -> setCfg(s, a);
            case "reload" -> { module.reloadModule(); msg(s, "<green>Configuration IA rechargée."); }
            case "history" -> history(s, a);
            case "ask", "question", "chat" -> askQuestion(s, a);
            case "config", "configure" -> configModule(s, a);
            case "code" -> code(s, a);
            case "coderun" -> codeRun(s);
            case "createitem" -> createItem(s, a);
            case "createboss" -> createBoss(s, a);
            case "createblock", "createore" -> createBlock(s, a);
            case "retexture" -> retexture(s, a);
            case "modifyitem" -> modifyItem(s, a);
            case "createbossdrop" -> createBossDrop(s, a);
            case "createreward" -> createReward(s, a);
            case "createrecipe" -> createRecipe(s, a);
            case "balanceitem" -> balanceItem(s, a);
            case "generatelore" -> generateLore(s, a);
            case "describeitem" -> describeItem(s, a);
            case "create", "make", "cree", "créer" -> createUnified(s, join(a, 1));
            default -> {
                // "Tout passe par l'IA" : toute saisie non reconnue = demande de création libre.
                createUnified(s, join(a, 0));
            }
        }
    }

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    /**
     * Commande unifiée : l'IA choisit le(s) type(s) (item/bloc/boss) et peut créer
     * PLUSIEURS éléments d'un coup, avec génération de textures et un seul rebuild du pack.
     */
    @SuppressWarnings("unchecked")
    private void createUnified(CommandSender s, String prompt) {
        if (prompt == null || prompt.isBlank()) { help(s); return; }
        var ci = module.customItemModule();
        var bm = module.bossModule();
        var cb = module.blockModule();
        ask(s, "create", prompt, module.prompts().unifiedCreateSystem(), text -> {
            java.util.Map<String, Object> root = module.validator().extractMap(text);
            Object list = root == null ? null : root.get("creations");
            if (!(list instanceof java.util.List<?> creations) || creations.isEmpty()) {
                fail(s, "create", prompt, "rien à créer (réponse IA inattendue)"); return;
            }
            java.util.List<java.util.concurrent.CompletableFuture<Void>> tex = new java.util.ArrayList<>();
            int[] count = {0, 0, 0}; // items, blocks, bosses
            int max = Math.min(8, creations.size());
            for (int i = 0; i < max; i++) {
                if (!(creations.get(i) instanceof java.util.Map<?, ?> elRaw)) continue;
                java.util.Map<String, Object> el = (java.util.Map<String, Object>) elRaw;
                String kind = String.valueOf(el.getOrDefault("kind", "item")).toLowerCase(Locale.ROOT);
                try {
                    switch (kind) {
                        case "block", "ore", "minerai" -> {
                            if (cb == null) break;
                            var def = buildBlockDef(el);
                            cb.put(def);
                            count[1]++;
                            msg(s, "<green>▸ bloc <white>" + def.id());
                            tex.add(genTexture(blockTexturePrompt(def.displayName()), false,
                                    new java.io.File(cb.store().texturesFolder(), def.id() + ".png"),
                                    () -> { var d = cb.rawDef(def.id()); if (d != null) { d.setModelKey(def.id()); cb.put(d); } }));
                        }
                        case "boss", "mob" -> {
                            if (bm == null) break;
                            Object dn = el.get("display-name");
                            String id = bossId(dn != null ? dn.toString() : prompt + "_" + i);
                            if (bm.createBoss(id, el)) { count[2]++; msg(s, "<green>▸ boss <white>" + id + " <gray>(/moon boss spawn " + id + ")"); }
                        }
                        default -> { // item
                            if (ci == null) break;
                            AiActionValidator.Result r = module.validator().validateItem(GSON.toJson(el), null);
                            if (r.ok() && r.def() != null) {
                                CustomItemDef def = r.def();
                                ci.put(def);
                                count[0]++;
                                msg(s, "<green>▸ objet <white>" + def.id());
                                tex.add(genTexture(itemTexturePrompt(def.displayName(), def.rarity().id(), def.type().id()),
                                        true, new java.io.File(ci.texturesFolder(), def.id() + ".png"),
                                        () -> { def.setModelKey(def.id()); if (def.customModelData() <= 0) def.setCustomModelData(ci.nextCustomModelData()); ci.put(def); }));
                            }
                        }
                    }
                } catch (Exception ex) {
                    msg(s, "<yellow>⚠ élément ignoré (" + kind + ") : " + ex.getMessage());
                }
            }
            int total = count[0] + count[1] + count[2];
            if (total == 0) { fail(s, "create", prompt, "aucun élément valide généré"); return; }
            msg(s, "<gray>🎨 Génération des textures (" + tex.size() + ")…");
            java.util.concurrent.CompletableFuture
                    .allOf(tex.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .whenComplete((v, e) -> sync(() -> {
                        module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class)
                                .ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
                        msg(s, "<green>✔ Créé : <white>" + count[0] + " objet(s), " + count[1]
                                + " bloc(s), " + count[2] + " boss<green>. Pack mis à jour.");
                    }));
            ok(s, "create", prompt, total + " éléments");
        });
    }

    private com.mooncore.modules.customblock.CustomBlockDef buildBlockDef(java.util.Map<String, Object> el) {
        Object dn = el.get("display-name");
        String id = bossId(dn != null ? dn.toString() : "bloc");
        var cb = module.blockModule();
        if (cb.rawDef(id) != null) id = id + "_" + Math.abs(("" + el).hashCode() % 1000);
        var def = new com.mooncore.modules.customblock.CustomBlockDef(id);
        if (dn != null) def.setDisplayName(dn.toString());
        def.setDropXp(mInt(el.get("drop-xp"), 0));
        def.setRequiresPickaxe(mBool(el.get("requires-pickaxe"), true));
        if (el.get("worldgen") instanceof java.util.Map<?, ?> g) {
            def.setGenerate(mBool(g.get("generate"), false));
            if (g.get("replace") instanceof String rs) {
                org.bukkit.Material m = org.bukkit.Material.matchMaterial(rs.toUpperCase(Locale.ROOT));
                if (m != null) def.setReplace(m);
            }
            def.setYRange(mInt(g.get("min-y"), -16), mInt(g.get("max-y"), 64));
            def.setVeinsPerChunk(Math.max(1, Math.min(8, mInt(g.get("veins-per-chunk"), 2))));
            def.setVeinSize(Math.max(1, Math.min(10, mInt(g.get("vein-size"), 4))));
        }
        return def;
    }

    /** Génère une texture (si activé) et l'écrit ; le rebuild pack est fait par l'appelant à la fin. */
    private java.util.concurrent.CompletableFuture<Void> genTexture(String prompt, boolean removeBg,
                                                                    java.io.File out, Runnable onWritten) {
        if (!module.client().config().generateTextures()) return java.util.concurrent.CompletableFuture.completedFuture(null);
        return module.client().generateTexture(prompt, removeBg).handle((png, err) -> {
            if (png != null) {
                try {
                    out.getParentFile().mkdirs();
                    java.nio.file.Files.write(out.toPath(), png);
                    sync(onWritten);
                } catch (Exception ignored) { }
            } else if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                if (c instanceof com.mooncore.modules.ai.AiException ae && ae.quota()) sync(this::alertAdminsQuota);
            }
            return null;
        });
    }

    private static String itemTexturePrompt(String name, String rarity, String type) {
        return "true 16-bit PIXEL ART game item icon of " + Text.strip(name) + " (" + rarity + " " + type + "). "
                + "Hard pixel edges, limited color palette, NO anti-aliasing, NO gradients, NO blur. "
                + "Single centered object, flat front view, thick readable silhouette, "
                + "pure solid white background (#FFFFFF), no shadow, no text, no border. Minecraft/Terraria style.";
    }

    private static String blockTexturePrompt(String name) {
        return "true 16-bit PIXEL ART seamless tileable Minecraft block texture of " + Text.strip(name) + ". "
                + "Hard pixel edges, limited palette, NO anti-aliasing, NO gradients, NO blur. "
                + "Top-down flat, fills the whole square, no transparency, no border, no text.";
    }

    // ---------------- model / reload / history ----------------

    private void model(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai model <list|set> [modèle]"); return; }
        if (a[1].equalsIgnoreCase("list")) {
            msg(s, "<gray>Modèle actuel : <white>" + module.client().config().model());
            msg(s, "<gray>Disponibles : <white>" + String.join(", ", module.client().config().availableModels()));
        } else if (a[1].equalsIgnoreCase("set") && a.length >= 3) {
            module.setModel(a[2]);
            msg(s, "<green>Modèle = <white>" + a[2]);
        } else {
            msg(s, "<red>/moon ai model <list|set> [modèle]");
        }
    }

    private static final java.util.Set<String> SETTABLE = java.util.Set.of(
            "provider", "model", "api-key", "endpoint", "temperature",
            "image-model", "generate-textures", "max-output-tokens",
            "timeout-seconds", "max-requests-per-minute", "developer-mode", "javac-path");

    private void setCfg(CommandSender s, String[] a) {
        if (a.length < 3) {
            msg(s, "<red>/moon ai set <clé> <valeur>");
            msg(s, "<gray>Clés : <white>" + String.join(", ", SETTABLE));
            msg(s, "<gray>Ex (autre API) : <white>/moon ai set provider openai");
            msg(s, "<gray>     <white>/moon ai set endpoint https://api.openai.com/v1/chat/completions");
            msg(s, "<gray>     <white>/moon ai set api-key sk-...");
            return;
        }
        String key = a[1].toLowerCase(Locale.ROOT);
        if (!SETTABLE.contains(key)) { msg(s, "<red>Clé inconnue. Valides : " + String.join(", ", SETTABLE)); return; }
        String value = join(a, 2);
        module.setConfigValue(key, value);
        // La clé API n'est JAMAIS réaffichée (sécurité).
        if (key.equals("api-key")) {
            msg(s, "<green>Clé API mise à jour (masquée). Provider actuel : <white>" + module.client().config().provider());
        } else {
            msg(s, "<green>" + key + " = <white>" + value);
        }
    }

    @SuppressWarnings("unchecked")
    private void configModule(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai config <ce que tu veux changer...>"); return; }
        String prompt = join(a, 1);
        String sys = module.prompts().configSchemaSystem(module.moduleIds());
        ask(s, "config", prompt, sys, text -> {
            java.util.Map<String, Object> fields = module.validator().extractMap(text);
            if (fields == null || !(fields.get("module") instanceof String mod)) {
                fail(s, "config", prompt, "JSON invalide (module manquant)"); return;
            }
            Object v = fields.get("values");
            if (!(v instanceof java.util.Map)) { fail(s, "config", prompt, "aucune valeur"); return; }
            java.util.Map<String, Object> values = (java.util.Map<String, Object>) v;
            String err = module.applyModuleConfig(mod, values);
            if (err != null) { fail(s, "config", prompt, err); return; }
            msg(s, "<green>✔ Module <white>" + mod + "<green> reconfiguré et rechargé :");
            values.forEach((k, val) -> msg(s, " <dark_gray>▸ <gray>" + k + " <white>= " + val));
            ok(s, "config", prompt, "config " + mod + " " + values.keySet());
        });
    }

    // Code Java généré en attente de revue (par admin).
    private final java.util.Map<String, String> pendingCode = new java.util.concurrent.ConcurrentHashMap<>();

    private void code(CommandSender s, String[] a) {
        if (!module.devMode()) {
            msg(s, "<red>Mode développeur désactivé.</red> <gray>Active-le (risque : exécution de code Java) :");
            msg(s, " <white>/moon ai set developer-mode true</white> <gray>(puis /moon ai reload)");
            msg(s, " <gray>Requiert un <white>JDK</white> (javac) côté serveur. Réservé à l'owner.");
            return;
        }
        if (!module.scriptEngine().available(module.javacPath())) {
            msg(s, "<red>Pas de compilateur Java (javac) trouvé : le serveur tourne sur un JRE.");
            msg(s, "<gray>Règle le chemin de ton JDK : <white>/moon ai set javac-path C:\\...\\jdk-21\\bin\\javac.exe");
            return;
        }
        if (a.length < 2) { msg(s, "<red>/moon ai code <description de ce que le code doit faire...>"); return; }
        String prompt = join(a, 1);
        ask(s, "code", prompt, module.prompts().codeSystem(), text -> {
            String src = stripFences(text);
            if (!src.contains("implements") || !src.contains("GeneratedScript")) {
                fail(s, "code", prompt, "réponse IA inattendue (pas de classe GeneratedScript)"); return;
            }
            pendingCode.put(s.getName(), src);
            try {
                java.io.File dir = new java.io.File(module.mc().getDataFolder(), "scripts");
                dir.mkdirs();
                java.nio.file.Files.writeString(new java.io.File(dir, "GeneratedScript.java").toPath(), src);
            } catch (Exception ignored) { }
            msg(s, "<gradient:#8a2be2:#c77dff>Code généré</gradient> <gray>(aperçu) :");
            String[] lines = src.split("\n");
            for (int i = 0; i < Math.min(lines.length, 14); i++) msg(s, "<dark_gray>" + lines[i]);
            if (lines.length > 14) msg(s, "<dark_gray>… (" + lines.length + " lignes, voir plugins/MoonCore/scripts/GeneratedScript.java)");
            msg(s, "<yellow>⚠ Vérifie le code, puis <white>/moon ai coderun</white> <yellow>pour l'exécuter.");
            ok(s, "code", prompt, "code généré (" + lines.length + " lignes)");
        });
    }

    private void codeRun(CommandSender s) {
        if (!module.devMode()) { msg(s, "<red>Mode développeur désactivé."); return; }
        String src = pendingCode.get(s.getName());
        if (src == null) { msg(s, "<red>Aucun code en attente. Génère-le d'abord : /moon ai code <desc>"); return; }
        msg(s, "<gray>Compilation + exécution…");
        String err = module.scriptEngine().compileAndRun(src, module.mc(), s, module.javacPath());
        if (err == null) {
            msg(s, "<green>✔ Code exécuté sans erreur.");
            module.audit().record(s.getName(), "coderun", "GeneratedScript", "OK", "OK", System.currentTimeMillis());
        } else {
            msg(s, "<red>✖ " + err);
            module.audit().record(s.getName(), "coderun", "GeneratedScript", err, "ERREUR", System.currentTimeMillis());
        }
    }

    private static String stripFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            int end = t.lastIndexOf("```");
            if (end >= 0) t = t.substring(0, end);
        }
        return t.trim();
    }

    private void askQuestion(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai ask <ta question...>"); return; }
        String prompt = join(a, 1);
        ask(s, "ask", prompt, module.prompts().assistantSystem(), text -> {
            msg(s, "<gradient:#8a2be2:#c77dff>IA</gradient> <dark_gray>»");
            for (String line : text.split("\n")) if (!line.isBlank()) msg(s, "<white>" + line.trim());
            ok(s, "ask", prompt, "réponse Q&A");
        });
    }

    private void createBoss(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai createboss <description...>"); return; }
        var bm = module.bossModule();
        if (bm == null) { msg(s, "<red>Module boss inactif."); return; }
        String prompt = join(a, 1);
        ask(s, "createboss", prompt, module.prompts().bossSchemaSystem(), text -> {
            java.util.Map<String, Object> fields = module.validator().extractMap(text);
            if (fields == null) { fail(s, "createboss", prompt, "JSON invalide"); return; }
            Object dn = fields.get("display-name");
            String id = bossId(dn != null ? dn.toString() : prompt);
            if (bm.createBoss(id, fields)) {
                msg(s, "<green>✔ Boss <white>" + id + "<green> créé. Invoque-le : <white>/moon boss spawn " + id);
                ok(s, "createboss", prompt, "boss " + id);
            } else {
                fail(s, "createboss", prompt, "données de boss invalides");
            }
        });
    }

    private void createBlock(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai createblock <description...>"); return; }
        var cb = module.blockModule();
        if (cb == null) { msg(s, "<red>Module custom-block inactif."); return; }
        String prompt = join(a, 1);
        ask(s, "createblock", prompt, module.prompts().blockSchemaSystem(), text -> {
            java.util.Map<String, Object> f = module.validator().extractMap(text);
            if (f == null) { fail(s, "createblock", prompt, "JSON invalide"); return; }
            Object dn = f.get("display-name");
            String id = bossId(dn != null ? dn.toString() : prompt);
            if (cb.rawDef(id) != null) id = id + "_" + Math.abs(prompt.hashCode() % 1000);

            com.mooncore.modules.customblock.CustomBlockDef def =
                    new com.mooncore.modules.customblock.CustomBlockDef(id);
            if (dn != null) def.setDisplayName(dn.toString());
            def.setDropXp(mInt(f.get("drop-xp"), 0));
            def.setRequiresPickaxe(mBool(f.get("requires-pickaxe"), true));
            if (f.get("worldgen") instanceof java.util.Map<?, ?> g) {
                def.setGenerate(mBool(g.get("generate"), false));
                if (g.get("replace") instanceof String rs) {
                    org.bukkit.Material m = org.bukkit.Material.matchMaterial(rs.toUpperCase(Locale.ROOT));
                    if (m != null) def.setReplace(m);
                }
                def.setYRange(mInt(g.get("min-y"), -16), mInt(g.get("max-y"), 64));
                def.setVeinsPerChunk(Math.max(1, Math.min(8, mInt(g.get("veins-per-chunk"), 2))));
                def.setVeinSize(Math.max(1, Math.min(10, mInt(g.get("vein-size"), 4))));
            }
            cb.put(def);
            msg(s, "<green>✔ Bloc <white>" + def.id() + "<green> créé"
                    + (def.generate() ? " (minerai, remplace " + def.replace().name() + ")" : "")
                    + ". <gray>Reçois-le : <white>/moon block get " + def.id());
            ok(s, "createblock", prompt, "block " + def.id());

            // Génère la texture (opaque, pas de transparence pour un bloc) + reconstruit le pack.
            final String fid = def.id();
            msg(s, "<gray>🎨 Génération de la texture du bloc…");
            module.client().generateTexture(blockTexturePrompt(def.displayName()), false).whenComplete((png, err) -> sync(() -> {
                if (err != null) {
                    Throwable c = err.getCause() != null ? err.getCause() : err;
                    msg(s, "<yellow>⚠ Texture non générée : " + c.getMessage());
                    if (c instanceof com.mooncore.modules.ai.AiException ae && ae.quota()) alertAdminsQuota();
                    return;
                }
                try {
                    java.io.File out = new java.io.File(cb.store().texturesFolder(), fid + ".png");
                    java.nio.file.Files.write(out.toPath(), png);
                    com.mooncore.modules.customblock.CustomBlockDef d2 = cb.rawDef(fid);
                    if (d2 != null) { d2.setModelKey(fid); cb.put(d2); }
                    module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class)
                            .ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
                    msg(s, "<green>🖼 Texture du bloc générée et appliquée. Pack mis à jour.");
                } catch (Exception e) {
                    msg(s, "<yellow>⚠ Écriture texture échouée : " + e.getMessage());
                }
            }));
        });
    }

    private static int mInt(Object o, int def) { return o instanceof Number n ? n.intValue() : def; }
    private static boolean mBool(Object o, boolean def) { return o instanceof Boolean b ? b : def; }

    private static String bossId(String src) {
        String s = com.mooncore.util.Text.strip(src).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_");
        if (s.isBlank()) s = "ai_boss";
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private void history(CommandSender s, String[] a) {
        int limit = a.length >= 2 ? parseInt(a[1], 10) : 10;
        module.audit().recent(limit).whenComplete((entries, err) -> sync(() -> {
            if (err != null) { msg(s, "<red>Erreur historique : " + err.getMessage()); return; }
            if (entries.isEmpty()) { msg(s, "<gray>Aucune action IA enregistrée."); return; }
            msg(s, "<gradient:#8a2be2:#c77dff>Historique IA</gradient> <dark_gray>(" + entries.size() + ")");
            for (var e : entries) {
                msg(s, " <dark_gray>• <gray>" + e.admin() + " <white>" + e.command()
                        + " <" + (e.status().startsWith("OK") ? "green" : "red") + ">" + e.status()
                        + " <dark_gray>« <gray>" + shorten(e.prompt(), 40) + " <dark_gray>»");
            }
        }));
    }

    // ---------------- item-producing commands ----------------

    private void createItem(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai createitem <description...>"); return; }
        String prompt = join(a, 1);
        ask(s, "createitem", prompt, module.prompts().itemSchemaSystem(), text -> {
            AiActionValidator.Result r = module.validator().validateItem(text, null);
            applyNewDef(s, "createitem", prompt, r, null);
        });
    }

    private void modifyItem(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon ai modifyitem <id> <description...>"); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { msg(s, "<red>CustomItemManager requis."); return; }
        CustomItemDef cur = ci.rawDef(a[1]);
        if (cur == null) { msg(s, "<red>Objet inconnu : " + a[1]); return; }
        String prompt = "Objet actuel : " + summary(cur) + "\nModification demandée : " + join(a, 2)
                + "\nRenvoie l'objet COMPLET mis à jour (même id).";
        ask(s, "modifyitem", prompt, module.prompts().itemSchemaSystem(), text -> {
            AiActionValidator.Result r = module.validator().validateItem(text, cur.id());
            applyNewDef(s, "modifyitem", prompt, r, null);
        });
    }

    private void createBossDrop(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon ai createbossdrop <bossId> <description...>"); return; }
        String bossId = a[1].toLowerCase(Locale.ROOT);
        String prompt = join(a, 2);
        ask(s, "createbossdrop", prompt, module.prompts().itemSchemaSystem(), text -> {
            AiActionValidator.Result r = module.validator().validateItem(text, null);
            applyNewDef(s, "createbossdrop", prompt, r,
                    def -> def.drops().add(new CustomItemDef.DropRule("boss:" + bossId, 0.25, 1, 1)));
        });
    }

    private void createReward(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon ai createreward <eventId> <description...>"); return; }
        String eventId = a[1].toLowerCase(Locale.ROOT);
        String prompt = join(a, 2);
        ask(s, "createreward", prompt, module.prompts().itemSchemaSystem(), text -> {
            AiActionValidator.Result r = module.validator().validateItem(text, null);
            applyNewDef(s, "createreward", prompt, r,
                    def -> def.drops().add(new CustomItemDef.DropRule("event:" + eventId, 1.0, 1, 1)));
        });
    }

    private void createRecipe(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon ai createrecipe <id> <contraintes...>"); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { msg(s, "<red>CustomItemManager requis."); return; }
        CustomItemDef def = ci.rawDef(a[1]);
        if (def == null) { msg(s, "<red>Objet inconnu : " + a[1]); return; }
        String prompt = "Objet : " + summary(def) + "\nContraintes : " + join(a, 2);
        ask(s, "createrecipe", prompt, module.prompts().recipeSchemaSystem(), text -> {
            List<String> warnings = new java.util.ArrayList<>();
            CustomItemDef.Recipe recipe = module.validator().extractRecipe(text, warnings);
            if (recipe == null) { fail(s, "createrecipe", prompt, "recette invalide"); return; }
            def.setRecipe(recipe);
            ci.put(def);
            ci.recipeManager().unregisterAll();
            ci.recipeManager().registerAll();
            warnings.forEach(w -> msg(s, " <yellow>⚠ " + w));
            ok(s, "createrecipe", prompt, "recette appliquée à " + def.id());
        });
    }

    private void balanceItem(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai balanceitem <id> [consignes...]"); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { msg(s, "<red>CustomItemManager requis."); return; }
        CustomItemDef def = ci.rawDef(a[1]);
        if (def == null) { msg(s, "<red>Objet inconnu : " + a[1]); return; }
        String prompt = "Rééquilibre cet objet : " + summary(def)
                + (a.length > 2 ? "\nConsignes : " + join(a, 2) : "")
                + "\nRenvoie l'objet COMPLET équilibré (même id, type, rareté).";
        ask(s, "balanceitem", prompt, module.prompts().itemSchemaSystem(), text -> {
            AiActionValidator.Result r = module.validator().validateItem(text, def.id());
            applyNewDef(s, "balanceitem", prompt, r, null);
        });
    }

    private void generateLore(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai generatelore <id> [thème...]"); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { msg(s, "<red>CustomItemManager requis."); return; }
        CustomItemDef def = ci.rawDef(a[1]);
        if (def == null) { msg(s, "<red>Objet inconnu : " + a[1]); return; }
        String prompt = "Objet : " + summary(def) + (a.length > 2 ? "\nThème : " + join(a, 2) : "");
        ask(s, "generatelore", prompt, module.prompts().loreSchemaSystem(), text -> {
            List<String> lore = module.validator().extractLore(text);
            if (lore.isEmpty()) { fail(s, "generatelore", prompt, "lore vide"); return; }
            def.lore().clear();
            def.lore().addAll(lore);
            ci.put(def);
            lore.forEach(l -> msg(s, "  <gray>" + l));
            ok(s, "generatelore", prompt, lore.size() + " ligne(s) appliquée(s) à " + def.id());
        });
    }

    private void describeItem(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon ai describeitem <id>"); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { msg(s, "<red>CustomItemManager requis."); return; }
        CustomItemDef def = ci.rawDef(a[1]);
        if (def == null) { msg(s, "<red>Objet inconnu : " + a[1]); return; }
        String prompt = "Décris cet objet : " + summary(def);
        ask(s, "describeitem", prompt, module.prompts().describeSystem(), text -> {
            msg(s, "<gradient:#8a2be2:#c77dff>" + def.id() + "</gradient>");
            for (String line : text.split("\n")) if (!line.isBlank()) msg(s, " <gray>" + line.trim());
            ok(s, "describeitem", prompt, "description générée");
        });
    }

    // ---------------- core async runner ----------------

    private void ask(CommandSender s, String command, String prompt, String system, Consumer<String> onText) {
        if (!module.client().config().hasApiKey()) {
            msg(s, "<red>Aucune clé API configurée. <gray>modules/ai-assistant.yml → api-key");
            return;
        }
        if (!module.client().tryAcquireRate(System.currentTimeMillis())) {
            msg(s, "<red>Limite de requêtes IA atteinte. Réessaie dans une minute.");
            return;
        }
        msg(s, "<gray>⏳ L'IA réfléchit… <dark_gray>(" + module.client().config().model() + ")");
        module.client().ask(system, prompt).whenComplete((text, err) -> sync(() -> {
            if (err != null) {
                Throwable cause = err.getCause() != null ? err.getCause() : err;
                fail(s, command, prompt, cause.getMessage());
                if (cause instanceof com.mooncore.modules.ai.AiException ae && ae.quota()) {
                    alertAdminsQuota();
                }
                return;
            }
            try {
                onText.accept(text);
            } catch (Throwable t) {
                fail(s, command, prompt, "application échouée : " + t.getMessage());
            }
        }));
    }

    /** Applique une définition validée : persiste, donne un aperçu, audite. */
    private void applyNewDef(CommandSender s, String command, String prompt,
                             AiActionValidator.Result r, Consumer<CustomItemDef> mutator) {
        if (!r.ok()) { fail(s, command, prompt, r.error()); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { fail(s, command, prompt, "CustomItemManager indisponible"); return; }
        CustomItemDef def = r.def();
        if (mutator != null) mutator.accept(def);
        ci.put(def);
        ci.recipeManager().registerAll();
        msg(s, "<green>✔ Objet <white>" + def.id() + "<green> créé/mis à jour.");
        for (String w : r.warnings()) msg(s, " <yellow>⚠ " + w);
        if (s instanceof Player p) {
            ci.give(p, def.id(), 1);
            msg(s, "<gray>Aperçu donné dans ton inventaire.");
        } else {
            msg(s, "<gray>/moon item give <joueur> " + def.id());
        }
        ok(s, command, prompt, "item " + def.id() + (r.warnings().isEmpty() ? "" : " (" + r.warnings().size() + " warn)"));

        maybeGenerateTexture(s, def);
    }

    /** L'IA refait la texture d'un objet EXISTANT à partir d'une description. */
    private void retexture(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon ai retexture <id> <description...>"); return; }
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) { msg(s, "<red>Module custom-item inactif."); return; }
        CustomItemDef def = ci.rawDef(a[1]);
        if (def == null) { msg(s, "<red>Id inconnu : " + a[1]); return; }
        String desc = join(a, 2);
        String prompt = itemTexturePrompt(desc, def.rarity().id(), def.type().id());
        msg(s, "<gray>🎨 Génération de la nouvelle texture…");
        module.client().generateTexture(prompt, true).whenComplete((png, err) -> sync(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                msg(s, "<yellow>⚠ Texture non générée : " + c.getMessage());
                if (c instanceof com.mooncore.modules.ai.AiException ae && ae.quota()) alertAdminsQuota();
                return;
            }
            try {
                java.io.File out = new java.io.File(ci.texturesFolder(), def.id() + ".png");
                java.nio.file.Files.write(out.toPath(), png);
                def.setModelKey(def.id());
                if (def.customModelData() <= 0) def.setCustomModelData(ci.nextCustomModelData());
                ci.put(def);
                module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class)
                        .ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
                msg(s, "<green>🖼 Nouvelle texture appliquée à <white>" + def.id() + "<green>. Pack mis à jour.");
                ok(s, "retexture", desc, "retexture " + def.id());
            } catch (Exception e) {
                msg(s, "<yellow>⚠ Écriture texture échouée : " + e.getMessage());
            }
        }));
    }

    /** Génère une texture via l'API image puis reconstruit/repousse le pack forcé. */
    private void maybeGenerateTexture(CommandSender s, CustomItemDef def) {
        if (!module.client().config().generateTextures()) return;
        CustomItemManagerModule ci = module.customItemModule();
        if (ci == null) return;
        String prompt = itemTexturePrompt(def.displayName(), def.rarity().id(), def.type().id());
        msg(s, "<gray>🎨 Génération de la texture…");
        module.client().generateTexture(prompt).whenComplete((png, err) -> sync(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                msg(s, "<yellow>⚠ Texture non générée : " + c.getMessage());
                return;
            }
            try {
                java.io.File out = new java.io.File(ci.texturesFolder(), def.id() + ".png");
                java.nio.file.Files.write(out.toPath(), png);
                def.setModelKey(def.id());
                if (def.customModelData() <= 0) def.setCustomModelData(ci.nextCustomModelData());
                ci.put(def);
                module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class)
                        .ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
                msg(s, "<green>🖼 Texture générée et appliquée (cmd " + def.customModelData()
                        + "). Le pack forcé est mis à jour.");
            } catch (Exception e) {
                msg(s, "<yellow>⚠ Écriture texture échouée : " + e.getMessage());
            }
        }));
    }

    // ---------------- audit + helpers ----------------

    private void ok(CommandSender s, String command, String prompt, String result) {
        module.audit().record(s.getName(), command, prompt, result, "OK", System.currentTimeMillis());
    }

    private void fail(CommandSender s, String command, String prompt, String reason) {
        msg(s, "<red>✖ Échec : " + (reason == null ? "inconnu" : reason));
        module.audit().record(s.getName(), command, prompt, String.valueOf(reason), "ERREUR",
                System.currentTimeMillis());
    }

    private void sync(Runnable r) {
        module.mc().schedulers().sync(r);
    }

    /** Prévient tous les admins en ligne (+ console) que l'API n'a plus de crédits/quota. */
    private void alertAdminsQuota() {
        String provider = module.client().config().provider();
        net.kyori.adventure.text.Component warn = Text.mm(
                "<red>⚠ [IA] L'API <white>" + provider + "</white> n'a plus de crédits/quota. "
                + "Rechargez le compte ou changez d'API : <white>/moon ai set api-key …</white>");
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("mooncore.admin.ai")) p.sendMessage(warn);
        }
        module.mc().logger().warn("[IA] Quota/crédits épuisés sur le fournisseur " + provider + ".");
    }

    private static String summary(CustomItemDef d) {
        String stats = d.stats().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(","));
        String abil = d.abilities().stream()
                .map(x -> x.id() + ":" + x.level()).collect(Collectors.joining(","));
        return "id=" + d.id() + " type=" + d.type().id() + " rarity=" + d.rarity().id()
                + " material=" + d.material().name() + " stats={" + stats + "} abilities=[" + abil + "]";
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon ai</gradient> <gray>— assistant IA admin");
        String[] lines = {
                "<gold>create <description></gold> — l'IA choisit le type et crée (plusieurs possibles !)",
                "ask <question> (poser une question libre à l'IA)",
                "config <instruction> (modifie la config d'un module existant)",
                "code <desc> + coderun (générer/exécuter du Java — mode dev, JDK requis)",
                "model <list|set> / set <clé> <valeur> (autre API) / reload / history [n]",
                "createitem <desc> / modifyitem <id> <desc> / balanceitem <id> [consignes]",
                "createboss <desc> / createblock <desc> (bloc/minerai) / createbossdrop <bossId> <desc>",
                "createreward <eventId> <desc>",
                "retexture <id> <desc> (l'IA refait la texture d'un objet)",
                "createrecipe <id> <contraintes> / generatelore <id> [thème] / describeitem <id>"
        };
        for (String l : lines) msg(s, " <dark_gray>▸ <gray>" + l);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, Math.min(from, a.length), a.length));
    }

    private static void msg(CommandSender s, String mm) {
        s.sendMessage(Text.mm(mm));
    }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "ask", "config", "code", "coderun", "model", "set", "reload", "history", "createitem", "modifyitem", "createboss",
                    "createblock", "retexture", "createbossdrop", "createreward", "createrecipe", "balanceitem", "generatelore", "describeitem"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "model" -> filter(List.of("list", "set"), a[1]);
                case "set" -> filter(new java.util.ArrayList<>(SETTABLE), a[1]);
                // commandes ciblant un objet existant → propose les ids
                case "modifyitem", "createrecipe", "balanceitem", "generatelore", "describeitem", "retexture" ->
                        filter(itemIds(), a[1]);
                default -> List.of();
            };
        }
        if (a.length == 3 && sub.equals("model") && a[1].equalsIgnoreCase("set")) {
            return filter(module.client().config().availableModels(), a[2]);
        }
        if (a.length == 3 && sub.equals("set") && a[1].equalsIgnoreCase("provider")) {
            return filter(List.of("openai", "anthropic"), a[2]);
        }
        return List.of();
    }

    private List<String> itemIds() {
        var ci = module.customItemModule();
        return ci == null ? List.of() : new java.util.ArrayList<>(ci.ids());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
