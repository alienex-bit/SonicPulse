package org.steve.sonicpulse.client.gui;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class SonicPulseHud implements HudRenderCallback {
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        render(context, false, 0, 0);
    }

    public void render(DrawContext context, boolean isPreview, int previewX, int previewY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;
        
        SonicPulseConfig cfg = SonicPulseConfig.get();
        TextRenderer textRenderer = client.textRenderer;

        boolean isVisible = cfg.hudVisible;
        int configX = cfg.hudX;
        int configY = cfg.hudY;
        
        // BUG FIX: Force full opacity (Alpha FF) on the colors.
        // A 6-digit hex color like 0x00BFFF is inherently completely transparent.
        int barColor = cfg.barColor | 0xFF000000;
        int titleColor = cfg.titleColor | 0xFF000000;
        
        int bgColor = cfg.skin.getBgColor();
        int borderColor = cfg.skin.getBorderColor();
        String currentTitle = cfg.currentTitle;

        if (!isPreview && (client.currentScreen != null || !isVisible)) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int hudWidth = 140;
        int hudHeight = 55;

        int finalX;
        int finalY;

        if (isPreview) {
            finalX = previewX;
            finalY = previewY;
        } else {
            finalX = (configX >= 0) ? configX : screenW + configX - hudWidth;
            finalY = (configY >= 0) ? configY : screenH + configY - hudHeight;
        }

        context.getMatrices().push();
        context.getMatrices().translate((float)finalX, (float)finalY, 0.0f);

        // Draw Background and Border
        context.fill(0, 0, hudWidth, hudHeight, bgColor);
        context.drawBorder(0, 0, hudWidth, hudHeight, borderColor);

        // Draw Visualizer Bars FIRST (Behind text)
        float[] vData = SonicPulseClient.getEngine().getVisualizerData();
        int barsCount = 16;
        int barWidth = 6;
        int barSpacing = 2;
        int bottomY = hudHeight - 6;
        
        for (int i = 0; i < barsCount; i++) {
            float amplitude = (vData != null && vData.length > i) ? vData[i] : 0.0f;
            
            // BUG FIX: Ensure the bar is always at least 1 pixel tall so it never vanishes
            int barHeight = Math.max((int)(amplitude * 35), 1); 
            int drawX = 5 + i * (barWidth + barSpacing);
            
            context.fill(drawX, bottomY - barHeight, drawX + barWidth, bottomY, barColor);
        }

        // Draw Text SECOND (On top)
        context.drawText(textRenderer, Text.literal("SONICPULSE"), 5, 5, titleColor, false);
        context.drawText(textRenderer, Text.literal("♫"), hudWidth - 12, 5, titleColor, false); 

        String trackDisplayName = (currentTitle != null) ? currentTitle : track.getInfo().title;
        
        if (trackDisplayName != null && trackDisplayName.contains("|")) {
            String[] parts = trackDisplayName.split("\\|", 2);
            String artistName = parts[0];
            String songName = parts[1];

            if (songName.toLowerCase().startsWith(artistName.toLowerCase())) {
                songName = songName.substring(artistName.length()).trim();
                if (songName.startsWith("-")) songName = songName.substring(1).trim();
            }

            context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(artistName, hudWidth - 10)), 5, 17, 0xFFFFFFFF, false);
            
            context.getMatrices().push();
            context.getMatrices().translate(5.0f, 27.0f, 0.0f);
            context.getMatrices().scale(0.8f, 0.8f, 1.0f);
            context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(songName, (int)((hudWidth - 10) / 0.8f))), 0, 0, 0xFFFFFFFF, false);
            context.getMatrices().pop();
        } else if (trackDisplayName != null) {
            context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(trackDisplayName, hudWidth - 10)), 5, 17, 0xFFFFFFFF, false);
        }

        context.getMatrices().pop();
    }
}