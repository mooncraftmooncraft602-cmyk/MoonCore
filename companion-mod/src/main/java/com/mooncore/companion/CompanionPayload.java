package com.mooncore.companion;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Charge utile réseau brute échangée avec le plugin MoonCore (serveur) sur le canal
 * {@code mooncore:companion}. Le contenu est un tableau d'octets dont le 1er octet est
 * un opcode (0x01 = HELLO client→serveur, 0x02 = WELCOME serveur→client). Format volontairement
 * minimal pour interopérer avec les « plugin messages » Bukkit.
 */
public record CompanionPayload(byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<CompanionPayload> ID =
            new CustomPayload.Id<>(Identifier.of("mooncore", "companion"));

    public static final PacketCodec<PacketByteBuf, CompanionPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBytes(value.data()),
            buf -> {
                byte[] b = new byte[buf.readableBytes()];
                buf.readBytes(b);
                return new CompanionPayload(b);
            });

    @Override
    public CustomPayload.Id<CompanionPayload> getId() {
        return ID;
    }
}
