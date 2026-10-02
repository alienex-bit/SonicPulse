package org.steve.sonicpulse.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.platform.InputConstants;
import org.steve.sonicpulse.client.engine.SonicPulseEngine;
import org.steve.sonicpulse.network.PlayUrlPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.steve.sonicpulse.client.screen.ConfigScreen;
import org.steve.sonicpulse.client.gui.SonicPulseHud;
import net.minecraft.resources.Identifier;

public class SonicPulseClient implements ClientModInitializer {
    private static SonicPulseEngine engine;
    private static KeyMapping configKeyBinding;
    public static final Logger LOGGER = LogManager.getLogger(SonicPulseClient.class);

    @Override
    public void onInitializeClient() {
        engine = new SonicPulseEngine();
        setupAssetDirectory();

        SonicPulseHud hud = new SonicPulseHud();
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("sonicpulse", "hud"),
                (graphics, deltaTracker) -> hud.render(graphics, false, 0, 0)
        );

        configKeyBinding = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.sonicpulse.config", InputConstants.KEY_P, KeyMapping.Category.MISC)
        );

        ClientPlayNetworking.registerGlobalReceiver(PlayUrlPayload.TYPE, (payload, context) -> {
            String url = payload.url();
            Minecraft.getInstance().execute(() -> engine.playTrack(url, url, "Network"));
        });

        // Disconnect Safety Hook: Kill audio engine when leaving world/server
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (engine != null)
                engine.stop();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (engine != null)
                engine.tick();
            if (configKeyBinding.consumeClick() && client.gui.screen() == null)
                client.gui.setScreen(new ConfigScreen());
        });

        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(
                        ClientCommands.literal("sonicpulse").then(
                                ClientCommands.literal("test").then(
                                        ClientCommands.argument("url", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                    engine.playTrack(
                                                            StringArgumentType.getString(context, "url"),
                                                            null,
                                                            "Command"
                                                    );
                                                    return 1;
                                                })
                                )
                        )
                )
        );
    }

    public static SonicPulseEngine getEngine() {
        return engine;
    }

    public static KeyMapping getConfigKeyBinding() {
        return configKeyBinding;
    }

    private void setupAssetDirectory() {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("sonicpulse").resolve("music");
            if (!Files.exists(dir))
                Files.createDirectories(dir);
        } catch (Exception e) {
            LOGGER.error("Failed to create asset directory", e);
        }
    }
}