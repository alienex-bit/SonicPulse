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
        render(context, false, 0, 0);
    }

    public void render(DrawContext context, boolean isPreview, int previewX, int previewY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;
        
        SonicPulseConfig cfg = SonicPulseConfig.get();
        TextRenderer textRenderer = client.textRenderer;

        if (!cfg.hudVisible) return;
        
        // Prevent normal HUD rendering if a screen is open, UNLESS it's our explicit live foreground override
        if (!isPreview && client.currentScreen != null) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        int screenW = client.getWindow().getScaledWidth();
        float scale = cfg.hudScale;

        // The Cinematic Ribbon logic
        int ribbonWidth = (int)(screenW / scale);
        int ribbonHeight = 35; 
        
        context.getMatrices().push();
        // Fixed exactly to the top of the screen
        context.getMatrices().translate(0.0f, 0.0f, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        
        // Draw a flat full-width box with a line border
        context.fill(0, 0, ribbonWidth, ribbonHeight, cfg.skin.getBgColor());
        context.fill(0, ribbonHeight - 1, ribbonWidth, ribbonHeight, cfg.skin.getBorderColor());

        // Determine Slot Positions dynamically
        int logoSlot = 0; 
        int trackSlot = 1;
        int barsSlot = 2;

        switch(cfg.ribbonLayout) {
            case LOG_TRK_BAR: logoSlot=0; trackSlot=1; barsSlot=2; break;
            case LOG_BAR_TRK: logoSlot=0; barsSlot=1; trackSlot=2; break;
            case TRK_LOG_BAR: trackSlot=0; logoSlot=1; barsSlot=2; break;
            case TRK_BAR_LOG: trackSlot=0; barsSlot=1; logoSlot=2; break;
            case BAR_LOG_TRK: barsSlot=0; logoSlot=1; trackSlot=2; break;
            case BAR_TRK_LOG: barsSlot=0; trackSlot=1; logoSlot=2; break;
        }

        int leftX = 10;
        int centerX = ribbonWidth / 2;
        int rightX = ribbonWidth - 10;

        // --- Render Logo ---
        if (cfg.showLogo) {
            int titleColor = cfg.titleColor | 0xFF000000;
            String logoTxt = "SONICPULSE ♫";
            int w = textRenderer.getWidth(logoTxt);
            int drawX = (logoSlot == 0) ? leftX : ((logoSlot == 1) ? centerX - (w / 2) : rightX - w);
            context.drawText(textRenderer, Text.literal(logoTxt), drawX, 14, titleColor, false);
        }

        // --- Render Track ---
        if (cfg.showTrack) {
            String trackName = (cfg.currentTitle != null) ? cfg.currentTitle : track.getInfo().title;
            if (trackName != null) {
                trackName = trackName.replace("|", " - "); // Flatten title for the ribbon
                int textW = textRenderer.getWidth(trackName);
                
                // Smart Truncation: Don't let a huge title crash into the side elements
                int maxW = (ribbonWidth / 3) - 20;
                if (textW > maxW) {
                    trackName = textRenderer.trimToWidth(trackName, maxW - textRenderer.getWidth("...")) + "...";
                    textW = textRenderer.getWidth(trackName);
                }

                int drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (textW / 2) : rightX - textW);
                context.drawText(textRenderer, Text.literal(trackName), drawX, 14, 0xFFFFFFFF, false);
            }
        }

        // --- Render Bars ---
        if (cfg.showBars) {
            int barColor = cfg.barColor | 0xFF000000;
            float[] vData = SonicPulseClient.getEngine().getVisualizerData();
            int barsCount = 16;
            int barWidth = 6;
            int barSpacing = 2;
            int totalBarsWidth = barsCount * (barWidth + barSpacing) - barSpacing;
            
            int drawStartX = (barsSlot == 0) ? leftX : ((barsSlot == 1) ? centerX - (totalBarsWidth / 2) : rightX - totalBarsWidth);
            int bottomY = ribbonHeight - 5;
            
            for (int i = 0; i < barsCount; i++) {
                float amplitude = (vData != null && vData.length > i) ? vData[i] : 0.0f;
                int barHeight = Math.max((int)(amplitude * 25), 1); 
                int drawX = drawStartX + i * (barWidth + barSpacing);
                
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

        context.getMatrices().pop();
    }
}