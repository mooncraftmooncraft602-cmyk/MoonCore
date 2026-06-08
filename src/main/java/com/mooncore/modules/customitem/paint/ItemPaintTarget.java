package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.customitem.CustomItemDef;
import com.mooncore.modules.customitem.CustomItemManagerModule;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;

import java.io.File;

/** Cible d'édition = un objet custom (texture dans items-textures/, custom_model_data). */
public final class ItemPaintTarget implements PaintTarget {

    private final CustomItemManagerModule module;
    private final String id;

    public ItemPaintTarget(CustomItemManagerModule module, String id) {
        this.module = module;
        this.id = id;
    }

    @Override public String id() { return id; }

    @Override public File textureFile() { return new File(module.texturesFolder(), id + ".png"); }

    @Override
    public void onSaved(MoonCore plugin, Player editor) {
        CustomItemDef def = module.rawDef(id);
        if (def == null) return;
        def.setModelKey(id);
        if (def.customModelData() <= 0) def.setCustomModelData(module.nextCustomModelData());
        module.put(def);
        plugin.services().get(ResourcePackService.class).ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
        if (editor != null) editor.sendMessage(Text.mm("<green>✔ Texture appliquée à l'objet <white>" + id
                + "<green> (cmd " + def.customModelData() + "). Pack mis à jour."));
    }
}
