package com.mooncore.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Écran « Studio » du mod client (placeholder de la Phase 1). Servira de toile d'éditeur
 * 2D natif (peinture à la souris, calques, timeline d'animation) et, plus tard, d'éditeur
 * de modèles 3D — communiquant avec le serveur via {@link CompanionPayload}.
 */
public final class StudioScreen extends Screen {

    public StudioScreen() {
        super(Text.literal("MoonCore Studio"));
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Fermer"), b -> close())
                .dimensions(this.width / 2 - 100, this.height / 2 + 50, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§dMoonCore Companion — Studio"), this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        String state = MoonCoreCompanionClient.connected
                ? "§aConnecté à un serveur MoonCore (capacités: " + MoonCoreCompanionClient.capabilities + ")"
                : "§7Aucun serveur MoonCore détecté sur cette partie";
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(state), this.width / 2, this.height / 2 - 30, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§7Éditeur 2D/3D natif — à venir (Phase 2)."), this.width / 2, this.height / 2, 0x888888);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
