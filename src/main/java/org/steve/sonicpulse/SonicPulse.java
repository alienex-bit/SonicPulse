package org.steve.sonicpulse;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.steve.sonicpulse.network.PlayUrlPayload;

public class SonicPulse implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(PlayUrlPayload.TYPE, PlayUrlPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayUrlPayload.TYPE, PlayUrlPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PlayUrlPayload.TYPE, (payload, context) -> {
            String url = payload.url();
            context.server().execute(() -> {
                context.server().getPlayerList().getPlayers().forEach(player -> {
                    if (ServerPlayNetworking.canSend(player, PlayUrlPayload.TYPE)) {
                        ServerPlayNetworking.send(player, new PlayUrlPayload(url));
                    }
                });
            });
        });
    }
}
