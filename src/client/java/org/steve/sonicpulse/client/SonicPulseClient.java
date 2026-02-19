package org.steve.sonicpulse.client;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.steve.sonicpulse.client.engine.SonicPulseEngine;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.steve.sonicpulse.network.PlayUrlPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.steve.sonicpulse.client.gui.SonicPulseHud;
import org.steve.sonicpulse.client.screen.ConfigScreen;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// reflection imports for v2 compatibility
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SonicPulseClient implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger();
    private static SonicPulseEngine engine;
    private static KeyBinding configKeyBinding;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing SonicPulse client");
        engine = new SonicPulseEngine();
        setupAssetDirectory();

        // Create a single SonicPulseHud instance and register it for the v1 API
        SonicPulseHud hud = new SonicPulseHud();
        HudRenderCallback.EVENT.register(hud);

        // If Fabric's v2 HudRenderCallback exists at runtime, register a proxy that forwards to the same hud instance.
        try {
            Class<?> v2HudClass = Class.forName("net.fabricmc.fabric.api.client.rendering.v2.HudRenderCallback");
            Field eventField = v2HudClass.getField("EVENT");
            Object eventObj = eventField.get(null);
            Object proxy = Proxy.newProxyInstance(v2HudClass.getClassLoader(), new Class<?>[]{v2HudClass}, (proxyInstance, method, args) -> {
                if ("onHudRender".equals(method.getName())) {
                    // Expected signature: onHudRender(DrawContext, float) in v2; we forward to our existing hud
                    try {
                        // call hud.onHudRender(DrawContext, RenderTickCounter) via reflection, passing null for tickCounter
                        Method m = SonicPulseHud.class.getMethod("onHudRender", net.minecraft.client.gui.DrawContext.class, net.minecraft.client.render.RenderTickCounter.class);
                        m.invoke(hud, args.length > 0 ? args[0] : null, null);
                    } catch (NoSuchMethodException nsme) {
                        // Fallback: try method with (DrawContext, float) on SonicPulseHud (unlikely), or just call any onHudRender
                        try {
                            Method m2 = SonicPulseHud.class.getMethod("onHudRender", net.minecraft.client.gui.DrawContext.class, float.class);
                            m2.invoke(hud, args.length > 0 ? args[0] : null, args.length > 1 ? args[1] : 0f);
                        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                            // nothing more we can do; ignore
                        }
                    } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                        // log and continue
                        LOGGER.debug("Failed to forward v2 onHudRender to hud instance", e);
                    }
                }
                return null;
            });

            // Register the proxy listener on the v2 event (Event.register(Object))
            Method registerMethod = eventObj.getClass().getMethod("register", Object.class);
            registerMethod.invoke(eventObj, proxy);
            LOGGER.info("Registered SonicPulseHud for Fabric v2 HudRenderCallback via reflection");
        } catch (ClassNotFoundException cnfe) {
            LOGGER.info("Fabric v2 HudRenderCallback not present; skipping v2 registration");
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException | java.lang.reflect.InvocationTargetException ex) {
            LOGGER.error("Failed to register Fabric v2 HudRenderCallback listener via reflection", ex);
        }

        // Register keybind for config screen (default: P)
        configKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.sonicpulse.config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            "category.sonicpulse"
        ));
        ClientPlayNetworking.registerGlobalReceiver(PlayUrlPayload.ID, (payload, context) -> {
            String url = payload.url();
            context.client().execute(() -> engine.playTrack(url));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (engine != null) engine.tick();
            if (configKeyBinding.wasPressed() && client.currentScreen == null) {
                client.setScreen(new ConfigScreen());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("sonicpulse").then(ClientCommandManager.literal("test").then(ClientCommandManager.argument("url", StringArgumentType.greedyString()).executes(context -> {
                String url = StringArgumentType.getString(context, "url");
                context.getSource().sendFeedback(Text.literal("Attempting to load: " + url));
                engine.playTrack(url);
                return 1;
            }))));
        });
    }
    public static SonicPulseEngine getEngine() { return engine; }
    private void setupAssetDirectory() {
        Path configDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse");
        try {
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
                Files.createDirectories(configDir.resolve("music"));
                LOGGER.info("Created SonicPulse asset directory: {}", configDir.toString());
            }
        } catch (IOException e) {
            // Use proper logging instead of printStackTrace
            LOGGER.error("Failed to create asset directory", e);
        }
    }
}
