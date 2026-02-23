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
import org.steve.sonicpulse.client.engine.SonicPulseEngine;
import java.util.ArrayList;
import java.util.List;

public class SonicPulseHud implements HudRenderCallback {
    private final float[] floatingPeaks = new float[16];
    private static final Style ROBOTO = Style.EMPTY.withFont(Identifier.of("sonicpulse", "roboto"));
    private static final Text LOGO_TEXT = Text.empty().append(Text.literal("SONICPULSE ").setStyle(ROBOTO)).append(Text.literal("♫"));
    
    private AudioTrack cachedTrack = null;
    private int cachedTagColor = 0xFF00FFFF;
    private long cachedPosSeconds = -1;
    private Text cachedTimeText = Text.empty(), cachedTagText = Text.empty();
    
    // Marquee Optimization
    private String rawTrackNameCache = null, safeTrackNameCache = null, marqueeCache = null, currentScrolledText = "";
    private int marqueeLenCache = 0;
    private long lastMarqueeUpdate = 0;

    private float baselineBass = 0, pulseLerp = 0, heatmapLerp = 0;
    private final List<Integer> activeSlots = new ArrayList<>();
    private final int[][] vhsGlitchData = new int[6][4];
    private long vhsGlitchEndTime = 0;
    private int vhsGlitchBands = 0;

