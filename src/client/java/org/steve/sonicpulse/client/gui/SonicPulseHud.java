package org.steve.sonicpulse.client.gui;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import org.steve.sonicpulse.client.engine.SonicPulseEngine;
import java.util.ArrayList;
import java.util.List;

public class SonicPulseHud {
    private final float[] floatingPeaks = new float[32]; // expanded for 32-bar visualizer
    private static final Style ROBOTO = Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("sonicpulse", "roboto")));
    private static final Component LOGO_BASE = Component.literal("SONICPULSE ").setStyle(ROBOTO);

    private AudioTrack cachedTrack = null;
    private int cachedTagColor = 0xFF00FFFF;
    private long cachedPosSeconds = -1;
    private Component cachedTimeText = Component.empty(), cachedTimeSuffix = Component.empty(), cachedTagText = Component.empty();

    private String rawTrackNameCache = null, safeTrackNameCache = null, marqueeCache = null, currentScrolledText = "";
    private int marqueeLenCache = 0;
    private long lastMarqueeUpdate = 0;

    private float baselineBass = 0, pulseLerp = 0, heatmapLerp = 0;
    private final List<Integer> activeSlots = new ArrayList<>();
    private final int[][] vhsGlitchData = new int[6][4];
    private long vhsGlitchEndTime = 0;
    private int vhsGlitchBands = 0;

    // STARFIELD particle state (max 48 particles recycled)
    private static final int MAX_SPARKS = 48;
    private final float[] sparkX = new float[MAX_SPARKS];
    private final float[] sparkY = new float[MAX_SPARKS];
    private final float[] sparkVX = new float[MAX_SPARKS];
    private final float[] sparkVY = new float[MAX_SPARKS];
    private final float[] sparkLife = new float[MAX_SPARKS]; // 0=dead, 1=fresh
    private int sparkNext = 0;
    private float sparkBaseline = 0;
    private long lastSparkTime = 0;

    private static final int HSB_LUT_SIZE = 256;
    private static final int[] HSB_LUT = new int[HSB_LUT_SIZE];
    static {
        for (int i = 0; i < HSB_LUT_SIZE; i++) {
            float hue = i / (float) HSB_LUT_SIZE;
            float s = 0.85f, b = 1.0f;
            int hi = (int) (hue * 6) % 6;
            float f = hue * 6 - (int) (hue * 6);
            float p = b * (1 - s), q = b * (1 - f * s), t = b * (1 - (1 - f) * s);
            float r, g, bl;
            switch (hi) {
                case 0:
                    r = b;
                    g = t;
                    bl = p;
                    break;
                case 1:
                    r = q;
                    g = b;
                    bl = p;
                    break;
                case 2:
                    r = p;
                    g = b;
                    bl = t;
                    break;
                case 3:
                    r = p;
                    g = q;
                    bl = b;
                    break;
                case 4:
                    r = t;
                    g = p;
                    bl = b;
                    break;
                default:
                    r = b;
                    g = p;
                    bl = q;
                    break;
            }
            HSB_LUT[i] = (((int) (r * 255)) << 16) | (((int) (g * 255)) << 8) | ((int) (bl * 255));
        }
    }

    private static int hsbLookup(float hue) {
        return HSB_LUT[(int) (((hue % 1.0f + 1.0f) % 1.0f) * HSB_LUT_SIZE) % HSB_LUT_SIZE];
    }

    private Component style(String text) {
        return Component.literal(text).setStyle(ROBOTO);
    }

    private Component buildTag(String icon, String name) {
        return Component.empty().append(Component.literal("[ ").setStyle(ROBOTO)).append(Component.literal(icon))
                .append(Component.literal(" " + name + " ]").setStyle(ROBOTO));
    }

    public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        render(context, false, 0, 0);
    }

    public void render(GuiGraphicsExtractor context, boolean isPreview, int previewX, int previewY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui.hud.isHidden())
            return;
        SonicPulseConfig cfg = SonicPulseConfig.get(); // fetch once for the whole frame
        if (!cfg.hudVisible || (!isPreview && client.gui.screen() != null))
            return;

        SonicPulseEngine engine = SonicPulseClient.getEngine();
        AudioTrack track = (engine != null && engine.getPlayer() != null) ? engine.getPlayer().getPlayingTrack() : null;
        if (track == null && !isPreview && (engine == null || !engine.isBuffering()))
            return;

        int screenW = client.getWindow().getGuiScaledWidth();
        float scale = cfg.hudScale;

        activeSlots.clear();
        int[] seq = switch (cfg.ribbonLayout) {
            case LOG_TRK_BAR -> new int[] { 1, 2, 3 };
            case LOG_BAR_TRK -> new int[] { 1, 3, 2 };
            case TRK_LOG_BAR -> new int[] { 2, 1, 3 };
            case TRK_BAR_LOG -> new int[] { 2, 3, 1 };
            case BAR_LOG_TRK -> new int[] { 3, 1, 2 };
            case BAR_TRK_LOG -> new int[] { 3, 2, 1 };
        };
        for (int i : seq) {
            activeSlots.add(i);
        }

        int ribbonWidth = Math.max(50, (int) ((screenW / scale) * cfg.hudWidth));
        int slotWidth = ribbonWidth / Math.max(1, activeSlots.size());

        context.pose().pushMatrix();
        context.pose().scale(scale, scale);
        context.pose().translate(((screenW / scale) - ribbonWidth) / 2.0f, 0.0f);
        context.fill(0, 0, ribbonWidth, 35, cfg.skin.getBgColor());
        // Top + bottom border
        context.fill(0, 0, ribbonWidth, 1, cfg.skin.getBorderColor());

        // Clone visualizer data once for the whole frame — shared by effects and bars
        float[] vData = (engine != null) ? engine.getVisualizerData() : null;
        float bass = 0, spike = 0;
        if (vData != null && vData.length >= 3) {
            bass = (vData[0] + vData[1] + vData[2]) / 3.0f;
            baselineBass += (bass - baselineBass) * 0.05f;
            spike = Math.max(0, bass - (baselineBass * 0.9f));
        }

        if (vData != null && vData.length >= 16)
            renderEffects(context, cfg, vData, ribbonWidth, bass, spike);

        context.fill(0, 34, ribbonWidth, 35, cfg.skin.getBorderColor());
        renderElements(context, client.font, cfg, track, engine, vData, ribbonWidth, slotWidth, bass, spike);
        context.pose().popMatrix();
    }

    private void renderEffects(GuiGraphicsExtractor context, SonicPulseConfig cfg, float[] vData, int ribbonWidth, float bass,
            float spike) {
        int centerX = ribbonWidth / 2;

        if (cfg.bgEffect == SonicPulseConfig.BgEffect.PULSE) {
            float mult = switch (cfg.pulseIntensity) {
                case SUBTLE -> 4.0f;
                case OVERDRIVE -> 24.0f;
                default -> 12.0f;
            };
            pulseLerp += (Math.min(spike * mult, 1.0f) - pulseLerp)
                    * (cfg.pulseDecay == SonicPulseConfig.PulseDecay.SNAPPY ? 0.4f : 0.15f);
            int alpha = (int) (pulseLerp * 180);
            if (alpha > 5)
                context.fillGradient(0, 0, ribbonWidth, 35, ((alpha / 3) << 24) | (cfg.barColor & 0xFFFFFF),
                        (alpha << 24) | (cfg.barColor & 0xFFFFFF));
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.AURA) {
            float vol = 0;
            for (float f : vData)
                vol += f;
            vol /= 16f;
            float timeDiv = switch (cfg.auraSpeed) {
                case CHILL -> 24000f;
                case WARP -> 4000f;
                default -> 12000f;
            };
            float time = (System.currentTimeMillis() % (long) timeDiv) / timeDiv;
            int alpha = 30 + (int) (Math.min(vol * 2.5f, 1.0f) * 110);
            int segments = 24;
            float segW = (float) ribbonWidth / segments;
            for (int s = 0; s < segments; s++) {
                float hL = 0.7f + (float) Math.sin(time * Math.PI * 2 + (s * 0.25f)) * 0.2f;
                float hR = 0.7f + (float) Math.sin(time * Math.PI * 2 + ((s + 1) * 0.25f)) * 0.2f;
                if (cfg.auraPalette == SonicPulseConfig.AuraPalette.AURORA) {
                    hL = 0.5f + (hL % 1f) * 0.35f;
                    hR = 0.5f + (hR % 1f) * 0.35f;
                }
                context.fillGradient((int) (s * segW), 0, (int) ((s + 1) * segW), 35,
                        (alpha << 24) | (hsbLookup(hL) & 0xFFFFFF), ((alpha / 4) << 24) | (hsbLookup(hR) & 0xFFFFFF));
            }
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.VHS) {
            int scan = cfg.vhsScanlines == SonicPulseConfig.VhsScanlines.OFF ? 0
                    : (cfg.vhsScanlines == SonicPulseConfig.VhsScanlines.DARK ? 0x33000000 : 0x1A000000);
            if (scan != 0)
                for (int y = 0; y < 35; y += 2)
                    context.fill(0, y, ribbonWidth, y + 1, scan);
            long now = System.currentTimeMillis();
            float thresh = switch (cfg.vhsGlitch) {
                case MINOR -> 0.18f;
                case CORRUPTED -> 0.08f;
                default -> 0.12f;
            };
            if (spike > thresh && now > vhsGlitchEndTime) {
                vhsGlitchBands = (cfg.vhsGlitch == SonicPulseConfig.VhsGlitch.CORRUPTED ? 4 : 2) + (int) (spike * 5);
                vhsGlitchEndTime = now + 60 + (long) (spike * 80);
                for (int i = 0; i < Math.min(vhsGlitchBands, 6); i++) {
                    vhsGlitchData[i][0] = (int) (Math.random() * 35);
                    vhsGlitchData[i][1] = 2 + (int) (Math.random() * 6);
                    vhsGlitchData[i][2] = (int) (Math.random() * 20) - 10;
                    vhsGlitchData[i][3] = (int) (Math.random() * 2);
                }
            }
            if (now <= vhsGlitchEndTime) {
                for (int i = 0; i < Math.min(vhsGlitchBands, 6); i++) {
                    int shift = vhsGlitchData[i][2];
                    context.fill(Math.max(0, shift), vhsGlitchData[i][0], Math.min(ribbonWidth, ribbonWidth + shift),
                            vhsGlitchData[i][0] + vhsGlitchData[i][1],
                            vhsGlitchData[i][3] == 0 ? 0x33FF0055 : 0x3300AAFF);
                }
            }
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.HEATMAP) {
            float vol = 0;
            for (float f : vData)
                vol += f;
            vol /= 16f;
            float target = (Math.min(vol * 1.5f, 0.3f) + Math.min(spike * 4f, 0.7f))
                    * (cfg.heatmapSpread == SonicPulseConfig.HeatmapSpread.CONFINED ? 0.45f : 1f);
            heatmapLerp += (target - heatmapLerp) * (target > heatmapLerp ? 0.3f : 0.05f);
            int slices = 30;
            float sliceW = (ribbonWidth / 2f) / slices;
            float cHue = switch (cfg.heatmapScale) {
                case PLASMA -> 0.85f;
                case TOXIC -> 0.3f;
                default -> 0f;
            };
            float eHue = switch (cfg.heatmapScale) {
                case PLASMA -> 0.5f;
                case TOXIC -> 0.75f;
                default -> 0.65f;
            };
            for (int s = 0; s < slices; s++) {
                float pct = (float) s / slices;
                float aPct = Math.max(0, 1f - (pct / Math.max(0.01f, heatmapLerp)));
                if (aPct <= 0.01f)
                    continue;
                int color = ((int) (aPct * 160) << 24)
                        | (hsbLookup(cHue + (eHue - cHue) * Math.min(1f, pct / Math.max(0.05f, heatmapLerp)))
                                & 0xFFFFFF);
                context.fill((int) (centerX - (s + 1) * sliceW), 0, (int) (centerX - s * sliceW), 35, color);
                context.fill((int) (centerX + s * sliceW), 0, (int) (centerX + (s + 1) * sliceW), 35, color);
            }
        } else if (cfg.bgEffect == SonicPulseConfig.BgEffect.STARFIELD) {
            sparkBaseline += (bass - sparkBaseline) * 0.05f;
            float sSpike = Math.max(0, bass - (sparkBaseline * 0.85f));
            long now = System.currentTimeMillis();
            if (sSpike > 0.08f && now - lastSparkTime > 80) {
                lastSparkTime = now;
                int count = cfg.sparkCount == SonicPulseConfig.SparkCount.FEW ? 6
                        : cfg.sparkCount == SonicPulseConfig.SparkCount.STORM ? 24 : 12;
                float cx = ribbonWidth / 2f, cy = 17f;
                for (int s = 0; s < count; s++) {
                    double angle = Math.random() * 2 * Math.PI;
                    float speed = 0.15f + (float) Math.random() * 0.2f;
                    sparkX[sparkNext] = cx;
                    sparkY[sparkNext] = cy;
                    sparkVX[sparkNext] = (float) Math.cos(angle) * speed;
                    sparkVY[sparkNext] = (float) Math.sin(angle) * speed * 0.5f;
                    sparkLife[sparkNext] = 1.0f;
                    sparkNext = (sparkNext + 1) % MAX_SPARKS;
                }
            }
            float decay = cfg.sparkDecay == SonicPulseConfig.SparkDecay.SNAP ? 0.08f : 0.02f;
            int barRgb = cfg.barColor & 0xFFFFFF;
            for (int s = 0; s < MAX_SPARKS; s++) {
                if (sparkLife[s] <= 0.01f)
                    continue;
                sparkVX[s] *= 1.07f;
                sparkVY[s] *= 1.07f;
                sparkX[s] += sparkVX[s];
                sparkY[s] += sparkVY[s];
                sparkLife[s] = Math.max(0f, sparkLife[s] - decay);

                int alpha = (int) (sparkLife[s] * 220);
                int color = (alpha << 24) | barRgb;
                int px = (int) sparkX[s], py = (int) sparkY[s];
                if (px >= 0 && px < ribbonWidth && py >= 1 && py < 34)
                    context.fill(px, py, px + 2, py + 2, color);
            }
        }
    }

    private void renderElements(GuiGraphicsExtractor context, Font font, SonicPulseConfig cfg, AudioTrack track,
            SonicPulseEngine engine, float[] vData, int ribbonWidth, int slotWidth, float bass,
            float spike) {
        boolean playing = track != null;
        long now = System.currentTimeMillis();

        if (engine != null && engine.isBuffering() && cfg.showBufferingBar) {
            float progress = engine.getBufferProgress();
            context.fill(0, 33, (int) (ribbonWidth * progress), 34, 0xFF00FFFF);
        }

        for (int i = 0; i < activeSlots.size(); i++) {
            int slotType = activeSlots.get(i);
            int slotCenterX = (i * slotWidth) + (slotWidth / 2);
            if (slotType == 1 && cfg.showLogo) { // LOGO
                int noteAlpha = Math.min(255, 140 + (int) (bass * 115));
                int noteColor = (noteAlpha << 24) | (cfg.barColor & 0xFFFFFF);
                int logoY = playing ? 6 : 14;
                int logoX = slotCenterX - (font.width(LOGO_BASE) / 2) - 4;
                context.text(font, LOGO_BASE, logoX, logoY, cfg.titleColor | 0xFF000000, false);
                context.text(font, Component.literal("♫"), logoX + font.width(LOGO_BASE), logoY,
                        noteColor, false);
                if (playing) {
                    if (track != cachedTrack) {
                        cachedTrack = track;
                        String uri = track.getInfo().uri.toLowerCase();
                        if (uri.startsWith("file") || uri.matches("^[a-zA-Z]:\\\\.*")
                                || cfg.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
                            cachedTagText = buildTag("📁", "LOCAL");
                            cachedTagColor = 0xFFFF00FF;
                        } else if (cfg.activeMode == SonicPulseConfig.SessionMode.RADIO) {
                            cachedTagText = buildTag("📻", "RADIO");
                            cachedTagColor = 0xFF00FFFF;
                        } else {
                            cachedTagText = buildTag("🌐", "REMOTE");
                            cachedTagColor = 0xFF00FFFF;
                        }
                        cachedPosSeconds = -1;
                    }
                    long cur = track.getPosition() / 1000;
                    if (cur != cachedPosSeconds) {
                        cachedPosSeconds = cur;
                        if (track.getInfo().isStream) {
                            cachedTimeText = style("");
                            cachedTimeSuffix = Component.empty();
                        } else {
                            cachedTimeText = style(String.format("  %02d:%02d", cur / 60, cur % 60));
                            cachedTimeSuffix = style(String.format(" / %02d:%02d",
                                    (track.getDuration() / 1000) / 60, (track.getDuration() / 1000) % 60));
                        }
                    }
                    int tW = font.width(cachedTagText);
                    int timeW = font.width(cachedTimeText) + font.width(cachedTimeSuffix);
                    int subX = slotCenterX - ((tW + timeW) / 2);
                    context.text(font, cachedTagText, subX, 18, cachedTagColor, false);
                    int posX = subX + tW;
                    context.text(font, cachedTimeText, posX, 18, 0xFFFFFFFF, false);
                    context.text(font, cachedTimeSuffix, posX + font.width(cachedTimeText), 18,
                            0xFFAAAAAA, false);
                }
            } else if (slotType == 2 && cfg.showTrack && (playing || (engine != null && engine.isBuffering()))) { // TRACK
                String baseName = (cfg.currentTitle != null) ? cfg.currentTitle
                        : (track != null ? track.getInfo().title : "Loading...");

                // TWO-LINE BUFFERING DISPLAY
                if (engine != null && engine.isBuffering()) {
                    int pct = (int) (engine.getBufferProgress() * 100);
                    String topText = "§eBuffering... " + pct + "%";
                    String bottomText = font.plainSubstrByWidth(baseName, slotWidth - 10);

                    int tW = font.width(topText);
                    int bW = font.width(bottomText);

                    context.text(font, style(topText), slotCenterX - (tW / 2), 6, 0xFFFFFFFF, false);
                    context.text(font, style("§7" + bottomText), slotCenterX - (bW / 2), 18, 0xFFFFFFFF,
                            false);
                }
                // STANDARD MARQUEE ONCE PLAYING
                else if (baseName != null) {
                    if (!baseName.equals(rawTrackNameCache)) {
                        rawTrackNameCache = baseName;
                        safeTrackNameCache = baseName.replace("|", " - ");
                        marqueeCache = safeTrackNameCache + "   •   " + safeTrackNameCache + "   •   ";
                        marqueeLenCache = safeTrackNameCache.length() + 7;
                    }
                    float tScale = 1.35f;
                    int maxW = slotWidth - 20;
                    int sTxtW = (int) (font.width(safeTrackNameCache) * tScale);
                    context.pose().pushMatrix();
                    if (sTxtW > maxW) {
                        if (now - lastMarqueeUpdate > 100) {
                            int offset = (int) ((now / 150) % marqueeLenCache);
                            currentScrolledText = font.plainSubstrByWidth(marqueeCache.substring(offset),
                                    (int) (maxW / tScale));
                            lastMarqueeUpdate = now;
                        }
                        context.pose().translate(slotCenterX - (maxW / 2f), 11);
                        context.pose().scale(tScale, tScale);
                        context.text(font, style(currentScrolledText), 0, 0, 0xFFFFFFFF, false);
                    } else {
                        context.pose().translate(slotCenterX - (sTxtW / 2f), 11);
                        context.pose().scale(tScale, tScale);
                        context.text(font, style(safeTrackNameCache), 0, 0, 0xFFFFFFFF, false);
                    }
                    context.pose().popMatrix();
                }
            } else if (slotType == 3 && cfg.showBars) { // BARS
                int bCount = 32, bW = 4, bS = 1;
                int startX = slotCenterX - ((bCount * (bW + bS) - bS) / 2);
                int barColorOpaque = cfg.barColor | 0xFF000000;
                int barColorDim = (cfg.barColor & 0xFFFFFF) | 0x88000000;
                for (int j = 0; j < bCount; j++) {
                    float amp = (vData != null && vData.length > j) ? vData[j] : 0f;
                    int bH = Math.max((int) (amp * 25), 1);
                    int x0 = startX + j * (bW + bS);
                    context.fillGradient(x0, 30 - bH, x0 + bW, 30, barColorOpaque, barColorDim);
                    if (cfg.visStyle == SonicPulseConfig.VisualizerStyle.FLOATING_PEAKS) {
                        floatingPeaks[j] = amp >= floatingPeaks[j] ? amp : Math.max(amp, floatingPeaks[j] - 0.005f);
                        int pH = Math.max((int) (floatingPeaks[j] * 25), 1);
                        context.fill(x0, 30 - pH - 2, x0 + bW, 30 - pH - 1, cfg.titleColor | 0xFF000000);
                    }
                }
            }
        }
    }
}