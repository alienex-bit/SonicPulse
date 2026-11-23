package org.steve.sonicpulse;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.steve.sonicpulse.network.PlayUrlPayload;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SonicPulse implements ModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(SonicPulse.class);
    public static final int MAX_NETWORK_URL_LENGTH = 2048;
    private static final long BROADCAST_COOLDOWN_MS = 3000L;
    private final Map<UUID, Long> lastBroadcastByPlayer = new ConcurrentHashMap<>();

    public static boolean isValidNetworkUrl(String rawUrl) {
        if (rawUrl == null) {
            return false;
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NETWORK_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(PlayUrlPayload.TYPE, PlayUrlPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PlayUrlPayload.TYPE, PlayUrlPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PlayUrlPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            if (!sender.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                LOGGER.warn("Rejected PlayUrlPayload from non-operator player {}", sender.getGameProfile().name());
                return;
            }

            String rawUrl = payload.url();
            if (!isValidNetworkUrl(rawUrl)) {
                LOGGER.warn("Rejected invalid network URL from {}", sender.getGameProfile().name());
                return;
            }

            long now = System.currentTimeMillis();
            Long last = lastBroadcastByPlayer.get(sender.getUUID());
            if (last != null && (now - last) < BROADCAST_COOLDOWN_MS) {
                LOGGER.debug("Rate-limited PlayUrlPayload from {}", sender.getGameProfile().name());
                return;
            }
            lastBroadcastByPlayer.put(sender.getUUID(), now);

            String url = rawUrl.trim();
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
