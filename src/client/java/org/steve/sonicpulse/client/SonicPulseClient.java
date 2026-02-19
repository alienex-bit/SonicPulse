package org.steve.sonicpulse.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.steve.sonicpulse.client.engine.SonicPulseEngine;
import org.steve.sonicpulse.network.PlayUrlPayload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.steve.sonicpulse.client.screen.ConfigScreen;

public class SonicPulseClient implements ClientModInitializer {
    private static SonicPulseEngine engine;
    private static KeyBinding configKeyBinding;
    public static final Logger LOGGER = LogManager.getLogger(SonicPulseClient.class);

    @Override
    public void onInitializeClient() {
        engine = new SonicPulseEngine();
        setupAssetDirectory();

        // Register the proper HUD renderer
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(new org.steve.sonicpulse.client.gui.SonicPulseHud());

        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sonicpulse.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "category.sonicpulse"
        ));

        ClientPlayNetworking.registerGlobalReceiver(PlayUrlPayload.ID, (payload, context) -> {
            String url = payload.url();
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> engine.playTrack(url));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (engine != null) engine.tick();
            if (configKeyBinding.wasPressed() && client.currentScreen == null) {
                client.setScreen(new ConfigScreen());
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("sonicpulse")
                .then(ClientCommandManager.literal("test")
                    .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                        .executes(context -> {
                            String url = StringArgumentType.getString(context, "url");
                            context.getSource().sendFeedback(Text.literal("Attempting to load: " + url));
                            engine.playTrack(url);
                            return 1;
                        })
                    )
                )
            )
        );
    }
    public static SonicPulseEngine getEngine() { return engine; }

    private void setupAssetDirectory() {
        try {
            Path configDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse/music");
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                LOGGER.info("Created SonicPulse asset directory: {}", configDir.toString());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create asset directory", e);
        }
    }
}
