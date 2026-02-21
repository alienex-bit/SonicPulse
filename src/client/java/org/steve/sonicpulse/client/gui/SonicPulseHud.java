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
        
        if (!isPreview && client.currentScreen != null) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        int screenW = client.getWindow().getScaledWidth();
        float scale = cfg.hudScale;

        int ribbonWidth = (int)(screenW / scale);
        int ribbonHeight = 35; 
        
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        
        // 1. Draw standard background
        context.fill(0, 0, ribbonWidth, ribbonHeight, cfg.skin.getBgColor());

        // 2. --- REACTIVE BACKGROUND EFFECTS ---
        float[] vData = SonicPulseClient.getEngine().getVisualizerData();
        if (vData != null && vData.length >= 16) {
            if (cfg.bgEffect == SonicPulseConfig.BgEffect.BASS_PULSE) {
                // Isolate the deep bass (first 3 bins) and square it for a punchy curve
                float bass = (vData[0] + vData[1] + vData[2]) / 3.0f;
                float intensity = bass * bass * 1.5f; 
                int alpha = (int)(Math.min(intensity, 1.0f) * 120); // Cap alpha at 120 so it's not blinding
                
                if (alpha > 5) {
                    int effectColor = (alpha << 24) | (cfg.barColor & 0xFFFFFF);
                    context.fill(0, 0, ribbonWidth, ribbonHeight, effectColor);
                }
            } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.RGB_AURA) {
                // Calculate average volume across all frequencies
                float vol = 0;
                for (int i = 0; i < 16; i++) vol += vData[i];
                vol /= 16.0f;
                
                // Slow hue cycle (1 full rotation every 6 seconds)
                float hue = (System.currentTimeMillis() % 6000) / 6000.0f;
                int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
                
                // Base faint glow (20) + volume spike
                int alpha = 20 + (int)(Math.min(vol * 2.0f, 1.0f) * 100);
                int effectColor = (alpha << 24) | (rgb & 0xFFFFFF);
                context.fill(0, 0, ribbonWidth, ribbonHeight, effectColor);
            }
        }

        // 3. Draw border line over the background
        context.fill(0, ribbonHeight - 1, ribbonWidth, ribbonHeight, cfg.skin.getBorderColor());

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

        if (cfg.showLogo) {
            int titleColor = cfg.titleColor | 0xFF000000;
            String logoTxt = "SONICPULSE ♫";
            int w = textRenderer.getWidth(logoTxt);
            int drawX = (logoSlot == 0) ? leftX : ((logoSlot == 1) ? centerX - (w / 2) : rightX - w);
            context.drawText(textRenderer, Text.literal(logoTxt), drawX, 14, titleColor, false);
        }

        if (cfg.showTrack) {
            String trackName = (cfg.currentTitle != null) ? cfg.currentTitle : track.getInfo().title;
            if (trackName != null) {
                trackName = trackName.replace("|", " - "); 
                int textW = textRenderer.getWidth(trackName);
                
                int maxW = (ribbonWidth / 3) - 20;
                int drawX;

                if (textW > maxW) {
                    String spacer = "   •   ";
                    String marquee = trackName + spacer + trackName + spacer + trackName;
                    int totalLen = trackName.length() + spacer.length();
                    int offset = (int)((System.currentTimeMillis() / 150) % totalLen);
                    String scrolled = marquee.substring(offset);
                    trackName = textRenderer.trimToWidth(scrolled, maxW);
                    drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (maxW / 2) : rightX - maxW);
                } else {
                    drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (textW / 2) : rightX - textW);
                }
                context.drawText(textRenderer, Text.literal(trackName), drawX, 14, 0xFFFFFFFF, false);
            }
        }

        if (cfg.showBars) {
            int barColor = cfg.barColor | 0xFF000000;
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