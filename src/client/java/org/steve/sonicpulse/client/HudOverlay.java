package org.steve.sonicpulse.client;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class HudOverlay implements HudRenderCallback {
    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        render(drawContext);
    }

    // v2-style method for reflective invocation
    public void onHudRender(DrawContext drawContext, float tickDelta) {
        render(drawContext);
    }

    private void render(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        SonicPulseConfig config = SonicPulseConfig.get();
        int x = 10;
        int y = 10;
        
        String displayLabel = track.getInfo().author + " - " + track.getInfo().title;
        for (SonicPulseConfig.HistoryEntry entry : config.history) {
            if (entry.url.equals(track.getInfo().uri)) {
                displayLabel = entry.label;
                break;
            }
        }

        // Title uses configurable titleColor
        drawContext.drawText(client.textRenderer, Text.literal("SonicPulse"), x, y, config.titleColor, true);
        drawContext.drawText(client.textRenderer, Text.literal(displayLabel), x, y + 10, 0xFFFFFFFF, true);
    }
}
