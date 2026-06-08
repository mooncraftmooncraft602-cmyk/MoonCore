package com.mooncore.modules.customitem.command;

import com.mooncore.MoonCore;
import com.mooncore.api.customitem.ItemStats;
import com.mooncore.api.customitem.ItemType;
import com.mooncore.api.customitem.Rarity;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.modules.customitem.ResourcePackBuilder;
import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import com.mooncore.modules.customitem.ability.Ability;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Commande admin {@code /moon item ...} : gestion complète des objets custom en jeu
 * (création, édition, stats, capacités, rareté, modèle, recettes, drops, récompenses,
 * import/export, génération de resource pack). Compatible Bedrock (100% texte/clic).
 */
public final class CustomItemSubCommand implements SubCommand {

    private final CustomItemManagerModule module;

    public CustomItemSubCommand(CustomItemManagerModule module) {
        this.module = module;
    }

    @Override public String name() { return "item"; }
    @Override public List<String> aliases() { return List.of("items", "ci"); }
    @Override public String permission() { return "mooncore.admin.items"; }
    @Override public String description() { return "Gestion des objets custom"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        String sub = a[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "menu", "gui" -> menu(s);
                case "paint", "draw" -> paint(s, a);
                case "importvanilla" -> importVanilla(s);
                case "list" -> list(s, a);
                case "info" -> info(s, a);
                case "create" -> create(s, a);
                case "clone" -> clone(s, a);
                case "delete", "remove" -> delete(s, a);
                case "rename" -> rename(s, a);
                case "give" -> give(s, a);
                case "get" -> get(s, a);
                case "edit" -> edit(s, a);
                case "stat" -> stat(s, a);
                case "ability" -> ability(s, a);
                case "rarity" -> rarity(s, a);
                case "model" -> model(s, a);
                case "recipe" -> recipe(s, a);
                case "drop" -> drop(s, a);
                case "reward" -> reward(s, a);
                case "export" -> msg(s, "<gray>Les définitions sont déjà des fichiers : <white>items/" + arg(a, 1, "<id>") + ".yml");
                case "import" -> { module.reloadDefinitions(); msg(s, "<green>Définitions rechargées depuis le disque."); }
                case "pack" -> pack(s, a);
                case "reload" -> { module.reloadModule(); msg(s, "<green>CustomItemManager rechargé."); }
                default -> help(s);
            }
        } catch (NumberFormatException nfe) {
            msg(s, "<red>Nombre invalide : " + nfe.getMessage());
        }
    }

    // ---------------- sous-actions ----------------

    private void importVanilla(CommandSender s) {
        java.io.File data = module.mc().getDataFolder();
        java.io.File src = com.mooncore.modules.customitem.VanillaTextureImporter.findSource(data);
        java.io.File vanillaDir = com.mooncore.modules.customitem.VanillaTextureImporter.vanillaFolder(data);
        java.io.File[] already = vanillaDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        int existing = already == null ? 0 : already.length;
        if (src == null) {
            if (existing > 0) {
                msg(s, "<green>✔ " + existing + " textures vanilla déjà disponibles — pas besoin de réimporter.");
                msg(s, "<gray>Pars d'une vanilla : <white>/moon item create monitem</white> → <white>/moon item paint monitem deepslate_diamond_ore</white>");
                msg(s, "<gray>…ou dans l'éditeur : livre <white>« Importer une texture »</white>.");
                return;
            }
            msg(s, "<red>Aucun fichier source.</red> <gray>Dépose le <white>.jar du client Minecraft</white> "
                    + "(ou un resource pack vanilla .zip) dans <white>plugins/MoonCore/import/</white>, puis relance.");
            return;
        }
        msg(s, "<gray>Extraction des textures vanilla depuis <white>" + src.getName() + "</white>…");
        var r = com.mooncore.modules.customitem.VanillaTextureImporter.extract(src, data);
        if (r.error() != null) {
            msg(s, "<yellow>⚠ " + r.error() + " (" + r.extracted() + " extraites)");
        } else {
            msg(s, "<green>✔ " + r.extracted() + " textures vanilla importées.");
            msg(s, "<gray>Tu peux maintenant partir d'une vanilla : <white>/moon item paint <nouvelId> <texture>");
            msg(s, "<gray>Ex : <white>/moon item create minerai_lunaire DEEPSLATE</white> puis "
                    + "<white>/moon item paint minerai_lunaire deepslate_diamond_ore");
        }
    }

    private void paint(CommandSender s, String[] a) {
        Player p = player(s);
        if (p == null) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (a.length < 2) { msg(s, "<red>/moon item paint <id> [taille 16|32] [importDepuisId]"); return; }
        CustomItemDef d = module.rawDef(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu. Crée-le d'abord : <white>/moon item create " + a[1]); return; }
        int size = 16; java.io.File source = null; // 16×16 par défaut (le plus facile)
        for (int i = 2; i < a.length; i++) {
            if (a[i].equals("16") || a[i].equals("32") || a[i].equals("64")) size = Integer.parseInt(a[i]);
            else {
                source = com.mooncore.modules.customitem.paint.PaintManager.resolveTexture(module.mc(), a[i]);
                if (source == null) { msg(s, "<yellow>Texture source introuvable pour : " + a[i] + " (toile vide)."); }
                else msg(s, "<gray>Import de la texture de <white>" + a[i] + "<gray> comme base.");
            }
        }
        module.paintManager().open(p, new com.mooncore.modules.customitem.paint.ItemPaintTarget(module, d.id()), size, source);
    }

    private void menu(CommandSender s) {
        Player p = player(s);
        if (p == null) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (module.rawDefs().isEmpty()) { msg(s, "<gray>Aucun objet custom. <white>/moon item create <id>"); return; }
        com.mooncore.modules.customitem.CustomItemMenu.open(module, p, 0);
    }

    private void list(CommandSender s, String[] a) {
        var defs = module.rawDefs().values();
        if (defs.isEmpty()) { msg(s, "<gray>Aucun objet custom défini. <white>/moon item create <id>"); return; }
        msg(s, "<gradient:#8a2be2:#c77dff>Objets custom</gradient> <dark_gray>(" + defs.size() + ")");
        for (CustomItemDef d : defs) {
            msg(s, " <dark_gray>▸ " + module.color(d.rarity()) + d.id()
                    + " <dark_gray>(<gray>" + d.type().id() + "<dark_gray>, " + module.label(d.rarity()) + ")");
        }
    }

    private void info(CommandSender s, String[] a) {
        CustomItemDef d = need(s, a, 1); if (d == null) return;
        msg(s, "<gradient:#8a2be2:#c77dff>" + d.id() + "</gradient>");
        msg(s, " <gray>Nom : <reset>" + d.displayName());
        msg(s, " <gray>Type : <white>" + d.type().id() + " <gray>Rareté : " + module.color(d.rarity()) + module.label(d.rarity()));
        msg(s, " <gray>Matériau : <white>" + d.material().name() + " <gray>CMD : <white>" + d.customModelData()
                + " <gray>Modèle : <white>" + (d.modelKey() == null ? "—" : d.modelKey()));
        msg(s, " <gray>Glow : <white>" + d.glowing() + " <gray>Incassable : <white>" + d.unbreakable());
        if (!d.stats().isEmpty()) {
            msg(s, " <gray>Stats : <white>" + d.stats().entrySet().stream()
                    .map(e -> ItemStats.label(e.getKey()) + "=" + e.getValue()).collect(Collectors.joining(", ")));
        }
        if (!d.abilities().isEmpty()) {
            msg(s, " <gray>Capacités : <white>" + d.abilities().stream()
                    .map(r -> r.id() + " " + r.level()).collect(Collectors.joining(", ")));
        }
        if (!d.drops().isEmpty()) {
            msg(s, " <gray>Drops :");
            for (int i = 0; i < d.drops().size(); i++) {
                var r = d.drops().get(i);
                msg(s, "  <dark_gray>[" + i + "] <white>" + r.source() + " <gray>chance=" + r.chance()
                        + " min=" + r.min() + " max=" + r.max());
            }
        }
        if (d.recipe() != null && !d.recipe().isEmpty()) {
            msg(s, " <gray>Recette : <white>" + d.recipe().shape + " " + d.recipe().ingredients);
        }
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon item create <id> [material]"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!com.mooncore.modules.customitem.CustomItemDefStore.isValidId(id)) {
            msg(s, "<red>Id invalide : minuscules, chiffres, _ et - uniquement (max 48)."); return;
        }
        if (module.rawDef(id) != null) { msg(s, "<red>Cet id existe déjà."); return; }
        CustomItemDef d = new CustomItemDef(id);
        ToolKind hint = ToolKind.fromText(id);
        if (hint != ToolKind.NONE) d.setTool(hint, ToolTier.IRON);
        if (a.length >= 3) {
            Material m = Material.matchMaterial(a[2].toUpperCase(Locale.ROOT));
            if (m == null) { msg(s, "<red>Matériau inconnu : " + a[2]); return; }
            d.setMaterial(m);
        }
        module.put(d);
        msg(s, "<green>Objet <white>" + id + "<green> créé.");
        Player p = player(s);
        if (p != null) com.mooncore.modules.customitem.editor.ItemEditorMenu.open(module, module.chatInput(), p, id);
        else msg(s, "<gray>Édite-le en jeu avec <white>/moon item edit " + id);
    }

    private void clone(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon item clone <src> <newId>"); return; }
        CustomItemDef src = module.rawDef(a[1]);
        if (src == null) { msg(s, "<red>Source inconnue."); return; }
        String newId = a[2].toLowerCase(Locale.ROOT);
        if (!com.mooncore.modules.customitem.CustomItemDefStore.isValidId(newId)) {
            msg(s, "<red>Id invalide : minuscules, chiffres, _ et - uniquement (max 48)."); return;
        }
        if (module.rawDef(newId) != null) { msg(s, "<red>Cet id existe déjà."); return; }
        module.put(src.cloneAs(newId));
        // unregister + register (un registerAll seul re-ajoute les clés déjà enregistrées → warnings « Duplicate recipe »).
        module.recipeManager().unregisterAll();
        module.recipeManager().registerAll();
        msg(s, "<green>Cloné <white>" + a[1] + "<green> → <white>" + newId);
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon item delete <id>"); return; }
        if (module.removeDef(a[1])) { msg(s, "<green>Supprimé : " + a[1]); module.recipeManager().unregisterAll(); module.recipeManager().registerAll(); }
        else msg(s, "<red>Id inconnu.");
    }

    private void rename(CommandSender s, String[] a) {
        CustomItemDef d = need(s, a, 1); if (d == null) return;
        if (a.length < 3) { msg(s, "<red>/moon item rename <id> <nom MiniMessage...>"); return; }
        d.setDisplayName(join(a, 2));
        module.put(d);
        msg(s, "<green>Nom mis à jour : <reset>" + d.displayName());
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon item give <joueur> <id> [quantité]"); return; }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) { msg(s, "<red>Joueur hors-ligne : " + a[1]); return; }
        if (module.rawDef(a[2]) == null) { msg(s, "<red>Id inconnu."); return; }
        int amount = a.length >= 4 ? Integer.parseInt(a[3]) : 1;
        module.give(target, a[2], amount);
        msg(s, "<green>Donné <white>" + amount + "× " + a[2] + "<green> à <white>" + target.getName());
    }

    private void get(CommandSender s, String[] a) {
        Player p = player(s); if (p == null) return;
        if (a.length < 2 || module.rawDef(a[1]) == null) { msg(s, "<red>/moon item get <id> [quantité]"); return; }
        int amount = a.length >= 3 ? Integer.parseInt(a[2]) : 1;
        module.give(p, a[1], amount);
        msg(s, "<green>Reçu <white>" + amount + "× " + a[1]);
    }

    private void edit(CommandSender s, String[] a) {
        CustomItemDef d = need(s, a, 1); if (d == null) return;
        if (a.length < 3) {
            // Sans champ → ouvre l'assistant GUI (joueur) ; sinon rappelle la syntaxe CLI.
            Player p = player(s);
            if (p != null) { com.mooncore.modules.customitem.editor.ItemEditorMenu.open(module, module.chatInput(), p, d.id()); return; }
            msg(s, "<red>/moon item edit <id> <material|type|tool|glow|unbreakable|lore> ...");
            return;
        }
        String field = a[2].toLowerCase(Locale.ROOT);
        switch (field) {
            case "material" -> {
                Material m = Material.matchMaterial(arg(a, 3, "").toUpperCase(Locale.ROOT));
                if (m == null) { msg(s, "<red>Matériau inconnu."); return; }
                d.setMaterial(m); msg(s, "<green>Matériau = " + m.name());
            }
            case "type" -> {
                ItemType t = ItemType.fromId(arg(a, 3, ""));
                if (t == null) { msg(s, "<red>Types : " + types()); return; }
                d.setType(t); msg(s, "<green>Type = " + t.id());
            }
            case "tool" -> {
                ToolKind kind = ToolKind.fromId(arg(a, 3, ""));
                ToolTier tier = ToolTier.fromId(arg(a, 4, "iron"));
                d.setTool(kind, kind == ToolKind.NONE ? ToolTier.HAND : tier);
                msg(s, "<green>Outil = " + (kind == ToolKind.NONE ? "aucun" : kind.label() + " " + d.toolTier().label())
                        + " <gray>(" + d.material().name() + ")");
            }
            case "glow" -> { d.setGlowing(Boolean.parseBoolean(arg(a, 3, "true"))); msg(s, "<green>Glow = " + d.glowing()); }
            case "unbreakable" -> { d.setUnbreakable(Boolean.parseBoolean(arg(a, 3, "true"))); msg(s, "<green>Incassable = " + d.unbreakable()); }
            case "lore" -> {
                String op = arg(a, 3, "").toLowerCase(Locale.ROOT);
                switch (op) {
                    case "add" -> { d.lore().add(join(a, 4)); msg(s, "<green>Ligne de lore ajoutée."); }
                    case "clear" -> { d.lore().clear(); msg(s, "<green>Lore vidé."); }
                    default -> { msg(s, "<red>/moon item edit " + d.id() + " lore <add|clear> [texte]"); return; }
                }
            }
            default -> { msg(s, "<red>Champ inconnu : " + field); return; }
        }
        module.put(d);
    }

    private void stat(CommandSender s, String[] a) {
        if (a.length < 4) { msg(s, "<red>/moon item stat <add|set|remove> <id> <stat> [valeur]"); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        String key = a[3].toLowerCase(Locale.ROOT);
        switch (op) {
            case "remove" -> { d.removeStat(key); msg(s, "<green>Stat retirée : " + key); }
            case "add" -> {
                double cur = d.stats().getOrDefault(key, 0.0);
                d.setStat(key, cur + Double.parseDouble(arg(a, 4, "0")));
                msg(s, "<green>" + ItemStats.label(key) + " = " + d.stats().get(key));
            }
            case "set" -> {
                d.setStat(key, Double.parseDouble(arg(a, 4, "0")));
                msg(s, "<green>" + ItemStats.label(key) + " = " + d.stats().get(key));
            }
            default -> { msg(s, "<red>Opérations : add, set, remove"); return; }
        }
        module.put(d);
    }

    private void ability(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon item ability <add|remove|list> <id> [capacité] [niveau]"); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        switch (op) {
            case "list" -> {
                msg(s, "<gray>Capacités dispo :");
                for (Ability ab : module.abilities().all()) {
                    msg(s, " <dark_gray>▸ " + (ab.isActive() ? "<gold>" : "<aqua>") + ab.id()
                            + " <gray>" + ab.description());
                }
            }
            case "add" -> {
                String abId = arg(a, 3, "");
                if (!module.abilities().exists(abId)) { msg(s, "<red>Capacité inconnue : " + abId); return; }
                int lvl = a.length >= 5 ? Integer.parseInt(a[4]) : 1;
                d.addAbility(abId, lvl);
                module.put(d);
                msg(s, "<green>Capacité ajoutée : " + abId + " " + lvl);
            }
            case "remove" -> {
                if (d.removeAbility(arg(a, 3, ""))) { module.put(d); msg(s, "<green>Capacité retirée."); }
                else msg(s, "<red>L'objet n'a pas cette capacité.");
            }
            default -> msg(s, "<red>Opérations : add, remove, list");
        }
    }

    private void rarity(CommandSender s, String[] a) {
        if (a.length < 4 || !a[1].equalsIgnoreCase("set")) { msg(s, "<red>/moon item rarity set <id> <rareté>"); return; }
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        Rarity r = Rarity.fromId(a[3]);
        if (r == null) { msg(s, "<red>Raretés : " + rarities()); return; }
        d.setRarity(r); module.put(d);
        msg(s, "<green>Rareté = " + module.color(r) + module.label(r));
    }

    private void model(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon item model <set|preview> <id> [cmd] [modelKey]"); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        if (op.equals("set")) {
            if (a.length < 4) { msg(s, "<red>/moon item model set <id> <cmd> [modelKey]"); return; }
            d.setCustomModelData(Integer.parseInt(a[3]));
            if (a.length >= 5) d.setModelKey(a[4].toLowerCase(Locale.ROOT));
            module.put(d);
            msg(s, "<green>Modèle : cmd=" + d.customModelData() + " key=" + d.modelKey()
                    + " <gray>(générer le pack : /moon item pack generate)");
        } else if (op.equals("preview")) {
            Player p = player(s); if (p == null) return;
            module.give(p, d.id(), 1);
            msg(s, "<green>Aperçu donné. <gray>Sans resource pack, le rendu reste vanilla (lore intact).");
        } else msg(s, "<red>Opérations : set, preview");
    }

    private void recipe(CommandSender s, String[] a) {
        // /moon item recipe set <id> <r1> <r2> <r3> X=MAT ...   |  recipe clear <id>
        if (a.length < 3) { msg(s, "<red>/moon item recipe <set|clear> <id> [r1 r2 r3 X=MAT ...]"); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        if (op.equals("clear")) {
            d.setRecipe(null); module.put(d);
            module.recipeManager().unregisterAll(); module.recipeManager().registerAll();
            msg(s, "<green>Recette supprimée."); return;
        }
        if (!op.equals("set") || a.length < 6) {
            msg(s, "<red>/moon item recipe set <id> \"r1\" \"r2\" \"r3\" X=MATERIAL ...");
            msg(s, "<gray>Ex : recipe set epee \" D\" \" D \" \" S \" D=DIAMOND S=STICK");
            return;
        }
        CustomItemDef.Recipe r = new CustomItemDef.Recipe();
        r.shaped = true;
        r.shape = new ArrayList<>(List.of(pad(a[3]), pad(a[4]), pad(a[5])));
        for (int i = 6; i < a.length; i++) {
            String[] kv = a[i].split("=", 2);
            if (kv.length != 2 || kv[0].isEmpty()) continue;
            CustomItemDef.RecipeIngredient ingredient = CustomItemDef.RecipeIngredient.parse(kv[1]);
            if (ingredient == null) { msg(s, "<red>Ingredient inconnu : " + kv[1]); return; }
            r.ingredients.put(kv[0].charAt(0), ingredient);
        }
        d.setRecipe(r); module.put(d);
        module.recipeManager().unregisterAll(); module.recipeManager().registerAll();
        msg(s, "<green>Recette enregistrée pour " + d.id());
    }

    private void drop(CommandSender s, String[] a) {
        // drop add <id> <source> <chance> [min] [max] | drop remove <id> <index> | drop list <id>
        if (a.length < 3) { msg(s, "<red>/moon item drop <add|remove|list> <id> ..."); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        switch (op) {
            case "add" -> {
                if (a.length < 5) { msg(s, "<red>drop add <id> <source> <chance> [min] [max] (source: boss:<id>|mob:<TYPE>|boss:*)"); return; }
                double chance = Double.parseDouble(a[4]);
                int min = a.length >= 6 ? Integer.parseInt(a[5]) : 1;
                int max = a.length >= 7 ? Integer.parseInt(a[6]) : min;
                d.drops().add(new CustomItemDef.DropRule(a[3], chance, min, max));
                module.put(d);
                msg(s, "<green>Drop ajouté : " + a[3] + " (" + chance + ")");
            }
            case "remove" -> {
                int idx = Integer.parseInt(arg(a, 3, "-1"));
                if (idx < 0 || idx >= d.drops().size()) { msg(s, "<red>Index hors limites."); return; }
                d.drops().remove(idx); module.put(d);
                msg(s, "<green>Drop retiré.");
            }
            case "list" -> {
                if (d.drops().isEmpty()) { msg(s, "<gray>Aucun drop."); return; }
                for (int i = 0; i < d.drops().size(); i++) {
                    var r = d.drops().get(i);
                    msg(s, " <dark_gray>[" + i + "] <white>" + r.source() + " <gray>" + r.chance() + " " + r.min() + "-" + r.max());
                }
            }
            default -> msg(s, "<red>Opérations : add, remove, list");
        }
    }

    private void reward(CommandSender s, String[] a) {
        // reward add <id> <eventId> <chance> | reward remove <id> <eventId>
        if (a.length < 4) { msg(s, "<red>/moon item reward <add|remove> <id> <eventId> [chance]"); return; }
        String op = a[1].toLowerCase(Locale.ROOT);
        CustomItemDef d = module.rawDef(a[2]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        String source = "event:" + a[3].toLowerCase(Locale.ROOT);
        if (op.equals("add")) {
            double chance = a.length >= 5 ? Double.parseDouble(a[4]) : 1.0;
            d.drops().add(new CustomItemDef.DropRule(source, chance, 1, 1));
            module.put(d);
            msg(s, "<green>Récompense d'événement associée : " + source);
        } else if (op.equals("remove")) {
            d.drops().removeIf(r -> r.source().equalsIgnoreCase(source));
            module.put(d);
            msg(s, "<green>Récompense retirée : " + source);
        } else msg(s, "<red>Opérations : add, remove");
    }

    private void pack(CommandSender s, String[] a) {
        String op = a.length >= 2 ? a[1].toLowerCase(Locale.ROOT) : "";
        var rp = module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class).orElse(null);
        switch (op) {
            case "rebuild" -> {
                if (rp == null) { msg(s, "<red>Module resource-pack inactif."); return; }
                rp.rebuild();
                msg(s, "<green>Pack reconstruit. URL : <white>" + rp.url());
            }
            case "resend" -> {
                if (rp == null) { msg(s, "<red>Module resource-pack inactif."); return; }
                rp.resendAll();
                msg(s, "<green>Pack forcé renvoyé à tous les joueurs Java.");
            }
            case "url" -> {
                if (rp == null) { msg(s, "<red>Module resource-pack inactif."); return; }
                msg(s, "<gray>URL du pack : <white>" + rp.url());
                msg(s, "<gray>Teste-la dans un navigateur ; si elle ne charge pas, règle <white>host/port<gray> dans resource-pack.yml.");
            }
            case "generate" -> {
                File out = new File(module.mc().getDataFolder(), "resourcepack");
                File texSrc = new File(module.mc().getDataFolder(), "items-textures");
                msg(s, "<gray>Génération du pack statique…");
                ResourcePackBuilder.Result r = new ResourcePackBuilder(module.mc().logger())
                        .build(module.rawDefs(), out, texSrc.isDirectory() ? texSrc : null);
                msg(s, "<green>Pack généré : <white>" + r.models() + " modèle(s), " + r.copied() + " texture(s).");
                for (String w : r.warnings()) msg(s, " <yellow>⚠ " + w);
            }
            default -> msg(s, "<red>/moon item pack <rebuild|resend|url|generate>");
        }
    }

    // ---------------- helpers ----------------

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon item</gradient> <gray>— objets custom");
        String[] lines = {
                "menu <gray>(GUI objets) <dark_gray>| <gray>paint <id> [base] <gray>(dessiner ; base = item/bloc/vanilla)",
                "importvanilla <gray>(importe les textures vanilla depuis le .jar client → import/)",
                "list / info <id>",
                "create <id> [mat] / clone <src> <new> / delete <id>",
                "rename <id> <nom> / give <joueur> <id> [n] / get <id> [n] / edit <id> ...",
                "stat <add|set|remove> <id> <stat> [val] / ability <add|remove|list> <id> [cap] [lvl]",
                "rarity set <id> <rareté> / model <set|preview> <id> ... / recipe <set|clear> <id> ...",
                "drop <add|remove|list> <id> ... / reward <add|remove> <id> <eventId> [chance]",
                "pack generate / import / reload"
        };
        for (String l : lines) msg(s, " <dark_gray>▸ <gray>" + l);
    }

    private CustomItemDef need(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) { msg(s, "<red>Id manquant."); return null; }
        CustomItemDef d = module.rawDef(a[idx]);
        if (d == null) msg(s, "<red>Id inconnu : " + a[idx]);
        return d;
    }

    private static Player player(CommandSender s) {
        return s instanceof Player p ? p : null;
    }

    private static String arg(String[] a, int i, String def) { return i < a.length ? a[i] : def; }

    private static String join(String[] a, int from) {
        return String.join(" ", Arrays.copyOfRange(a, Math.min(from, a.length), a.length));
    }

    private static String pad(String row) {
        String r = row;
        if (r.length() > 3) r = r.substring(0, 3);
        while (r.length() < 3) r += " ";
        return r;
    }

    private static String types() {
        return Arrays.stream(ItemType.values()).map(ItemType::id).collect(Collectors.joining(", "));
    }

    private static String rarities() {
        return Arrays.stream(Rarity.values()).map(Rarity::id).collect(Collectors.joining(", "));
    }

    private static void msg(CommandSender s, String mm) {
        s.sendMessage(Text.mm(mm));
    }

    // ---------------- tab ----------------

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("menu", "paint", "importvanilla", "list", "info", "create", "clone", "delete", "rename", "give", "get",
                    "edit", "stat", "ability", "rarity", "model", "recipe", "drop", "reward",
                    "export", "import", "pack", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "info", "delete", "remove", "rename", "edit", "clone", "get", "rarity", "model", "recipe", "drop", "reward", "paint" ->
                        filter(new ArrayList<>(module.ids()), a[1]);
                case "give" -> filter(online(), a[1]);
                case "stat", "ability" -> filter(List.of("add", "set", "remove", "list"), a[1]);
                case "pack" -> filter(List.of("rebuild", "resend", "url", "generate"), a[1]);
                default -> List.of();
            };
        }
        if (a.length == 3) {
            return switch (sub) {
                case "give", "get" -> filter(new ArrayList<>(module.ids()), a[2]);
                case "stat", "ability", "drop", "reward" -> filter(new ArrayList<>(module.ids()), a[2]);
                case "rarity" -> filter(List.of("set"), a[2]);
                case "model" -> filter(List.of("set", "preview"), a[2]);
                case "recipe" -> filter(List.of("set", "clear"), a[2]);
                case "create" -> filter(materials(), a[2]);
                case "edit" -> filter(List.of("material", "type", "tool", "glow", "unbreakable", "lore"), a[2]);
                default -> List.of();
            };
        }
        if (a.length == 4) {
            return switch (sub) {
                case "rarity" -> filter(Arrays.stream(Rarity.values()).map(Rarity::id).toList(), a[3]);
                case "stat" -> filter(new ArrayList<>(ItemStats.known().keySet()), a[3]);
                case "ability" -> filter(module.abilities().all().stream().map(Ability::id).toList(), a[3]);
                case "model" -> filter(new ArrayList<>(module.ids()), a[3]);
                case "edit" -> editValueComplete(a[2], a[3]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> editValueComplete(String field, String prefix) {
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "material" -> filter(materials(), prefix);
            case "type" -> filter(Arrays.stream(ItemType.values()).map(ItemType::id).toList(), prefix);
            case "glow", "unbreakable" -> filter(List.of("true", "false"), prefix);
            case "lore" -> filter(List.of("add", "clear"), prefix);
            default -> List.of();
        };
    }

    private static List<String> materials() {
        return Arrays.stream(Material.values())
                .filter(m -> m.isItem() && !m.isLegacy())
                .map(m -> m.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private static List<String> online() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
