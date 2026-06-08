package com.mooncore.modules.customitem.paint;

import com.mooncore.MoonCore;
import com.mooncore.api.resourcepack.ResourcePackService;
import com.mooncore.modules.boss.BossManagerModule;
import com.mooncore.util.Text;
import org.bukkit.entity.Player;

import java.io.File;

/** Cible d'édition = une texture cosmétique de boss (boss-textures/, portée sur la tête). */
public final class BossPaintTarget implements PaintTarget {

    private final BossManagerModule module;
    private final String id;

    public BossPaintTarget(BossManagerModule module, String id) {
        this.module = module;
        this.id = id;
    }

    @Override public String id() { return id; }

    @Override public File textureFile() { return new File(module.texturesFolder(), id + ".png"); }

    @Override
    public void onSaved(MoonCore plugin, Player editor) {
        if (!module.setTexture(id, id, BossManagerModule.textureModelData(id))) return;
        plugin.services().get(ResourcePackService.class).ifPresent(rp -> { rp.rebuild(); rp.resendAll(); });
        if (editor != null) editor.sendMessage(Text.mm("<green>✔ Texture appliquée au boss <white>" + id
                + "<green>. Pack mis à jour."));
    }
}
