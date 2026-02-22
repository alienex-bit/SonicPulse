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
    
    // OPTIMIZATION: Cache variables to prevent heavy string generation every single frame
    private static final Text LOGO_TEXT = Text.literal("SONICPULSE ♫");
    private AudioTrack cachedTrack = null;
    private String cachedTag = "[ 🌐 WEB AUDIO ]";
    private int cachedTagColor = 0xFF00FFFF;
    
    private long cachedPosSeconds = -1;
    private Text cachedTimeText = Text.literal("");
    private Text cachedTagText = Text.literal("");
    
    private String rawTrackNameCache = null;
    private String safeTrackNameCache = null;
    private String marqueeCache = null;
    private int marqueeLenCache = 0;

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
        
        context.fill(0, 0, ribbonWidth, ribbonHeight, cfg.skin.getBgColor());

        // 2. --- REACTIVE BACKGROUND EFFECTS ---
        float[] vData = SonicPulseClient.getEngine().getVisualizerData();
        if (vData != null && vData.length >= 16) {
            if (cfg.bgEffect == SonicPulseConfig.BgEffect.BASS_PULSE) {
                float bass = (vData[0] + vData[1] + vData[2]) / 3.0f;
                float intensity = bass * bass * 1.5f; 
                int alpha = (int)(Math.min(intensity, 1.0f) * 140);
                if (alpha > 5) {
                    // Gradient: Fades from faint at the top to bright at the bottom (Stage Light effect)
                    int topColor = ((alpha / 6) << 24) | (cfg.barColor & 0xFFFFFF);
                    int bottomColor = (alpha << 24) | (cfg.barColor & 0xFFFFFF);
                    context.fillGradient(0, 0, ribbonWidth, ribbonHeight, topColor, bottomColor);
                }
            } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.RGB_AURA) {
                float vol = 0;
                for (int i = 0; i < 16; i++) vol += vData[i];
                vol /= 16.0f;
                
                float time = (System.currentTimeMillis() % 12000) / 12000.0f; 
                int alpha = 30 + (int)(Math.min(vol * 2.5f, 1.0f) * 110);
                
                // THE LIQUID WAVE: Slice the background into 24 strips to simulate a horizontal flowing gradient
                int segments = 24;
                float segWidth = (float) ribbonWidth / segments;
                
                for (int s = 0; s < segments; s++) {
                    int startX = (int)(s * segWidth);
                    int endX = (int)((s + 1) * segWidth);
                    if (s == segments - 1) endX = ribbonWidth; // Ensure it reaches the exact edge
                    
                    // Add a horizontal offset (s * 0.25f) so the color ripples left-to-right
                    float wave = (float)Math.sin(time * Math.PI * 2 + (s * 0.25f));
                    float hue = 0.7f + (wave * 0.2f);
                    
                    int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 1.0f);
                    int topColor = (alpha << 24) | (rgb & 0xFFFFFF);
                    int bottomColor = ((alpha / 4) << 24) | (rgb & 0xFFFFFF);
                    
                    context.fillGradient(startX, 0, endX, ribbonHeight, topColor, bottomColor);
                }
            }
        }

        context.fill(0, ribbonHeight - 1, ribbonWidth, ribbonHeight, cfg.skin.getBorderColor());

        int logoSlot = 0, trackSlot = 1, barsSlot = 2;
        switch(cfg.ribbonLayout) {
            case LOG_TRK_BAR: logoSlot=0; trackSlot=1; barsSlot=2; break;
            case LOG_BAR_TRK: logoSlot=0; barsSlot=1; trackSlot=2; break;
            case TRK_LOG_BAR: trackSlot=0; logoSlot=1; barsSlot=2; break;
            case TRK_BAR_LOG: trackSlot=0; barsSlot=1; logoSlot=2; break;
            case BAR_LOG_TRK: barsSlot=0; logoSlot=1; trackSlot=2; break;
            case BAR_TRK_LOG: barsSlot=0; trackSlot=1; logoSlot=2; break;
        }

        int leftX = 10, centerX = ribbonWidth / 2, rightX = ribbonWidth - 10;

        if (cfg.showLogo) {
            int titleColor = cfg.titleColor | 0xFF000000;
            int w = textRenderer.getWidth(LOGO_TEXT);
            int drawX = (logoSlot == 0) ? leftX : ((logoSlot == 1) ? centerX - (w / 2) : rightX - w);
            context.drawText(textRenderer, LOGO_TEXT, drawX, 14, titleColor, false);
        }

        if (cfg.showTrack) {
            if (track != cachedTrack) {
                cachedTrack = track;
                String uri = track.getInfo().uri.toLowerCase();
                boolean isLocal = uri.startsWith("file") || uri.matches("^[a-zA-Z]:\\\\.*");
                
                if (isLocal) { cachedTag = "[ 📁 LOCAL ]"; cachedTagColor = 0xFFFF00FF; }
                else if (track.getInfo().isStream) {
                    if (uri.contains("twitch.tv")) { cachedTag = "[ 📺 TWITCH ]"; cachedTagColor = 0xFFA020F0; }
                    else if (uri.contains("youtube.com") || uri.contains("youtu.be")) { cachedTag = "[ 🔴 YT LIVE ]"; cachedTagColor = 0xFFFF0000; }
                    else { cachedTag = "[ 📻 STREAM ]"; cachedTagColor = 0xFF00FFFF; }
                } else {
                    if (uri.contains("youtube.com") || uri.contains("youtu.be")) { cachedTag = "[ ► YOUTUBE ]"; cachedTagColor = 0xFFFF0000; }
                    else if (uri.contains("soundcloud.com")) { cachedTag = "[ ☁ SOUNDCLOUD ]"; cachedTagColor = 0xFFFFA500; }
                    else if (uri.contains("bandcamp.com")) { cachedTag = "[ 🎧 BANDCAMP ]"; cachedTagColor = 0xFF00CED1; }
                    else if (uri.contains("vimeo.com")) { cachedTag = "[ 🎬 VIMEO ]"; cachedTagColor = 0xFF1E90FF; }
                    else { cachedTag = "[ 🌐 WEB AUDIO ]"; cachedTagColor = 0xFF00FFFF; }
                }
                cachedTagText = Text.literal(cachedTag);
                cachedPosSeconds = -1; 
            }

            long currentPosSec = track.getPosition() / 1000;
            if (currentPosSec != cachedPosSeconds) {
                cachedPosSeconds = currentPosSec;
                if (!track.getInfo().isStream) {
                    long dur = track.getDuration() / 1000;
                    cachedTimeText = Text.literal(String.format("  %02d:%02d / %02d:%02d", currentPosSec/60, currentPosSec%60, dur/60, dur%60));
                } else {
                    cachedTimeText = Text.literal("");
                }
            }

            String trackName = (cfg.currentTitle != null) ? cfg.currentTitle : track.getInfo().title;
            if (trackName != null) {
                if (!trackName.equals(rawTrackNameCache)) {
                    rawTrackNameCache = trackName;
                    safeTrackNameCache = trackName.replace("|", " - ");
                    String spacer = "   •   ";
                    marqueeCache = safeTrackNameCache + spacer + safeTrackNameCache + spacer + safeTrackNameCache;
                    marqueeLenCache = safeTrackNameCache.length() + spacer.length();
                }

                int textW = textRenderer.getWidth(safeTrackNameCache);
                int maxW = (ribbonWidth / 3) - 20;
                int drawX;

                if (textW > maxW) {
                    int offset = (int)((System.currentTimeMillis() / 150) % marqueeLenCache);
                    String scrolled = marqueeCache.substring(offset);
                    String finalDisplay = textRenderer.trimToWidth(scrolled, maxW);
                    drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (maxW / 2) : rightX - maxW);
                    context.drawText(textRenderer, Text.literal(finalDisplay), drawX, 6, 0xFFFFFFFF, false);
                } else {
                    drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (textW / 2) : rightX - textW);
                    context.drawText(textRenderer, Text.literal(safeTrackNameCache), drawX, 6, 0xFFFFFFFF, false);
                }

                context.drawText(textRenderer, cachedTagText, drawX, 18, cachedTagColor, false);
                context.drawText(textRenderer, cachedTimeText, drawX + textRenderer.getWidth(cachedTagText), 18, 0xFFFFFFFF, false);
            }
        }

        if (cfg.showBars) {
            int barColor = cfg.barColor | 0xFF000000;
            int barsCount = 16, barWidth = 6, barSpacing = 2;
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