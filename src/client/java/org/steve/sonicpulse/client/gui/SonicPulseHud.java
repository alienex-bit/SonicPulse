package org.steve.sonicpulse.client.gui;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

@SuppressWarnings("deprecation")
public class SonicPulseHud implements HudRenderCallback {
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        render(context);
    }

    private void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;

        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        if (track == null) return;

        SonicPulseConfig config = SonicPulseConfig.get();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        
        int hudW = 140; 
        int hudH = 55; 
        
        int x = config.hudX >= 0 ? config.hudX : width + config.hudX - (int)(hudW * config.hudScale);
        int y = config.hudY >= 0 ? config.hudY : height + config.hudY - (int)(hudH * config.hudScale);

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(config.hudScale, config.hudScale, 1.0f);

        // 1. Background
        context.fill(0, 0, hudW, hudH, config.skin.getBgColor());
        context.drawBorder(0, 0, hudW, hudH, config.skin.getBorderColor());

        // 2. Visualizer - resample source data into TARGET_BARS bands using cheap interpolation
        float[] v = SonicPulseClient.getEngine().getVisualizerData();
        final int TARGET_BARS = 16;
        float[] aggr = new float[TARGET_BARS];
        // If no source data available, keep aggr as zeros; otherwise sample/interpolate per bar
        if (v != null && v.length > 0) {
            int sourceLen = v.length;
            // cheap linear-resample: pick a fractional position in source for each bar and lerp
            for (int b = 0; b < TARGET_BARS; b++) {
                float pos = (b + 0.5f) * sourceLen / (float)TARGET_BARS; // center of the target bin
                int idx = (int)Math.floor(pos);
                float frac = pos - idx;
                if (idx <= 0) {
                    aggr[b] = v[0];
                } else if (idx >= sourceLen - 1) {
                    aggr[b] = v[sourceLen - 1];
                } else {
                    float a = v[idx];
                    float bb = v[idx + 1];
                    aggr[b] = a * (1f - frac) + bb * frac;
                }
            }
        }

        // Render bars now (so they are behind text) - increase height multiplier for stronger visuals
        int bars = Math.min(TARGET_BARS, aggr.length);
        int barW = Math.max(1, (hudW - 10) / bars);
        int barStartY = hudH - 6;
        long now = System.currentTimeMillis();
        float phase = (now % 2000L) / 2000f;
        int maxBarCap = Math.max(8, hudH - 14); // allow bars to use most of the HUD height
        for (int i = 0; i < bars; i++) {
            float norm = Math.max(0f, Math.min(1f, aggr[i]));
            int[] cols = computeBarColors(config, i, bars, norm, phase);
            int topCol = cols[1];
            // stronger scaling than before
            int bh = (int)(norm * maxBarCap * 1.2f);
            if (bh > maxBarCap) bh = maxBarCap;
            if (bh > 0) context.fill(5 + i * barW, barStartY - bh, 5 + i * barW + (barW - 1), barStartY, topCol);
            if (config.colorMode == SonicPulseConfig.ColorMode.NEON_OUTLINE) {
                int neon = (topCol & 0x00FFFFFF) | (0xC0 << 24);
                int left = 5 + i * barW; int right = 5 + i * barW + (barW - 1);
                context.fill(left, barStartY - 1 - Math.max(1, (int)(norm * maxBarCap)), right, barStartY - Math.max(1, (int)(norm * maxBarCap)), neon);
            }
        }

        // 3. Title Logic (compute text but draw after bars)
        String displayText;
        if (config.currentTitle != null && !config.currentTitle.isEmpty()) {
            displayText = config.currentTitle; // The Forced Title
        } else {
            if (track.getInfo().author != null && !track.getInfo().author.equals("Unknown")) {
                displayText = track.getInfo().author + " - " + track.getInfo().title;
            } else {
                displayText = track.getInfo().title;
            }
        }
        if (displayText == null || displayText.trim().isEmpty()) displayText = "Streaming...";

        // 4. Header (title uses configurable titleColor) and draw computed text on top of bars
        context.drawText(client.textRenderer, "SONICPULSE", 5, 5, config.titleColor, true);
        int padding = 10; // left+right padding (5 each)
        int maxTextWidth = hudW - padding;
        String drawText = displayText;
        int textWidth = client.textRenderer.getWidth(drawText);
        if (textWidth > maxTextWidth) {
            String ell = "...";
            int ellW = client.textRenderer.getWidth(ell);
            int allowed = Math.max(0, maxTextWidth - ellW);
            int low = 0, high = drawText.length();
            while (low < high) {
                int mid = (low + high + 1) / 2;
                String sub = drawText.substring(0, mid);
                if (client.textRenderer.getWidth(sub) <= allowed) low = mid; else high = mid - 1;
            }
            drawText = drawText.substring(0, low) + ell;
        }
        context.drawText(client.textRenderer, drawText, 5, 20, 0xFFFFFFFF, true);

        context.getMatrices().pop();
    }

    // Compute base and top colors for a bar depending on current ColorMode
    private static int[] computeBarColors(SonicPulseConfig config, int index, int bars, float norm, float phase) {
        int cfg = config.barColor & 0x00FFFFFF;
        int alpha = 0x99; // ~60% opacity for bars
        switch (config.colorMode) {
            case RAINBOW: {
                float hue = (index / (float)Math.max(1, bars) + phase) % 1f;
                int top = hsvToRgb(alpha, hue, 0.95f, 1.0f);
                int base = hsvToRgb(alpha, hue, 0.65f, 0.85f);
                return new int[]{base, top};
            }
            case MATRIX: {
                int g = Math.min(255, Math.max(16, (int)(40 + norm * 215)));
                int top = (alpha << 24) | (0x00 << 16) | (g << 8) | 0x00;
                int base = (alpha << 24) | (0x00 << 16) | (Math.min(255, g + 60) << 8) | 0x00;
                return new int[]{base, top};
            }
            case HEATMAP: {
                float hue = (1f - norm) * 0.66f; // blue->red
                int top = hsvToRgb(alpha, hue, 1f, 1f);
                return new int[]{top, top};
            }
            case VAPORWAVE: {
                float hue = (0.6f + index / (float)bars * 0.2f + phase * 0.05f) % 1f;
                int top = hsvToRgb(alpha, hue, 0.55f, 0.95f);
                int base = blendWithWhite(top, 0.25f) & 0x00FFFFFF | (alpha << 24);
                return new int[]{base, top};
            }
            case HORIZONTAL: {
                float t = index / (float)Math.max(1, bars - 1);
                int top = blendWithWhite(0xFF000000 | cfg, Math.min(0.9f, 0.25f + t * 0.4f));
                top = (alpha << 24) | (top & 0x00FFFFFF);
                return new int[]{(alpha << 24) | cfg, top};
            }
            case PULSING_DUAL: {
                int br = Math.max(0, ((cfg >> 16) & 0xFF) - 30);
                int bg = Math.max(0, ((cfg >> 8) & 0xFF) - 30);
                int bb = Math.max(0, (cfg & 0xFF) - 30);
                int base = (alpha << 24) | (br << 16) | (bg << 8) | bb;
                float p = Math.min(1f, 0.25f + norm * 0.9f);
                int accent = blendWithWhite(0xFF000000 | cfg, p) & 0x00FFFFFF;
                int top = (alpha << 24) | accent;
                return new int[]{base, top};
            }
            case NEON_OUTLINE: {
                int br = Math.max(0, ((cfg >> 16) & 0xFF) - 40);
                int bg = Math.max(0, ((cfg >> 8) & 0xFF) - 40);
                int bb = Math.max(0, (cfg & 0xFF) - 40);
                int base = (alpha << 24) | (br << 16) | (bg << 8) | bb;
                int neon = blendWithWhite(0xFF000000 | cfg, 0.6f) & 0x00FFFFFF;
                int top = (alpha << 24) | neon;
                return new int[]{base, top};
            }
            case SOLID:
            default: {
                int top = (alpha << 24) | cfg;
                int base = blendWithWhite(top, 0.35f) & 0x00FFFFFF | (alpha << 24);
                return new int[]{base, top};
            }
        }
    }

    private static int blendWithWhite(int color, float t) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int nr = Math.min(255, (int)(r + (255 - r) * t));
        int ng = Math.min(255, (int)(g + (255 - g) * t));
        int nb = Math.min(255, (int)(b + (255 - b) * t));
        return (0xFF << 24) | (nr << 16) | (ng << 8) | nb;
    }

    private static int hsvToRgb(int alpha, float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        if (s == 0f) { r = g = b = v; }
        else {
            float hf = h * 6f;
            int i = (int)Math.floor(hf);
            float f = hf - i;
            float p = v * (1f - s);
            float q = v * (1f - s * f);
            float t = v * (1f - s * (1f - f));
            switch (i % 6) {
                case 0: r = v; g = t; b = p; break;
                case 1: r = q; g = v; b = p; break;
                case 2: r = p; g = v; b = t; break;
                case 3: r = p; g = q; b = v; break;
                case 4: r = t; g = p; b = v; break;
                case 5: r = v; g = p; b = q; break;
            }
        }
        int ri = Math.min(255, Math.max(0, (int)(r * 255f)));
        int gi = Math.min(255, Math.max(0, (int)(g * 255f)));
        int bi = Math.min(255, Math.max(0, (int)(b * 255f)));
        return (alpha & 0xFF) << 24 | (ri << 16) | (gi << 8) | bi;
    }

}
