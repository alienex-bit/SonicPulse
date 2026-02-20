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
    
    // Memory for the floating peaks
    private final float[] floatingPeaks = new float[16];

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        render(context, false, 0, 0);
    }

    public void render(DrawContext context, boolean isPreview, int previewX, int previewY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;
        
        SonicPulseConfig cfg = SonicPulseConfig.get();
        TextRenderer textRenderer = client.textRenderer;

        if (!isPreview && (client.currentScreen != null || !cfg.hudVisible)) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        int topHeight = cfg.showTopZone ? 15 : 0;
        int midHeight = cfg.showMidZone ? 25 : 0;
        int botHeight = cfg.showBotZone ? 50 : 0; 
        
        int hudWidth = 140;
        int hudHeight = topHeight + midHeight + botHeight;
        if (hudHeight == 0) return; 

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        // In menu it stays at 60%, in-game it uses your dynamic scale slider!
        float scale = isPreview ? 0.6f : cfg.hudScale;

        int finalX = isPreview ? previewX : ((cfg.hudX >= 0) ? cfg.hudX : screenW + cfg.hudX - (int)(hudWidth * scale));
        int finalY = isPreview ? previewY : ((cfg.hudY >= 0) ? cfg.hudY : screenH + cfg.hudY - (int)(hudHeight * scale));

        context.getMatrices().push();
        context.getMatrices().translate((float)finalX, (float)finalY, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);

        // Draw outer shell
        context.fill(0, 0, hudWidth, hudHeight, cfg.skin.getBgColor());
        context.drawBorder(0, 0, hudWidth, hudHeight, cfg.skin.getBorderColor());

        int currentY = 0; 

        // --- TOP ZONE ---
        if (cfg.showTopZone) {
            int titleColor = cfg.titleColor | 0xFF000000;
            context.drawText(textRenderer, Text.literal("SONICPULSE"), 5, currentY + 5, titleColor, false);
            context.drawText(textRenderer, Text.literal("♫"), hudWidth - 12, currentY + 5, titleColor, false); 
            currentY += topHeight;
        }

        // --- MIDDLE ZONE ---
        if (cfg.showMidZone) {
            String trackName = (cfg.currentTitle != null) ? cfg.currentTitle : track.getInfo().title;
            
            if (trackName != null && trackName.contains("|")) {
                String[] parts = trackName.split("\\|", 2);
                String artist = parts[0];
                String song = parts[1];

                if (song.toLowerCase().startsWith(artist.toLowerCase())) {
                    song = song.substring(artist.length()).trim();
                    if (song.startsWith("-")) song = song.substring(1).trim();
                }

                context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(artist, hudWidth - 10)), 5, currentY + 2, 0xFFFFFFFF, false);
                context.getMatrices().push();
                context.getMatrices().translate(5.0f, currentY + 12.0f, 0.0f);
                context.getMatrices().scale(0.8f, 0.8f, 1.0f);
                context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(song, (int)((hudWidth - 10) / 0.8f))), 0, 0, 0xFFFFFFFF, false);
                context.getMatrices().pop();
            } else if (trackName != null) {
                context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(trackName, hudWidth - 10)), 5, currentY + 8, 0xFFFFFFFF, false);
            }
            currentY += midHeight;
        }

        // --- BOTTOM ZONE ---
        if (cfg.showBotZone) {
            int barColor = cfg.barColor | 0xFF000000;
            float[] vData = SonicPulseClient.getEngine().getVisualizerData();
            int barsCount = 16;
            int barWidth = 6;
            int barSpacing = 2;
            int bottomY = currentY + botHeight - 6;
            
            for (int i = 0; i < barsCount; i++) {
                float amplitude = (vData != null && vData.length > i) ? vData[i] : 0.0f;
                int barHeight = Math.max((int)(amplitude * 40), 1); 
                int drawX = 5 + i * (barWidth + barSpacing);
                
                context.fill(drawX, bottomY - barHeight, drawX + barWidth, bottomY, barColor);

                if (cfg.visStyle == SonicPulseConfig.VisualizerStyle.FLOATING_PEAKS) {
                    if (amplitude >= floatingPeaks[i]) {
                        floatingPeaks[i] = amplitude;
                    } else {
                        floatingPeaks[i] -= 0.005f; 
                        if (floatingPeaks[i] < amplitude) floatingPeaks[i] = amplitude;
                    }
                    int peakHeight = Math.max((int)(floatingPeaks[i] * 40), 1);
                    context.fill(drawX, bottomY - peakHeight - 2, drawX + barWidth, bottomY - peakHeight - 1, 0xFFFFFFFF);
                }
            }
        }

        context.getMatrices().pop();
    }
}