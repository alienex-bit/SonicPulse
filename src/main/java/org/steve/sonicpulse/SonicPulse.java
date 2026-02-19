package org.steve.sonicpulse;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.steve.sonicpulse.network.PlayUrlPayload;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class SonicPulse implements ModInitializer {

    @Override
    public void onInitialize() {

        PayloadTypeRegistry.playC2S().register(PlayUrlPayload.ID, PlayUrlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayUrlPayload.ID, PlayUrlPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PlayUrlPayload.ID, (payload, context) -> {
            String url = payload.url();
            context.player().getServer().execute(() -> {
                // Find jukeboxes near the player and set their URL/playing state
                // This is a simplified approach: broadcast to all clients
                context.player().getServer().getPlayerManager().getPlayerList().forEach(player -> {
                    if (ServerPlayNetworking.canSend(player, PlayUrlPayload.ID)) {
                        ServerPlayNetworking.send(player, new PlayUrlPayload(url));
                    }
                });
            });
        });
    }
}
