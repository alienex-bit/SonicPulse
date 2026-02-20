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
        
        int hudW = 140, hudH = 55; 
        int x = config.hudX >= 0 ? config.hudX : width + config.hudX - (int)(hudW * config.hudScale);
        int y = config.hudY >= 0 ? config.hudY : height + config.hudY - (int)(hudH * config.hudScale);

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(config.hudScale, config.hudScale, 1.0f);

        // 1. Background
        context.fill(0, 0, hudW, hudH, config.skin.getBgColor());
        context.drawBorder(0, 0, hudW, hudH, config.skin.getBorderColor());

        // 2. Visualizer Data
        float[] v = SonicPulseClient.getEngine().getVisualizerData();
        final int TARGET_BARS = 16;
        float[] aggr = new float[TARGET_BARS];
        if (v != null && v.length >= 32) {
            for (int b = 0; b < TARGET_BARS; b++) aggr[b] = (v[b * 2] + v[b * 2 + 1]) / 2.0f;
        }

        int barW = (hudW - 10) / TARGET_BARS;
        int barBottomY = hudH - 6;
        int maxH = hudH - 32; // Limit height so it doesn't hit the title text
        float phase = (System.currentTimeMillis() % 2000L) / 2000f;

        for (int i = 0; i < TARGET_BARS; i++) {
            float norm = Math.max(0.02f, aggr[i]);
            int col = computeBarColors(config, i, TARGET_BARS, norm, phase)[1];
            int bh = (int)(norm * maxH);
            int bx = 5 + i * barW;
            int bw = barW - 2;

            switch (config.visStyle) {
                case SEGMENTED:
                    int segs = 5;
                    for (int s = 0; s < segs; s++) {
                        if (norm > (float)s / segs) {
                            int sY2 = barBottomY - s * (maxH / segs);
                            int sY1 = sY2 - (maxH / segs) + 1;
                            context.fill(bx, sY1, bx + bw, sY2, col);
                        }
                    }
                    break;
                case MIRRORED:
                    int mid = barBottomY - (maxH / 2);
                    context.fill(bx, mid - (bh / 2), bx + bw, mid + (bh / 2), col);
                    break;
                case WAVEFORM:
                    int waveMid = barBottomY - (maxH / 2);
                    context.fill(bx, waveMid - bh, bx + bw, waveMid + bh, col);
                    context.fill(bx, waveMid, bx + bw, waveMid + 1, 0x88FFFFFF); // Center line
                    break;
                case PEAK_DOTS:
                    context.fill(bx, barBottomY - bh - 2, bx + bw, barBottomY - bh, col); // The Dot
                    context.fill(bx, barBottomY - bh, bx + bw, barBottomY, (col & 0x00FFFFFF) | (0x33 << 24)); // Ghost bar
                    break;
                case SOLID:
                default:
                    context.fill(bx, barBottomY - bh, bx + bw, barBottomY, col);
                    break;
            }
        }

        // 3. Text
        context.drawText(client.textRenderer, "SONICPULSE", 5, 5, config.titleColor, true);
        String title = (config.currentTitle != null) ? config.currentTitle : track.getInfo().title;
        context.drawText(client.textRenderer, client.textRenderer.trimToWidth(title, hudW - 10), 5, 18, 0xFFFFFFFF, true);

        context.getMatrices().pop();
    }

    private static int[] computeBarColors(SonicPulseConfig config, int index, int bars, float norm, float phase) {
        int cfg = config.barColor & 0x00FFFFFF;
        int alpha = 0xBB;
        switch (config.colorMode) {
            case RAINBOW: 
                float h = (index / (float)bars + phase) % 1f;
                int c = hsvToRgb(alpha, h, 0.9f, 1.0f);
                return new int[]{c, c};
            case MATRIX:
                int g = Math.min(255, (int)(50 + norm * 205));
                int mc = (alpha << 24) | (g << 8);
                return new int[]{mc, mc};
            default:
                int sc = (alpha << 24) | cfg;
                return new int[]{sc, sc};
        }
    }

    private static int hsvToRgb(int a, float h, float s, float v) {
        float r=0, g=0, b=0;
        float hf=h*6f; int i=(int)Math.floor(hf); float f=hf-i;
        float p=v*(1f-s), q=v*(1f-s*f), t=v*(1f-s*(1f-f));
        switch(i%6){
            case 0: r=v; g=t; b=p; break; case 1: r=q; g=v; b=p; break;
            case 2: r=p; g=v; b=t; break; case 3: r=p; g=q; b=v; break;
            case 4: r=t; g=p; b=v; break; case 5: r=v; g=p; b=q; break;
        }
        return (a << 24) | ((int)(r*255)<<16) | ((int)(g*255)<<8) | (int)(b*255);
    }
}