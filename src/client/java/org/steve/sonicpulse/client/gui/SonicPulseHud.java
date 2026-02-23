package org.steve.sonicpulse.client.gui;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class SonicPulseHud implements HudRenderCallback {
    
    private final float[] floatingPeaks = new float[16];
    
    private static final Style ROBOTO = Style.EMPTY.withFont(Identifier.of("sonicpulse", "roboto"));
    
    private static final Text LOGO_TEXT = Text.empty()
        .append(Text.literal("SONICPULSE ").setStyle(ROBOTO))
        .append(Text.literal("♫"));
    
    private AudioTrack cachedTrack = null;
    private int cachedTagColor = 0xFF00FFFF;
    
    private long cachedPosSeconds = -1;
    private Text cachedTimeText = Text.empty();
    private Text cachedTagText = Text.empty();
    
    private String rawTrackNameCache = null;
    private String safeTrackNameCache = null;
    private String marqueeCache = null;
    private int marqueeLenCache = 0;
    private long marqueeStartTime = 0;

    private float baselineBass = 0;
    private float pulseLerp = 0;
    private float heatmapLerp = 0;

    private long vhsGlitchEndTime = 0;
    private int vhsGlitchBands = 0;
    private final int[][] vhsGlitchData = new int[6][4];

    private static final int HSB_LUT_SIZE = 256;
    private static final int[] HSB_LUT = new int[HSB_LUT_SIZE];
    static {
        for (int i = 0; i < HSB_LUT_SIZE; i++) {
            float hue = i / (float) HSB_LUT_SIZE;
            float s = 0.85f, b = 1.0f;
            int hi = (int)(hue * 6) % 6;
            float f = hue * 6 - (int)(hue * 6);
            float p = b * (1 - s);
            float q = b * (1 - f * s);
            float t = b * (1 - (1 - f) * s);
            float r, g, bl;
            switch (hi) {
                case 0: r=b; g=t; bl=p; break;
                case 1: r=q; g=b; bl=p; break;
                case 2: r=p; g=b; bl=t; break;
                case 3: r=p; g=q; bl=b; break;
                case 4: r=t; g=p; bl=b; break;
                default: r=b; g=p; bl=q; break;
            }
            HSB_LUT[i] = (((int)(r*255)) << 16) | (((int)(g*255)) << 8) | ((int)(bl*255));
        }
    }

    private static int hsbLookup(float hue) {
        int idx = (int)(((hue % 1.0f + 1.0f) % 1.0f) * HSB_LUT_SIZE) % HSB_LUT_SIZE;
        return HSB_LUT[idx];
    }

    private Text style(String text) {
        return Text.literal(text).setStyle(ROBOTO);
    }

    private Text buildTag(String icon, String name) {
        return Text.empty()
            .append(Text.literal("[ ").setStyle(ROBOTO))
            .append(Text.literal(icon))
            .append(Text.literal(" " + name + " ]").setStyle(ROBOTO));
    }

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
        if (track == null && !isPreview) return;

        int screenW = client.getWindow().getScaledWidth();
        float scale = cfg.hudScale;

        int ribbonWidth = (int)(screenW / scale);
        int ribbonHeight = 35; 
        
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 0.0f);
        context.getMatrices().scale(scale, scale, 1.0f);
        
        context.fill(0, 0, ribbonWidth, ribbonHeight, cfg.skin.getBgColor());

        float[] vData = SonicPulseClient.getEngine().getVisualizerData();
        int centerX = ribbonWidth / 2;

        if (vData != null && vData.length >= 16) {
            float bass = (vData[0] + vData[1] + vData[2]) / 3.0f;
            baselineBass += (bass - baselineBass) * 0.05f;
            float spike = Math.max(0, bass - (baselineBass * 0.9f));

            if (cfg.bgEffect == SonicPulseConfig.BgEffect.PULSE) {
                float mult = cfg.pulseIntensity == SonicPulseConfig.PulseIntensity.SUBTLE ? 4.0f : (cfg.pulseIntensity == SonicPulseConfig.PulseIntensity.OVERDRIVE ? 24.0f : 12.0f);
                float targetPulse = Math.min(spike * mult, 1.0f);
                float decay = cfg.pulseDecay == SonicPulseConfig.PulseDecay.SNAPPY ? 0.4f : 0.15f;
                pulseLerp += (targetPulse - pulseLerp) * decay;
                
                int alpha = (int)(pulseLerp * 180);
                if (alpha > 5) {
                    int topColor = ((alpha / 3) << 24) | (cfg.barColor & 0xFFFFFF);
                    int bottomColor = (alpha << 24) | (cfg.barColor & 0xFFFFFF);
                    context.fillGradient(0, 0, ribbonWidth, ribbonHeight, topColor, bottomColor);
                }

            } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.AURA) {
                float vol = 0;
                for (int i = 0; i < 16; i++) vol += vData[i];
                vol /= 16.0f;

                float timeDiv = cfg.auraSpeed == SonicPulseConfig.AuraSpeed.CHILL ? 24000.0f : (cfg.auraSpeed == SonicPulseConfig.AuraSpeed.WARP ? 4000.0f : 12000.0f);
                float time = (System.currentTimeMillis() % (long)timeDiv) / timeDiv;
                int alpha = 30 + (int)(Math.min(vol * 2.5f, 1.0f) * 110);

                int segments = 24;
                float segWidth = (float) ribbonWidth / segments;

                float[] segHue = new float[segments + 1];
                for (int s = 0; s <= segments; s++) {
                    float wave = (float) Math.sin(time * Math.PI * 2 + (s * 0.25f));
                    segHue[s] = 0.7f + (wave * 0.2f);
                }

                for (int s = 0; s < segments; s++) {
                    int startX = (int)(s * segWidth);
                    int endX   = (int)((s + 1) * segWidth);
                    if (s == segments - 1) endX = ribbonWidth;

                    int rgbLeft;
                    int rgbRight;

                    if (cfg.auraPalette == SonicPulseConfig.AuraPalette.AURORA) {
                        float auroraLeftHue = 0.5f + (segHue[s] % 1.0f) * 0.35f;
                        float auroraRightHue = 0.5f + (segHue[s + 1] % 1.0f) * 0.35f;
                        rgbLeft = hsbLookup(auroraLeftHue);
                        rgbRight = hsbLookup(auroraRightHue);
                    } else {
                        rgbLeft = hsbLookup(segHue[s]);
                        rgbRight = hsbLookup(segHue[s + 1]);
                    }

                    int topL    = (alpha << 24)       | (rgbLeft  & 0xFFFFFF);
                    int topR    = (alpha << 24)       | (rgbRight & 0xFFFFFF);
                    int botL    = ((alpha / 4) << 24) | (rgbLeft  & 0xFFFFFF);
                    int botR    = ((alpha / 4) << 24) | (rgbRight & 0xFFFFFF);

                    context.fillGradient(startX, 0, endX, ribbonHeight, topL, botR);
                }

            } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.VHS) {
                int alphaScan = cfg.vhsScanlines == SonicPulseConfig.VhsScanlines.OFF ? 0 : (cfg.vhsScanlines == SonicPulseConfig.VhsScanlines.DARK ? 0x33000000 : 0x1A000000);
                if (alphaScan != 0) {
                    for (int y = 0; y < ribbonHeight; y += 2) {
                        context.fill(0, y, ribbonWidth, y + 1, alphaScan);
                    }
                }

                long now = System.currentTimeMillis();
                float spikeThresh = cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.MINOR ? 0.18f : (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 0.08f : 0.12f);

                if (spike > spikeThresh && now > vhsGlitchEndTime) {
                    int baseBands = cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.MINOR ? 1 : (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 4 : 2);
                    vhsGlitchBands = baseBands + (int)(spike * (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 8 : 5)); 
                    if (vhsGlitchBands > vhsGlitchData.length) vhsGlitchBands = vhsGlitchData.length;
                    
                    vhsGlitchEndTime = now + 60 + (long)(spike * 80);
                    for (int i = 0; i < vhsGlitchBands; i++) {
                        vhsGlitchData[i][0] = (int)(Math.random() * ribbonHeight);         
                        vhsGlitchData[i][1] = 2 + (int)(Math.random() * 6);                
                        vhsGlitchData[i][2] = (int)(Math.random() * (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 30 : 18)) - (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 15 : 9);               
                        vhsGlitchData[i][3] = (int)(Math.random() * 2);                    
                    }
                }

                if (now <= vhsGlitchEndTime) {
                    for (int i = 0; i < vhsGlitchBands; i++) {
                        int ry    = vhsGlitchData[i][0];
                        int rh    = vhsGlitchData[i][1];
                        int shift = vhsGlitchData[i][2];
                        if (vhsGlitchData[i][3] == 0) {
                            context.fill(Math.max(0, shift),  ry,     Math.min(ribbonWidth, ribbonWidth + shift),  ry + rh,     0x33FF0055);
                            context.fill(Math.max(0, -shift), ry + 1, Math.min(ribbonWidth, ribbonWidth - shift),  ry + rh + 1, 0x3300AAFF);
                        } else {
                            context.fill(Math.max(0, shift),  ry,     Math.min(ribbonWidth, ribbonWidth + shift),  ry + rh,     0x3300AAFF);
                            context.fill(Math.max(0, -shift), ry + 1, Math.min(ribbonWidth, ribbonWidth - shift),  ry + rh + 1, 0x33FF0055);
                        }
                    }
                }

            } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.HEATMAP) {
                float vol = 0;
                for (int i = 0; i < 16; i++) vol += vData[i];
                vol /= 16.0f;

                float baseCore = Math.min(vol * 1.5f, 0.3f); 
                float spikeExpansion = Math.min(spike * 4.0f, 0.7f); 
                float targetVol = baseCore + spikeExpansion;

                if (cfg.heatmapSpread == SonicPulseConfig.HeatmapSpread.CONFINED) targetVol *= 0.45f;

                heatmapLerp += (targetVol - heatmapLerp) * (targetVol > heatmapLerp ? 0.3f : 0.05f);

                int numSlices = 30;
                float sliceW = (ribbonWidth / 2.0f) / numSlices;
                
                float centerHue = 0.0f, edgeHue = 0.65f;
                if (cfg.heatmapScale == SonicPulseConfig.HeatmapScale.PLASMA) { centerHue = 0.85f; edgeHue = 0.5f; }
                else if (cfg.heatmapScale == SonicPulseConfig.HeatmapScale.TOXIC) { centerHue = 0.3f; edgeHue = 0.75f; }

                for (int s = 0; s < numSlices; s++) {
                    float pct = (float) s / numSlices; 
                    float alphaPct = Math.max(0, 1.0f - (pct / Math.max(0.01f, heatmapLerp)));
                    if (alphaPct <= 0.01f) continue;

                    int a = (int)(alphaPct * 160);
                    float hue = centerHue + (edgeHue - centerHue) * Math.min(1.0f, pct / Math.max(0.05f, heatmapLerp));

                    int rgb   = hsbLookup(hue);
                    int color = (a << 24) | (rgb & 0xFFFFFF);

                    int lx = (int)(centerX - (s + 1) * sliceW);
                    int rx = (int)(centerX + s * sliceW);
                    context.fill(lx, 0, lx + (int) sliceW, ribbonHeight, color);
                    context.fill(rx, 0, rx + (int) sliceW, ribbonHeight, color);
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

        int leftX = 10, rightX = ribbonWidth - 10;
        boolean playing = track != null;

        int logoW = textRenderer.getWidth(LOGO_TEXT);
        int logoDrawX = (logoSlot == 0) ? leftX : ((logoSlot == 1) ? centerX - (logoW / 2) : rightX - logoW);
        
        int logoY = playing ? 6 : 14;

        if (cfg.showLogo) {
            int titleColor = cfg.titleColor | 0xFF000000;
            context.drawText(textRenderer, LOGO_TEXT, logoDrawX, logoY, titleColor, false);
        }

        if (playing) {
            if (track != cachedTrack) {
                cachedTrack = track;
                String uri = track.getInfo().uri.toLowerCase();
                
                // SOURCE LABELING FIX: Check original metadata type for replays and radio
                boolean isLocal = uri.startsWith("file") || uri.matches("^[a-zA-Z]:\\\\.*") || (SonicPulseConfig.get().activeMode == SonicPulseConfig.SessionMode.LOCAL);
                boolean isRadio = (SonicPulseConfig.get().activeMode == SonicPulseConfig.SessionMode.RADIO);
                
                if (isLocal) { cachedTagText = buildTag("📁", "LOCAL"); cachedTagColor = 0xFFFF00FF; }
                else if (isRadio) { cachedTagText = buildTag("📻", "RADIO"); cachedTagColor = 0xFF00FFFF; }
                else if (track.getInfo().isStream) {
                    if (uri.contains("twitch.tv")) { cachedTagText = buildTag("📺", "TWITCH"); cachedTagColor = 0xFFA020F0; }
                    else if (uri.contains("youtube.com") || uri.contains("youtu.be")) { cachedTagText = buildTag("🔴", "YT LIVE"); cachedTagColor = 0xFFFF0000; }
                    else { cachedTagText = buildTag("📻", "STREAM"); cachedTagColor = 0xFF00FFFF; }
                } else {
                    if (uri.contains("youtube.com") || uri.contains("youtu.be")) { cachedTagText = buildTag("►", "YOUTUBE"); cachedTagColor = 0xFFFF0000; }
                    else if (uri.contains("soundcloud.com")) { cachedTagText = buildTag("☁", "SOUNDCLOUD"); cachedTagColor = 0xFFFFA500; }
                    else if (uri.contains("bandcamp.com")) { cachedTagText = buildTag("🎧", "BANDCAMP"); cachedTagColor = 0xFF00CED1; }
                    else if (uri.contains("vimeo.com")) { cachedTagText = buildTag("🎬", "VIMEO"); cachedTagColor = 0xFF1E90FF; }
                    else { cachedTagText = buildTag("🌐", "REMOTE"); cachedTagColor = 0xFF00FFFF; }
                }
                cachedPosSeconds = -1;
            }

            long currentPosSec = track.getPosition() / 1000;
            if (currentPosSec != cachedPosSeconds) {
                cachedPosSeconds = currentPosSec;
                if (!track.getInfo().isStream) {
                    long dur = track.getDuration() / 1000;
                    cachedTimeText = style(String.format("  %02d:%02d / %02d:%02d", currentPosSec/60, currentPosSec%60, dur/60, dur%60));
                } else {
                    cachedTimeText = style("");
                }
            }

            context.drawText(textRenderer, cachedTagText, logoDrawX, 18, cachedTagColor, false);
            context.drawText(textRenderer, cachedTimeText, logoDrawX + textRenderer.getWidth(cachedTagText), 18, 0xFFFFFFFF, false);
        }

        if (cfg.showTrack && playing) {
            String trackName = (cfg.currentTitle != null) ? cfg.currentTitle : track.getInfo().title;
            if (trackName != null) {
                if (!trackName.equals(rawTrackNameCache)) {
                    rawTrackNameCache = trackName;
                    safeTrackNameCache = trackName.replace("|", " - ");
                    String spacer = "   •   ";
                    marqueeCache = safeTrackNameCache + spacer + safeTrackNameCache + spacer + safeTrackNameCache;
                    marqueeLenCache = safeTrackNameCache.length() + spacer.length();
                    marqueeStartTime = System.currentTimeMillis();
                }

                Text fullTitleText = style(safeTrackNameCache);
                int unscaledTextW = textRenderer.getWidth(fullTitleText);
                
                float trackScale = 1.35f;
                int scaledTextW = (int)(unscaledTextW * trackScale);
                
                int maxW = (ribbonWidth / 3) - 20;
                int drawX;

                if (scaledTextW > maxW) {
                    int unscaledMaxW = (int)(maxW / trackScale);
                    int offset = (int)(((System.currentTimeMillis() - marqueeStartTime) / 150) % marqueeLenCache);
                    String scrolled = marqueeCache.substring(offset);
                    String finalDisplay = textRenderer.trimToWidth(scrolled, unscaledMaxW);
                    
                    drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (maxW / 2) : rightX - maxW);
                    
                    context.getMatrices().push();
                    context.getMatrices().translate(drawX, 11, 0);
                    context.getMatrices().scale(trackScale, trackScale, 1.0f);
                    context.drawText(textRenderer, style(finalDisplay), 0, 0, 0xFFFFFFFF, false);
                    context.getMatrices().pop();
                } else {
                    drawX = (trackSlot == 0) ? leftX : ((trackSlot == 1) ? centerX - (scaledTextW / 2) : rightX - scaledTextW);
                    
                    context.getMatrices().push();
                    context.getMatrices().translate(drawX, 11, 0);
                    context.getMatrices().scale(trackScale, trackScale, 1.0f);
                    context.drawText(textRenderer, fullTitleText, 0, 0, 0xFFFFFFFF, false);
                    context.getMatrices().pop();
                }
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