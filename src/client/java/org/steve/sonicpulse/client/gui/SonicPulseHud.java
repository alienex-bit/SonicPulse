package org.steve.sonicpulse.client.gui;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class SonicPulseHud implements HudRenderCallback {
    // scrollX removed - static text for now to ensure visibility
    
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
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

        // 2. Header (title uses configurable titleColor)
        context.drawText(client.textRenderer, "SONICPULSE", 5, 5, config.titleColor, true);

        // 3. Title Logic
        String displayText;
        if (config.currentTitle != null && !config.currentTitle.isEmpty()) {
            displayText = config.currentTitle; // The Forced Title
        } else {
            // Fallback
            if (track.getInfo().author != null && !track.getInfo().author.equals("Unknown")) {
                displayText = track.getInfo().author + " - " + track.getInfo().title;
            } else {
                displayText = track.getInfo().title;
            }
        }

        if (displayText == null || displayText.trim().isEmpty()) displayText = "Streaming...";

        // 4. Draw Text (DIRECTLY - No Clipping/Scissor)
        // Ensure the title never exceeds the HUD box width; truncate by pixel width and add ellipsis.
        int padding = 10; // left+right padding (5 each)
        int maxTextWidth = hudW - padding;
        String drawText = displayText;
        int textWidth = client.textRenderer.getWidth(drawText);
        if (textWidth > maxTextWidth) {
            String ell = "...";
            int ellW = client.textRenderer.getWidth(ell);
            int allowed = Math.max(0, maxTextWidth - ellW);
            // binary-search-like shortening by character count for efficiency
            int low = 0, high = drawText.length();
            while (low < high) {
                int mid = (low + high + 1) / 2;
                String sub = drawText.substring(0, mid);
                if (client.textRenderer.getWidth(sub) <= allowed) low = mid; else high = mid - 1;
            }
            drawText = drawText.substring(0, low) + ell;
        }
        context.drawText(client.textRenderer, drawText, 5, 20, 0xFFFFFFFF, true);

        // 5. Visualizer - support multiple styles
        float[] v = SonicPulseClient.getEngine().getVisualizerData();
        int bars = Math.min(32, v.length);
        int barW = Math.max(1, (hudW - 10) / bars);
        int barStartY = hudH - 5;

        switch (config.visStyle) {
            case SOLID: {
                // Solid continuous bars (original behavior)
                for (int i = 0; i < bars; i++) {
                    int bh = (int)(v[i] * 25);
                    if (bh > 20) bh = 20;
                    if (bh > 0) {
                        context.fill(5 + i * barW, barStartY - bh, 5 + i * barW + (barW - 1), barStartY, config.barColor);
                    }
                }
                break;
            }
            case SEGMENTED: {
                // Draw bars as stacked segments
                int segmentHeight = 5;
                int maxHeight = 20;
                int segments = Math.max(1, maxHeight / segmentHeight);
                for (int i = 0; i < bars; i++) {
                    int bh = (int)(v[i] * maxHeight);
                    if (bh > maxHeight) bh = maxHeight;
                    for (int s = 0; s < segments; s++) {
                        int segTop = barStartY - (s + 1) * segmentHeight;
                        int segBottom = barStartY - s * segmentHeight;
                        if (bh > s * segmentHeight) {
                            // filled segment
                            context.fill(5 + i * barW, segTop, 5 + i * barW + (barW - 1), segBottom, config.barColor);
                        } else {
                            // optionally draw dim background for segment (low alpha gray)
                            // using a darker color for unfilled segments
                            context.fill(5 + i * barW, segTop, 5 + i * barW + (barW - 1), segBottom, 0xFF222222);
                        }
                    }
                }
                break;
            }
            case MIRRORED: {
                // Bars mirrored around a center line
                int centerY = hudH / 2;
                int maxHeight = Math.min(centerY - 4, 20);
                for (int i = 0; i < bars; i++) {
                    int bh = (int)(v[i] * maxHeight);
                    if (bh > maxHeight) bh = maxHeight;
                    if (bh > 0) {
                        // upper part
                        context.fill(5 + i * barW, centerY - bh, 5 + i * barW + (barW - 1), centerY, config.barColor);
                        // lower mirrored part
                        context.fill(5 + i * barW, centerY, 5 + i * barW + (barW - 1), centerY + bh, config.barColor);
                    }
                }
                break;
            }
            case WAVEFORM: {
                // Draw a simple waveform-like visualization: small vertical line whose top varies
                int maxH = 18;
                for (int i = 0; i < bars; i++) {
                    int hh = (int)(v[i] * maxH);
                    if (hh > maxH) hh = maxH;
                    int lineX = 5 + i * barW;
                    // draw a 2px tall vertical line centered, representing waveform height
                    context.fill(lineX, barStartY - hh, lineX + Math.max(1, barW / 2), barStartY, config.barColor);
                }
                break;
            }
            case PEAK_DOTS: {
                // Show a small dot at the peak for each bar
                for (int i = 0; i < bars; i++) {
                    int bh = (int)(v[i] * 25);
                    if (bh > 20) bh = 20;
                    if (bh > 0) {
                        int dotY = barStartY - bh;
                        int dotX = 5 + i * barW + (barW / 2) - 1;
                        context.fill(dotX, dotY, dotX + 2, dotY + 2, config.barColor);
                    }
                }
                break;
            }
            default: {
                // fallback to solid
                for (int i = 0; i < bars; i++) {
                    int bh = (int)(v[i] * 25);
                    if (bh > 20) bh = 20;
                    if (bh > 0) {
                        context.fill(5 + i * barW, barStartY - bh, 5 + i * barW + (barW - 1), barStartY, config.barColor);
                    }
                }
                break;
            }
        }

        context.getMatrices().pop();
     }
 }
