package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.customblock.CustomBlockDef;
import com.mooncore.modules.customblock.CustomBlockManagerModule;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;

import java.io.File;

/** Cible d'édition = un bloc custom (texture dans blocks-textures/). */
public final class BlockPaintTarget implements PaintTarget {

    private final CustomBlockManagerModule module;
    private final String id;

    public BlockPaintTarget(CustomBlockManagerModule module, String id) {
        this.module = module;
        this.id = id;
    }

    @Override public String id() { return id; }

    @Override public File textureFile() { return new File(module.store().texturesFolder(), id + ".png"); }

    @Override
    public void onSaved(MoonCore plugin, Player editor) {
        CustomBlockDef def = module.rawDef(id);
        if (def == null) return;
        def.setModelKey(id);
        module.put(def);
        plugin.services().get(ResourcePackService.class).ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
        if (editor != null) editor.sendMessage(Text.mm("<green>✔ Texture appliquée au bloc <white>" + id
                + "<green>. Pack mis à jour."));
    }
}
