package org.steve.sonicpulse.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlayUrlPayload(String url) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayUrlPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("sonicpulse", "play_url"));
    public static final StreamCodec<ByteBuf, PlayUrlPayload> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(
            PlayUrlPayload::new,
            PlayUrlPayload::url
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
