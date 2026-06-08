package com.mooncore.modules.customblock.command;

import com.mooncore.MoonCore;
import com.mooncore.command.SubCommand;
import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.modules.customitem.ToolKind;
import com.mooncore.modules.customitem.ToolTier;
import com.mooncore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Commande admin {@code /moon block ...} : blocs/minerais custom. */
public final class CustomBlockSubCommand implements SubCommand {

    private final CustomBlockManagerModule module;

    public CustomBlockSubCommand(CustomBlockManagerModule module) { this.module = module; }

    @Override public String name() { return "block"; }
    @Override public List<String> aliases() { return List.of("blocks", "cb"); }
    @Override public String permission() { return "mooncore.admin.blocks"; }
    @Override public String description() { return "Gestion des blocs custom"; }
    @Override public String category() { return "admin"; }

    @Override
    public void execute(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 0) { help(s); return; }
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "create" -> create(s, a);
                case "paint", "draw" -> paint(s, a);
                case "importvanilla" -> importVanilla(s);
                case "delete", "remove" -> { if (need(s, a) != null && module.removeDef(a[1])) msg(s, "<green>Supprimé : " + a[1]); else msg(s, "<red>Id inconnu."); }
                case "list" -> list(s);
                case "info" -> info(s, a);
                case "give" -> give(s, a);
                case "get" -> get(s, a);
                case "drop" -> setDrop(s, a);
                case "tool" -> setTool(s, a);
                case "hardness", "durability", "resistance" -> setHardness(s, a);
                case "face" -> setFace(s, a);
                case "worldgen" -> worldgen(s, a);
                case "pack" -> pack(s);
                case "reload" -> { module.reloadModule(); msg(s, "<green>Blocs rechargés."); }
                default -> help(s);
            }
        } catch (NumberFormatException e) {
            msg(s, "<red>Nombre invalide.");
        }
    }

    private void paint(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (a.length < 2 || module.rawDef(a[1]) == null) { msg(s, "<red>/moon block paint <id> [taille 16|32] [importDepuisId]"); return; }
        var ci = module.mc().moduleManager().get(com.mooncore.modules.customitem.CustomItemManagerModule.class);
        if (ci == null || ci.paintManager() == null) { msg(s, "<red>Éditeur indisponible (module custom-item inactif)."); return; }
        int size = 16; java.io.File source = null; // 16×16 par défaut (le plus facile)
        for (int i = 2; i < a.length; i++) {
            if (a[i].equals("16") || a[i].equals("32") || a[i].equals("64")) size = Integer.parseInt(a[i]);
            else {
                source = com.mooncore.modules.customitem.paint.PaintManager.resolveTexture(module.mc(), a[i]);
                if (source != null) msg(s, "<gray>Import de la texture de <white>" + a[i] + "<gray> comme base.");
            }
        }
        ci.paintManager().open(p, new com.mooncore.modules.customitem.paint.BlockPaintTarget(module, a[1].toLowerCase(Locale.ROOT)), size, source);
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/moon block create <id>"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (module.rawDef(id) != null) { msg(s, "<red>Cet id existe déjà."); return; }
        CustomBlockDef d = new CustomBlockDef(id);
        module.put(d);
        msg(s, "<green>Bloc <white>" + id + "<green> créé (état " + d.stateIndex() + "). "
                + "Texture : <white>blocks-textures/" + id + ".png<green> ou via l'IA, puis <white>/moon block pack");
    }

    private void list(CommandSender s) {
        var defs = module.rawDefs().values();
        if (defs.isEmpty()) { msg(s, "<gray>Aucun bloc custom. <white>/moon block create <id>"); return; }
        msg(s, "<gradient:#8a2be2:#c77dff>Blocs custom</gradient> <dark_gray>(" + defs.size() + ")");
        for (CustomBlockDef d : defs) {
            msg(s, " <dark_gray>▸ <white>" + d.id() + " <gray>(état " + d.stateIndex()
                    + (d.generate() ? ", <green>minerai" : "") + "<gray>)");
        }
    }

    private void info(CommandSender s, String[] a) {
        CustomBlockDef d = need(s, a); if (d == null) return;
        msg(s, "<gradient:#8a2be2:#c77dff>" + d.id() + "</gradient>");
        msg(s, " <gray>Nom : <reset>" + d.displayName());
        msg(s, " <gray>État note-block : <white>" + d.stateIndex() + " <gray>Modèle : <white>" + d.modelKey());
        msg(s, " <gray>Drop : <white>" + (d.dropItemId() == null ? "lui-meme" : d.dropItemId())
                + " <gray>XP : <white>" + d.dropXp());
        msg(s, " <gray>Outil : <white>" + (d.requiredTool() == ToolKind.NONE ? "aucun" : d.requiredTool().label() + " " + d.minToolTier().label() + "+")
                + " <gray>Durabilite : <white>" + d.breakDurability()
                + " <gray>Resistance explosion : <white>" + d.blastResistance());
        msg(s, " <gray>Worldgen : <white>" + (d.generate()
                ? "oui (remplace " + d.replace().name() + ", Y " + d.minY() + "→" + d.maxY()
                  + ", " + d.veinsPerChunk() + " veine(s)/chunk, taille " + d.veinSize() + ")"
                : "non"));
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon block give <joueur> <id> [n]"); return; }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) { msg(s, "<red>Joueur hors-ligne."); return; }
        if (module.rawDef(a[2]) == null) { msg(s, "<red>Id inconnu."); return; }
        int n = a.length >= 4 ? Integer.parseInt(a[3]) : 1;
        module.give(t, a[2], n);
        msg(s, "<green>Donné " + n + "× " + a[2] + " à " + t.getName());
    }

    private void get(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { msg(s, "<red>Réservé aux joueurs."); return; }
        if (a.length < 2 || module.rawDef(a[1]) == null) { msg(s, "<red>/moon block get <id> [n]"); return; }
        int n = a.length >= 3 ? Integer.parseInt(a[2]) : 1;
        module.give(p, a[1], n);
        msg(s, "<green>Reçu " + n + "× " + a[1]);
    }

    private void setDrop(CommandSender s, String[] a) {
        // /moon block drop <id> <itemId|self> [xp]
        if (a.length < 3) { msg(s, "<red>/moon block drop <id> <itemId|self> [xp]"); return; }
        CustomBlockDef d = module.rawDef(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        d.setDropItemId(a[2].equalsIgnoreCase("self") ? null : a[2].toLowerCase(Locale.ROOT));
        if (a.length >= 4) d.setDropXp(Integer.parseInt(a[3]));
        module.put(d);
        msg(s, "<green>Drop de " + d.id() + " = " + (d.dropItemId() == null ? "lui-même" : d.dropItemId()) + " (xp " + d.dropXp() + ")");
    }

    private void setTool(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon block tool <id> <none|pickaxe|axe|shovel|hoe> [wood|stone|iron|gold|diamond|netherite]"); return; }
        CustomBlockDef d = module.rawDef(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        ToolKind tool = ToolKind.fromId(a[2]);
        d.setRequiredTool(tool);
        if (a.length >= 4) d.setMinToolTier(ToolTier.fromId(a[3]));
        module.put(d);
        msg(s, "<green>Outil requis de " + d.id() + " = <white>"
                + (d.requiredTool() == ToolKind.NONE ? "aucun" : d.requiredTool().label() + " " + d.minToolTier().label() + "+"));
    }

    private void setHardness(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/moon block hardness <id> <durabilite> [resistanceExplosion]"); return; }
        CustomBlockDef d = module.rawDef(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        d.setBreakDurability(Integer.parseInt(a[2]));
        if (a.length >= 4) d.setBlastResistance(Double.parseDouble(a[3]));
        module.put(d);
        msg(s, "<green>Durete de " + d.id() + " = <white>" + d.breakDurability()
                + " <gray>/ resistance explosion <white>" + d.blastResistance());
    }

    private void importVanilla(CommandSender s) {
        java.io.File data = module.mc().getDataFolder();
        java.io.File src = com.mooncore.modules.customitem.VanillaTextureImporter.findSource(data);
        java.io.File vanillaDir = com.mooncore.modules.customitem.VanillaTextureImporter.vanillaFolder(data);
        java.io.File[] already = vanillaDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (src == null) {
            if (already != null && already.length > 0) {
                msg(s, "<green>✔ " + already.length + " textures vanilla déjà disponibles — pas besoin de réimporter.");
                msg(s, "<gray>Ex : <white>/moon block create monbloc</white> → <white>/moon block paint monbloc deepslate_diamond_ore</white>");
                return;
            }
            msg(s, "<red>Aucun fichier source.</red> <gray>Dépose le <white>.jar du client Minecraft</white> "
                    + "(ou un resource pack vanilla .zip) dans <white>plugins/MoonCore/import/</white>, puis relance.");
            return;
        }
        msg(s, "<gray>Extraction des textures vanilla depuis <white>" + src.getName() + "</white>…");
        var r = com.mooncore.modules.customitem.VanillaTextureImporter.extract(src, data);
        if (r.error() != null) msg(s, "<yellow>⚠ " + r.error() + " (" + r.extracted() + " extraites)");
        else {
            msg(s, "<green>✔ " + r.extracted() + " textures vanilla importées.");
            msg(s, "<gray>Ex : <white>/moon block create minerai_lunaire</white> puis "
                    + "<white>/moon block paint minerai_lunaire deepslate_diamond_ore");
        }
    }

    private void setFace(CommandSender s, String[] a) {
        // /moon block face <id> <top|side|bottom|reset> <cléTexture>
        if (a.length < 3) { msg(s, "<red>/moon block face <id> <top|side|bottom|reset> [cléTexture]"); return; }
        CustomBlockDef d = module.rawDef(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        String face = a[2].toLowerCase(Locale.ROOT);
        if (face.equals("reset")) {
            d.setTextureTop(null); d.setTextureSide(null); d.setTextureBottom(null);
            module.put(d);
            msg(s, "<green>Faces réinitialisées (texture unique).");
            return;
        }
        if (a.length < 4) { msg(s, "<red>Précise la clé de texture (PNG dans blocks-textures/)."); return; }
        String key = a[3].toLowerCase(Locale.ROOT);
        switch (face) {
            case "top", "up" -> d.setTextureTop(key);
            case "side" -> d.setTextureSide(key);
            case "bottom", "down" -> d.setTextureBottom(key);
            default -> { msg(s, "<red>Face : top, side, bottom ou reset."); return; }
        }
        module.put(d);
        msg(s, "<green>Face " + face + " de " + d.id() + " = <white>" + key
                + "<gray>. Place la PNG dans blocks-textures/ puis <white>/moon block pack");
    }

    private void worldgen(CommandSender s, String[] a) {
        // /moon block worldgen <id> <on|off> [replace] [minY] [maxY] [veins] [veinSize]
        if (a.length < 3) { msg(s, "<red>/moon block worldgen <id> <on|off> [replace] [minY] [maxY] [veins] [veinSize]"); return; }
        CustomBlockDef d = module.rawDef(a[1]);
        if (d == null) { msg(s, "<red>Id inconnu."); return; }
        d.setGenerate(a[2].equalsIgnoreCase("on") || a[2].equalsIgnoreCase("true"));
        if (a.length >= 4) {
            Material m = Material.matchMaterial(a[3].toUpperCase(Locale.ROOT));
            if (m != null) d.setReplace(m); else { msg(s, "<red>Matériau inconnu : " + a[3]); return; }
        }
        if (a.length >= 6) d.setYRange(Integer.parseInt(a[4]), Integer.parseInt(a[5]));
        if (a.length >= 7) d.setVeinsPerChunk(Integer.parseInt(a[6]));
        if (a.length >= 8) d.setVeinSize(Integer.parseInt(a[7]));
        module.put(d);
        msg(s, "<green>Worldgen de " + d.id() + " = " + d.generate()
                + " (remplace " + d.replace().name() + ", Y " + d.minY() + "→" + d.maxY()
                + ", " + d.veinsPerChunk() + " veine(s)/chunk, taille " + d.veinSize() + ").");
        msg(s, "<gray>Ne s'applique qu'aux <white>nouveaux chunks</white> (explore pour les voir).");
    }

    private void pack(CommandSender s) {
        var rp = module.mc().services().get(com.mooncore.api.resourcepack.ResourcePackService.class).orElse(null);
        if (rp == null) { msg(s, "<red>Module resource-pack inactif."); return; }
        rp.rebuild();
        rp.resendAll();
        msg(s, "<green>Pack reconstruit et renvoyé. URL : <white>" + rp.url());
    }

    private CustomBlockDef need(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>Id manquant."); return null; }
        CustomBlockDef d = module.rawDef(a[1]);
        if (d == null) msg(s, "<red>Id inconnu : " + a[1]);
        return d;
    }

    private void help(CommandSender s) {
        msg(s, "<gradient:#8a2be2:#c77dff>/moon block</gradient> <gray>— blocs/minerais custom");
        String[] l = {
                "create <id> / paint <id> [base] (dessiner ; base = item/bloc/vanilla) / delete <id> / list / info <id>",
                "importvanilla (importe les textures vanilla depuis le .jar client → import/)",
                "give <joueur> <id> [n] / get <id> [n]",
                "drop <id> <itemId|self> [xp] (ce que le bloc lâche)",
                "face <id> <top|side|bottom|reset> <cléTexture> (faces distinctes)",
                "worldgen <id> <on|off> [replace] [minY] [maxY] [veins] [veinSize]",
                "pack (reconstruit + renvoie le resource pack)"
        };
        for (String x : l) msg(s, " <dark_gray>▸ <gray>" + x);
    }

    private static void msg(CommandSender s, String mm) { s.sendMessage(Text.mm(mm)); }

    @Override
    public List<String> tabComplete(MoonCore plugin, CommandSender s, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "paint", "importvanilla", "delete", "list", "info", "give", "get", "drop", "tool", "hardness", "durability", "resistance", "face", "worldgen", "pack", "reload"), a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 2) {
            return switch (sub) {
                case "delete", "info", "get", "drop", "tool", "hardness", "durability", "resistance", "worldgen", "paint", "face" -> filter(new ArrayList<>(module.ids()), a[1]);
                case "give" -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), a[1]);
                default -> List.of();
            };
        }
        if (a.length == 3) {
            return switch (sub) {
                case "give" -> filter(new ArrayList<>(module.ids()), a[2]);
                case "worldgen" -> filter(List.of("on", "off"), a[2]);
                case "face" -> filter(List.of("top", "side", "bottom", "reset"), a[2]);
                case "tool" -> filter(List.of("none", "pickaxe", "axe", "shovel", "hoe"), a[2]);
                default -> List.of();
            };
        }
        if (a.length == 4 && sub.equals("tool")) {
            return filter(List.of("wood", "stone", "iron", "gold", "diamond", "netherite"), a[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
