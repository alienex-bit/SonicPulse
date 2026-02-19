package org.steve.sonicpulse.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PlayUrlPayload(String url) implements CustomPayload {
    public static final Id<PlayUrlPayload> ID = new Id<>(Identifier.of("sonicpulse", "play_url"));
    public static final PacketCodec<PacketByteBuf, PlayUrlPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, PlayUrlPayload::url,
            PlayUrlPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
