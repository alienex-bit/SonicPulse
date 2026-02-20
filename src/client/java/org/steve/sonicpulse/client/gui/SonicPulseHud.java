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
import org.steve.sonicpulse.client.screen.ConfigScreen;

public class SonicPulseHud implements HudRenderCallback {
    
    // Memory for the floating peaks
    private final float[] floatingPeaks = new float[16];

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;
        
        SonicPulseConfig cfg = SonicPulseConfig.get();
        TextRenderer textRenderer = client.textRenderer;

        // True Live Preview: Only hide the HUD if the open screen is NOT our ConfigScreen
        if (!cfg.hudVisible) return;
        if (client.currentScreen != null && !(client.currentScreen instanceof ConfigScreen)) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        float scale = cfg.hudScale;

        context.getMatrices().push();

        if (cfg.hudMode == SonicPulseConfig.HudMode.CLASSIC) {
            int topHeight = cfg.showTopZone ? 15 : 0;
            int midHeight = cfg.showMidZone ? 25 : 0;
            int botHeight = cfg.showBotZone ? 50 : 0; 
            
            int hudWidth = 140;
            int hudHeight = topHeight + midHeight + botHeight;
            if (hudHeight == 0) { context.getMatrices().pop(); return; }

            int finalX = (cfg.hudX >= 0) ? cfg.hudX : screenW + cfg.hudX - (int)(hudWidth * scale);
            int finalY = (cfg.hudY >= 0) ? cfg.hudY : screenH + cfg.hudY - (int)(hudHeight * scale);

            context.getMatrices().translate((float)finalX, (float)finalY, 0.0f);
            context.getMatrices().scale(scale, scale, 1.0f);

            context.fill(0, 0, hudWidth, hudHeight, cfg.skin.getBgColor());
            context.drawBorder(0, 0, hudWidth, hudHeight, cfg.skin.getBorderColor());

            int currentY = 0; 
            if (cfg.showTopZone) {
                int titleColor = cfg.titleColor | 0xFF000000;
                context.drawText(textRenderer, Text.literal("SONICPULSE"), 5, currentY + 5, titleColor, false);
                context.drawText(textRenderer, Text.literal("♫"), hudWidth - 12, currentY + 5, titleColor, false); 
                currentY += topHeight;
            }

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
        } 
        else if (cfg.hudMode == SonicPulseConfig.HudMode.CINEMATIC) {
            // The Cinematic Ribbon logic
            int ribbonWidth = (int)(screenW / scale);
            int ribbonHeight = 35; 
            
            int finalX = 0; // Stretches fully across
            int finalY = (cfg.hudY >= 0) ? cfg.hudY : screenH + cfg.hudY - (int)(ribbonHeight * scale);
            
            context.getMatrices().translate((float)finalX, (float)finalY, 0.0f);
            context.getMatrices().scale(scale, scale, 1.0f);
            
            // Draw a flat full-width box with a line border
            context.fill(0, 0, ribbonWidth, ribbonHeight, cfg.skin.getBgColor());
            context.fill(0, ribbonHeight - 1, ribbonWidth, ribbonHeight, cfg.skin.getBorderColor());
            
            // LEFT: Logo
            if (cfg.showTopZone) {
                int titleColor = cfg.titleColor | 0xFF000000;
                context.drawText(textRenderer, Text.literal("SONICPULSE ♫"), 10, 14, titleColor, false);
            }
            
            // CENTER: Track Info
            if (cfg.showMidZone) {
                String trackName = (cfg.currentTitle != null) ? cfg.currentTitle : track.getInfo().title;
                if (trackName != null) {
                    trackName = trackName.replace("|", " - "); // Flatten title for the ribbon
                    int textW = textRenderer.getWidth(trackName);
                    context.drawText(textRenderer, Text.literal(trackName), (ribbonWidth / 2) - (textW / 2), 14, 0xFFFFFFFF, false);
                }
            }
            
            // RIGHT: Bars
            if (cfg.showBotZone) {
                int barColor = cfg.barColor | 0xFF000000;
                float[] vData = SonicPulseClient.getEngine().getVisualizerData();
                int barsCount = 16;
                int barWidth = 6;
                int barSpacing = 2;
                int totalBarsWidth = barsCount * (barWidth + barSpacing);
                int startX = ribbonWidth - totalBarsWidth - 10;
                int bottomY = ribbonHeight - 5;
                
                for (int i = 0; i < barsCount; i++) {
                    float amplitude = (vData != null && vData.length > i) ? vData[i] : 0.0f;
                    int barHeight = Math.max((int)(amplitude * 25), 1); 
                    int drawX = startX + i * (barWidth + barSpacing);
                    
                    context.fill(drawX, bottomY - barHeight, drawX + barWidth, bottomY, barColor);

                    if (cfg.visStyle == SonicPulseConfig.VisualizerStyle.FLOATING_PEAKS) {
                        if (amplitude >= floatingPeaks[i]) {
                            floatingPeaks[i] = amplitude;
                        } else {
                            floatingPeaks[i] -= 0.005f; 
                            if (floatingPeaks[i] < amplitude) floatingPeaks[i] = amplitude;
                        }
                        int peakHeight = Math.max((int)(floatingPeaks[i] * 25), 1);
                        context.fill(drawX, bottomY - peakHeight - 2, drawX + barWidth, bottomY - peakHeight - 1, 0xFFFFFFFF);
                    }
                }
            }
        }
        
        context.getMatrices().pop();
    }
}