    private static final int HSB_LUT_SIZE = 256;
    private static final int[] HSB_LUT = new int[HSB_LUT_SIZE];
    static {
        for (int i = 0; i < HSB_LUT_SIZE; i++) {
            float hue = i / (float) HSB_LUT_SIZE;
            float s = 0.85f, b = 1.0f;
            int hi = (int)(hue * 6) % 6;
            float f = hue * 6 - (int)(hue * 6);
            float p = b * (1 - s), q = b * (1 - f * s), t = b * (1 - (1 - f) * s);
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

    private static int hsbLookup(float hue) { return HSB_LUT[(int)(((hue % 1.0f + 1.0f) % 1.0f) * HSB_LUT_SIZE) % HSB_LUT_SIZE]; }
    private Text style(String text) { return Text.literal(text).setStyle(ROBOTO); }
    private Text buildTag(String icon, String name) { return Text.empty().append(Text.literal("[ ").setStyle(ROBOTO)).append(Text.literal(icon)).append(Text.literal(" " + name + " ]").setStyle(ROBOTO)); }

    @Override public void onHudRender(DrawContext context, RenderTickCounter tickCounter) { render(context, false, 0, 0); }

    public void render(DrawContext context, boolean isPreview, int previewX, int previewY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;
        SonicPulseConfig cfg = SonicPulseConfig.get();
        if (!cfg.hudVisible || (!isPreview && client.currentScreen != null)) return;

        SonicPulseEngine engine = SonicPulseClient.getEngine();
        AudioTrack track = (engine != null && engine.getPlayer() != null) ? engine.getPlayer().getPlayingTrack() : null;
        if (track == null && !isPreview && (engine == null || !engine.isBuffering())) return;

        int screenW = client.getWindow().getScaledWidth();
        float scale = cfg.hudScale;
        
        activeSlots.clear();
        int[] seq = switch(cfg.ribbonLayout) {
            case LOG_TRK_BAR -> new int[]{1, 2, 3}; case LOG_BAR_TRK -> new int[]{1, 3, 2};
            case TRK_LOG_BAR -> new int[]{2, 1, 3}; case TRK_BAR_LOG -> new int[]{2, 3, 1};
            case BAR_LOG_TRK -> new int[]{3, 1, 2}; case BAR_TRK_LOG -> new int[]{3, 2, 1};
        };
        for (int i : seq) {
            if (i == 1 && cfg.showLogo) activeSlots.add(1);
            if (i == 2 && cfg.showTrack) activeSlots.add(2);
            if (i == 3 && cfg.showBars) activeSlots.add(3);
        }

        int ribbonWidth = Math.max(50, (int)((screenW / scale) * cfg.hudWidth));
        int slotWidth = ribbonWidth / Math.max(1, activeSlots.size());

        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        context.getMatrices().translate(((screenW / scale) - ribbonWidth) / 2.0f, 0.0f, 0.0f);
        context.fill(0, 0, ribbonWidth, 35, cfg.skin.getBgColor());

        float[] vData = engine != null ? engine.getVisualizerData() : null;
        if (vData != null && vData.length >= 16) renderEffects(context, cfg, vData, ribbonWidth);

        context.fill(0, 34, ribbonWidth, 35, cfg.skin.getBorderColor());
        renderElements(context, client.textRenderer, cfg, track, ribbonWidth, slotWidth);
        context.getMatrices().pop();
    }

    private void renderEffects(DrawContext context, SonicPulseConfig cfg, float[] vData, int ribbonWidth) {
        float bass = (vData[0] + vData[1] + vData[2]) / 3.0f;
        baselineBass += (bass - baselineBass) * 0.05f;
        float spike = Math.max(0, bass - (baselineBass * 0.9f));
        int centerX = ribbonWidth / 2;

        if (cfg.bgEffect == SonicPulseConfig.BgEffect.PULSE) {
            float mult = switch(cfg.pulseIntensity) { case SUBTLE -> 4.0f; case OVERDRIVE -> 24.0f; default -> 12.0f; };
            pulseLerp += (Math.min(spike * mult, 1.0f) - pulseLerp) * (cfg.pulseDecay == SonicPulseConfig.PulseDecay.SNAPPY ? 0.4f : 0.15f);
            int alpha = (int)(pulseLerp * 180);
            if (alpha > 5) context.fillGradient(0, 0, ribbonWidth, 35, ((alpha / 3) << 24) | (cfg.barColor & 0xFFFFFF), (alpha << 24) | (cfg.barColor & 0xFFFFFF));
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.AURA) {
            float vol = 0; for (float f : vData) vol += f; vol /= 16f;
            float timeDiv = switch(cfg.auraSpeed) { case CHILL -> 24000f; case WARP -> 4000f; default -> 12000f; };
            float time = (System.currentTimeMillis() % (long)timeDiv) / timeDiv;
            int alpha = 30 + (int)(Math.min(vol * 2.5f, 1.0f) * 110);
            int segments = 24; float segW = (float)ribbonWidth / segments;
            for (int s = 0; s < segments; s++) {
                float hL = 0.7f + (float)Math.sin(time * Math.PI * 2 + (s * 0.25f)) * 0.2f;
                float hR = 0.7f + (float)Math.sin(time * Math.PI * 2 + ((s+1) * 0.25f)) * 0.2f;
                if (cfg.auraPalette == SonicPulseConfig.AuraPalette.AURORA) { hL = 0.5f + (hL%1f)*0.35f; hR = 0.5f + (hR%1f)*0.35f; }
                context.fillGradient((int)(s*segW), 0, (int)((s+1)*segW), 35, (alpha << 24) | (hsbLookup(hL) & 0xFFFFFF), ((alpha/4) << 24) | (hsbLookup(hR) & 0xFFFFFF));
            }
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.VHS) {
            int scan = cfg.vhsScanlines == SonicPulseConfig.VhsScanlines.OFF ? 0 : (cfg.vhsScanlines == SonicPulseConfig.VhsScanlines.DARK ? 0x33000000 : 0x1A000000);
            if (scan != 0) for (int y = 0; y < 35; y += 2) context.fill(0, y, ribbonWidth, y + 1, scan);
            long now = System.currentTimeMillis();
            float thresh = switch(cfg.vhsGlitch) { case MINOR -> 0.18f; case CORRUPTED -> 0.08f; default -> 0.12f; };
            if (spike > thresh && now > vhsGlitchEndTime) {
                vhsGlitchBands = (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 4 : 2) + (int)(spike * 5);
                vhsGlitchEndTime = now + 60 + (long)(spike * 80);
                for (int i = 0; i < Math.min(vhsGlitchBands, 6); i++) {
                    vhsGlitchData[i][0] = (int)(Math.random()*35); vhsGlitchData[i][1] = 2+(int)(Math.random()*6);
                    vhsGlitchData[i][2] = (int)(Math.random()*20)-10; vhsGlitchData[i][3] = (int)(Math.random()*2);
                }
            }
            if (now <= vhsGlitchEndTime) {
                for (int i = 0; i < Math.min(vhsGlitchBands, 6); i++) {
                    int shift = vhsGlitchData[i][2];
                    context.fill(Math.max(0, shift), vhsGlitchData[i][0], Math.min(ribbonWidth, ribbonWidth + shift), vhsGlitchData[i][0] + vhsGlitchData[i][1], vhsGlitchData[i][3] == 0 ? 0x33FF0055 : 0x3300AAFF);
                }
            }
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.HEATMAP) {
            float vol = 0; for (float f : vData) vol += f; vol /= 16f;
            float target = (Math.min(vol * 1.5f, 0.3f) + Math.min(spike * 4f, 0.7f)) * (cfg.heatmapSpread == SonicPulseConfig.HeatmapSpread.CONFINED ? 0.45f : 1f);
            heatmapLerp += (target - heatmapLerp) * (target > heatmapLerp ? 0.3f : 0.05f);
            int slices = 30; float sliceW = (ribbonWidth / 2f) / slices;
            float cHue = switch(cfg.heatmapScale) { case PLASMA -> 0.85f; case TOXIC -> 0.3f; default -> 0f; };
            float eHue = switch(cfg.heatmapScale) { case PLASMA -> 0.5f; case TOXIC -> 0.75f; default -> 0.65f; };
            for (int s = 0; s < slices; s++) {
                float pct = (float)s / slices; float aPct = Math.max(0, 1f - (pct / Math.max(0.01f, heatmapLerp)));
                if (aPct <= 0.01f) continue;
                int color = ((int)(aPct * 160) << 24) | (hsbLookup(cHue + (eHue - cHue) * Math.min(1f, pct / Math.max(0.05f, heatmapLerp))) & 0xFFFFFF);
                context.fill((int)(centerX - (s+1)*sliceW), 0, (int)(centerX - s*sliceW), 35, color);
                context.fill((int)(centerX + s*sliceW), 0, (int)(centerX + (s+1)*sliceW), 35, color);
            }
        }
    }

    private void renderElements(DrawContext context, TextRenderer textRenderer, SonicPulseConfig cfg, AudioTrack track, int ribbonWidth, int slotWidth) {
        boolean playing = track != null;
        SonicPulseEngine engine = SonicPulseClient.getEngine();
        
        // VISUAL BUFFER BAR
        if (engine != null && engine.isBuffering() && cfg.showBufferingBar) {
            float progress = engine.getBufferProgress();
            context.fill(0, 34, (int)(ribbonWidth * progress), 35, 0xFF00FFFF); 
        }

        for (int i = 0; i < activeSlots.size(); i++) {
            int slotType = activeSlots.get(i);
            int slotCenterX = (i * slotWidth) + (slotWidth / 2);
            if (slotType == 1) { // LOGO
                context.drawText(textRenderer, LOGO_TEXT, slotCenterX - (textRenderer.getWidth(LOGO_TEXT) / 2), playing ? 6 : 14, cfg.titleColor | 0xFF000000, false);
                if (playing) {
                    if (track != cachedTrack) {
                        cachedTrack = track; String uri = track.getInfo().uri.toLowerCase();
                        if (uri.startsWith("file") || uri.matches("^[a-zA-Z]:\\\\.*") || cfg.activeMode == SonicPulseConfig.SessionMode.LOCAL) { cachedTagText = buildTag("📁", "LOCAL"); cachedTagColor = 0xFFFF00FF; }
                        else if (cfg.activeMode == SonicPulseConfig.SessionMode.RADIO) { cachedTagText = buildTag("📻", "RADIO"); cachedTagColor = 0xFF00FFFF; }
                        else { cachedTagText = buildTag("🌐", "REMOTE"); cachedTagColor = 0xFF00FFFF; }
                        cachedPosSeconds = -1;
                    }
                    long cur = track.getPosition() / 1000;
                    if (cur != cachedPosSeconds) {
                        cachedPosSeconds = cur;
                        cachedTimeText = track.getInfo().isStream ? style("") : style(String.format("  %02d:%02d / %02d:%02d", cur/60, cur%60, (track.getDuration()/1000)/60, (track.getDuration()/1000)%60));
                    }
                    int tW = textRenderer.getWidth(cachedTagText); int subX = slotCenterX - ((tW + textRenderer.getWidth(cachedTimeText)) / 2);
                    context.drawText(textRenderer, cachedTagText, subX, 18, cachedTagColor, false);
                    context.drawText(textRenderer, cachedTimeText, subX + tW, 18, 0xFFFFFFFF, false);
                }
            } else if (slotType == 2 && (playing || (engine != null && engine.isBuffering()))) { // TRACK
                String name = (cfg.currentTitle != null) ? cfg.currentTitle : (track != null ? track.getInfo().title : "Buffering Stream...");
                if (name != null) {
                    if (!name.equals(rawTrackNameCache)) {
                        rawTrackNameCache = name; safeTrackNameCache = name.replace("|", " - ");
                        marqueeCache = safeTrackNameCache + "   •   " + safeTrackNameCache + "   •   ";
                        marqueeLenCache = safeTrackNameCache.length() + 7;
                    }
                    float tScale = 1.35f; int maxW = slotWidth - 20; int sTxtW = (int)(textRenderer.getWidth(safeTrackNameCache) * tScale);
                    context.getMatrices().push();
                    if (sTxtW > maxW) {
                        if (System.currentTimeMillis() - lastMarqueeUpdate > 100) {
                            int offset = (int)((System.currentTimeMillis() / 150) % marqueeLenCache);
                            currentScrolledText = textRenderer.trimToWidth(marqueeCache.substring(offset), (int)(maxW/tScale));
                            lastMarqueeUpdate = System.currentTimeMillis();
                        }
                        context.getMatrices().translate(slotCenterX - (maxW/2f), 11, 0); context.getMatrices().scale(tScale, tScale, 1f);
                        context.drawText(textRenderer, style(currentScrolledText), 0, 0, 0xFFFFFFFF, false);
                    } else {
                        context.getMatrices().translate(slotCenterX - (sTxtW/2f), 11, 0); context.getMatrices().scale(tScale, tScale, 1f);
                        context.drawText(textRenderer, style(safeTrackNameCache), 0, 0, 0xFFFFFFFF, false);
                    }
                    context.getMatrices().pop();
                }
            } else if (slotType == 3) { // BARS
                float[] vData = engine != null ? engine.getVisualizerData() : null;
                int bCount = 16, bW = 6, bS = 2; int startX = slotCenterX - ((bCount * (bW + bS) - bS) / 2);
                for (int j = 0; j < bCount; j++) {
                    float amp = (vData != null && vData.length > j) ? vData[j] : 0f;
                    int bH = Math.max((int)(amp * 25), 1);
                    context.fill(startX + j*(bW+bS), 30 - bH, startX + j*(bW+bS) + bW, 30, cfg.barColor | 0xFF000000);
                    if (cfg.visStyle == SonicPulseConfig.VisualizerStyle.FLOATING_PEAKS) {
                        floatingPeaks[j] = amp >= floatingPeaks[j] ? amp : Math.max(amp, floatingPeaks[j] - 0.005f);
                        int pH = Math.max((int)(floatingPeaks[j] * 25), 1);
                        context.fill(startX + j*(bW+bS), 30 - pH - 2, startX + j*(bW+bS) + bW, 30 - pH - 1, 0xFFFFFFFF);
                    }
                }
            }
        }
    }
}