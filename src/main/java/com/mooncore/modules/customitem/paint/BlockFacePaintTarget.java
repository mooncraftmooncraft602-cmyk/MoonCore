package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;

import java.io.File;

/** Cible d'édition = une face précise d'un bloc custom (top/side/bottom). */
public final class BlockFacePaintTarget implements PaintTarget {

    public enum Face { TOP, SIDE, BOTTOM }

    private final CustomBlockManagerModule module;
    private final String id;
    private final String textureKey;
    private final Face face;

    public BlockFacePaintTarget(CustomBlockManagerModule module, String id, String textureKey, Face face) {
        this.module = module;
        this.id = id;
        this.textureKey = textureKey;
        this.face = face;
    }

    @Override public String id() { return textureKey; }

    @Override public File textureFile() { return new File(module.store().texturesFolder(), textureKey + ".png"); }

    @Override
    public void onSaved(MoonCore plugin, Player editor) {
        CustomBlockDef def = module.rawDef(id);
        if (def == null) return;
        switch (face) {
            case TOP -> def.setTextureTop(textureKey);
            case SIDE -> def.setTextureSide(textureKey);
            case BOTTOM -> def.setTextureBottom(textureKey);
        }
        module.put(def);
        plugin.services().get(ResourcePackService.class).ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
        if (editor != null) editor.sendMessage(Text.mm("<green>✔ Texture " + face.name().toLowerCase()
                + " appliquée au bloc <white>" + id + "<green>. Pack mis à jour."));
    }
}
