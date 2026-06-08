package com.mooncore.companion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Point d'entrée client du mod OPTIONNEL MoonCore Companion.
 * <ul>
 *   <li>Enregistre le canal {@code mooncore:companion} (HELLO / WELCOME).</li>
 *   <li>À la connexion, envoie un HELLO ; si le serveur répond WELCOME, on active les outils.</li>
 *   <li>Touche (par défaut <b>M</b>) pour ouvrir le Studio (placeholder pour l'instant).</li>
 * </ul>
 * Si le serveur n'est pas un serveur MoonCore (ou si le joueur n'a pas ce mod), rien ne change.
 */
public final class MoonCoreCompanionClient implements ClientModInitializer {

    private static final byte OP_HELLO = 0x01;
    private static final byte OP_WELCOME = 0x02;
    private static final byte PROTOCOL = 1;

    /** True quand un serveur MoonCore a répondu WELCOME. Capacités = bitmask annoncé. */
    public static volatile boolean connected = false;
    public static volatile int capabilities = 0;

    private static KeyBinding studioKey;
    private int helloTicks = 0; // tente le HELLO pendant ~5 s après le join

    @Override
    public void onInitializeClient() {
        // Types de paquet (les deux sens) — requis pour envoyer/recevoir sur le canal.
        PayloadTypeRegistry.playC2S().register(CompanionPayload.ID, CompanionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CompanionPayload.ID, CompanionPayload.CODEC);

        studioKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mooncore-companion.studio", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M,
                "key.categories.mooncore-companion"));

        // Réception du WELCOME serveur → on connait les capacités activées.
        ClientPlayNetworking.registerGlobalReceiver(CompanionPayload.ID, (payload, context) -> {
            byte[] d = payload.data();
            if (d.length >= 1 && d[0] == OP_WELCOME) {
                connected = true;
                capabilities = d.length >= 3 ? (d[2] & 0xFF) : 0;
                context.client().execute(() -> {
                    if (context.client().player != null) {
                        context.client().player.sendMessage(
                                Text.literal("§dMoonCore Companion §aconnecté §7(appuie sur §fM§7 pour le Studio)."), false);
                    }
                });
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { connected = false; capabilities = 0; helloTicks = 0; });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { connected = false; capabilities = 0; });

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        // Envoi du HELLO (réessais 1×/s pendant 5 s, le temps que le canal soit annoncé).
        if (client.player != null && !connected && helloTicks < 100) {
            if (helloTicks % 20 == 0 && ClientPlayNetworking.canSend(CompanionPayload.ID)) {
                try { ClientPlayNetworking.send(new CompanionPayload(new byte[]{OP_HELLO, PROTOCOL})); }
                catch (Throwable ignored) { /* serveur non-MoonCore : on ignore */ }
            }
            helloTicks++;
        }
        while (studioKey != null && studioKey.wasPressed()) {
            if (client.player != null) client.setScreen(new StudioScreen());
        }
    }
}
