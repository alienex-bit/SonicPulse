package org.steve.sonicpulse.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.File;

public class ConfigScreen extends Screen {
    private static final int BOX_WIDTH = 360, BOX_HEIGHT = 220;
    private static final int SIDEBAR_WIDTH = 75;
    private TextFieldWidget urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, renamingIndex = -1, radioScrollOffset = 0, historyScrollOffset = 0;
    private boolean showOnlyFavorites = false; 
    private final List<String[]> radioStreams = new ArrayList<>();
    private static final int ACTIVE_BORDER = 0xFFFF00FF; // Magenta
    
    private static final String[] COLOR_NAMES = {"Green", "Cyan", "Magenta", "Yellow", "Orange", "Blue", "Red", "Coral", "DeepSkyBlue", "Violet", "Lime", "Salmon", "Turquoise", "Indigo", "Amber", "Mint", "Brown"};
    private static final int[] BAR_COLORS = {
        0xFF00FF00, 0xFF00FFFF, 0xFFFF00FF, 0xFFFFFF00, 0xFFFF5500, 0xFF0000FF, 0xFFFF0000, 0xFFFF7F50, 
        0xFF00BFFF, 0xFF8A2BE2, 0xFF32CD32, 0xFFFA8072, 0xFF40E0D0, 0xFF4B0082, 0xFFFFBF00, 0xFF98FF98, 0xFFA52A2A
    };
    
    private List<File> localSongs = new ArrayList<>();
    private static final String[] TAB_LABELS = {"PLAY", "VISUALS", "LAYOUT", "HISTORY", "RADIO", "LOCAL"};

    public ConfigScreen() { 
        super(Text.literal("SonicPulse Config")); 
        for(int i=0; i<BAR_COLORS.length; i++) { 
            if(BAR_COLORS[i]==config.barColor) colorIndex=i; 
            if(BAR_COLORS[i]==config.titleColor) titleColorIndex=i; 
        } 
    }

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10;
        int contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int index = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[index]), b -> { 
                currentTab = index; renamingIndex = -1; historyScrollOffset = 0; radioScrollOffset = 0;
                if (index == 3) showOnlyFavorites = false; 
                refreshWidgets(); 
            }).dimensions(x + 5, y + 25 + (i * 22), SIDEBAR_WIDTH - 10, 20).build());
        }

        switch (currentTab) {
            case 1: // VISUALS - Fixed apply logic
                int colW = (contentW / 2) - 10;
                addDrawableChild(ButtonWidget.builder(Text.literal("HUD Theme: " + config.skin.getName()), b -> { config.nextSkin(); refreshWidgets(); }).dimensions(contentX, y + 40, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Title: " + COLOR_NAMES[titleColorIndex]), b -> { titleColorIndex = (titleColorIndex+1)%BAR_COLORS.length; config.setTitleColor(BAR_COLORS[titleColorIndex]); refreshWidgets(); }).dimensions(contentX, y + 65, colW, 20).build());
                
                int col2X = contentX + colW + 20;
                addDrawableChild(ButtonWidget.builder(Text.literal("Style: " + config.visStyle.name()), b -> { config.nextVisStyle(); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(col2X, y + 40, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bar Color: " + COLOR_NAMES[colorIndex]), b -> { colorIndex = (colorIndex+1)%BAR_COLORS.length; config.setColor(BAR_COLORS[colorIndex]); refreshWidgets(); }).dimensions(col2X, y + 65, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Animation: " + config.colorMode.name()), b -> { config.nextColorMode(); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(col2X, y + 90, colW, 20).build());
                break;
            case 0: // PLAY
                urlField = new TextFieldWidget(textRenderer, contentX, y + 40, contentW, 20, Text.literal("URL"));
                urlField.setMaxLength(256); addSelectableChild(urlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("▶ PLAY"), b -> {
                    String u = urlField.getText();
                    if (!u.isEmpty()) { config.currentTitle = null; SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(u); }
                }).dimensions(contentX, y + 65, 60, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("⏸"), b -> SonicPulseClient.getEngine().getPlayer().setPaused(!SonicPulseClient.getEngine().getPlayer().isPaused())).dimensions(contentX + 65, y + 65, 30, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> SonicPulseClient.getEngine().stop()).dimensions(contentX + 100, y + 65, 30, 20).build());
                addDrawableChild(new SliderWidget(contentX, y + 95, contentW, 20, Text.literal("Volume: " + config.volume + "%"), config.volume / 100.0) { 
                    @Override protected void updateMessage() { setMessage(Text.literal("Volume: " + (int)(value * 100) + "%")); } 
                    @Override protected void applyValue() { config.volume = (int)(value * 100); SonicPulseConfig.save(); } 
                });
                break;
            case 2: // LAYOUT
                addDrawableChild(ButtonWidget.builder(Text.literal("Top Left Corner"), b -> { config.setPos(10, 10); refreshWidgets(); }).dimensions(contentX, y + 40, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Top Right Corner"), b -> { config.setPos(-10, 10); refreshWidgets(); }).dimensions(contentX, y + 65, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bottom Left Corner"), b -> { config.setPos(10, -10); refreshWidgets(); }).dimensions(contentX, y + 90, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bottom Right Corner"), b -> { config.setPos(-10, -10); refreshWidgets(); }).dimensions(contentX, y + 115, contentW, 20).build());
                break;
            case 3: // HISTORY
                int filterBtnW = (contentW / 3) - 2;
                addDrawableChild(ButtonWidget.builder(Text.literal("§e★ Favourites"), b -> { showOnlyFavorites = true; historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX, y + 25, filterBtnW, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("§eAll History"), b -> { showOnlyFavorites = false; historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX + filterBtnW + 2, y + 25, filterBtnW, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX + (filterBtnW * 2) + 4, y + 25, filterBtnW, 18).build());
                List<SonicPulseConfig.HistoryEntry> hist = showOnlyFavorites ? config.getFavoriteHistory() : new ArrayList<>(config.history);
                for (int i = historyScrollOffset; i < Math.min(hist.size(), historyScrollOffset + 6); i++) {
                    final int eIdx = i; SonicPulseConfig.HistoryEntry e = hist.get(i);
                    int rowY = y + 48 + ((i - historyScrollOffset) * 22);
                    if (renamingIndex == eIdx) {
                        renameField = new TextFieldWidget(textRenderer, contentX, rowY, contentW - 50, 20, Text.literal("Rename"));
                        renameField.setText(e.label); addSelectableChild(renameField);
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> { e.label = renameField.getText(); renamingIndex = -1; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 45, rowY, 20, 20).build());
                    } else {
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> { config.currentTitle = e.label; SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(e.url); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 120, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.favorite ? "★" : "☆"), b -> { e.favorite = !e.favorite; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 117, rowY, 20, 20).build());
                        if (showOnlyFavorites) {
                            addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> moveHistoryItem(e, -1)).dimensions(contentX + contentW - 96, rowY, 15, 20).build());
                            addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> moveHistoryItem(e, 1)).dimensions(contentX + contentW - 81, rowY, 15, 20).build());
                        }
                        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> { renamingIndex = eIdx; refreshWidgets(); }).dimensions(contentX + contentW - 66, rowY, 20, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 45, rowY, 20, 20).build());
                    }
                }
                break;
            case 4: // RADIO
                String[] radioPresets = {"BBC", "Soma", "Mix", "Elec", "Chill"};
                String[] presetUrls = {"https://gist.githubusercontent.com/bpsib/67089b959e4fa898af69fea59ad74bc3/raw/c7255834f326bc6a406080eed104ebaa9d3bc85d/BBC-Radio-HLS.m3u", "https://gist.githubusercontent.com/casaper/ddec35d21a0158628fccbab7876b7ef3/raw/somafm.m3u", "https://raw.githubusercontent.com/lovehifi/playlist-radio/main/playlist_radio.tgz", "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/all-online_radio/electronic.m3u", "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/all-online_radio/chillout.m3u"};
                for (int i = 0; i < radioPresets.length; i++) {
                    final int pIdx = i;
                    addDrawableChild(ButtonWidget.builder(Text.literal(radioPresets[i]), b -> { radioUrlField.setText(presetUrls[pIdx]); loadRadioM3U(presetUrls[pIdx]); }).dimensions(contentX + (i * (contentW / 5)), y + 25, (contentW / 5) - 2, 20).build());
                }
                radioUrlField = new TextFieldWidget(textRenderer, contentX, y + 50, contentW - 40, 20, Text.literal("M3U")); addSelectableChild(radioUrlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("Load"), b -> loadRadioM3U(radioUrlField.getText())).dimensions(contentX + contentW - 35, y + 50, 35, 20).build());
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 5); i++) {
                    String[] rs = radioStreams.get(i);
                    addDrawableChild(ButtonWidget.builder(Text.literal(rs[0]), b -> { config.currentTitle = rs[0]; config.addHistory("Radio", rs[0], rs[1]); SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(rs[1]); refreshWidgets(); }).dimensions(contentX, y + 75 + ((i - radioScrollOffset) * 22), contentW - 25, 20).build());
                }
                break;
        }
    }

    private void moveHistoryItem(SonicPulseConfig.HistoryEntry entry, int direction) {
        int oldIndex = config.history.indexOf(entry);
        int newIndex = oldIndex + direction;
        if (newIndex >= 0 && newIndex < config.history.size()) { Collections.swap(config.history, oldIndex, newIndex); SonicPulseConfig.save(); refreshWidgets(); }
    }

    private void loadRadioM3U(String url) {
        config.lastRadioUrl = url; SonicPulseConfig.save(); radioStreams.clear();
        new Thread(() -> { try { java.net.URL u = new java.net.URI(url).toURL(); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream())); String l, lt = null; while ((l = r.readLine()) != null) { l = l.trim(); if (l.isEmpty() || l.startsWith("#EXTM3U")) continue; if (l.startsWith("#EXTINF")) { int c = l.indexOf(","); if (c != -1) lt = l.substring(c + 1).trim(); } else if (!l.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : "Station", l}); lt = null; } } r.close(); } catch (Exception e) {} MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (currentTab == 0 && urlField != null && urlField.isMouseOver(mx, my)) urlField.setText("");
        return super.mouseClicked(mx, my, b);
    }

    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 20, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 5, 0x44FFFFFF);
        
        int contentX = x + SIDEBAR_WIDTH + 10;
        int contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;
        if (currentTab == 1) {
            context.fill(contentX + (contentW / 2), y + 25, contentX + (contentW / 2) + 1, y + BOX_HEIGHT - 10, 0x44FFFFFF);
        }

        context.drawBorder(x + 4, y + 24 + (currentTab * 22), SIDEBAR_WIDTH - 8, 22, ACTIVE_BORDER);
        if (currentTab == 2) {
            if (config.hudX == 10 && config.hudY == 10) context.drawBorder(contentX - 1, y + 39, contentW + 2, 22, ACTIVE_BORDER);
            if (config.hudX == -10 && config.hudY == 10) context.drawBorder(contentX - 1, y + 64, contentW + 2, 22, ACTIVE_BORDER);
            if (config.hudX == 10 && config.hudY == -10) context.drawBorder(contentX - 1, y + 89, contentW + 2, 22, ACTIVE_BORDER);
            if (config.hudX == -10 && config.hudY == -10) context.drawBorder(contentX - 1, y + 114, contentW + 2, 22, ACTIVE_BORDER);
        }
        if (currentTab == 3) {
            int filterBtnW = (contentW / 3) - 2;
            int hX = showOnlyFavorites ? contentX : contentX + filterBtnW + 2;
            context.drawBorder(hX - 1, y + 24, filterBtnW + 2, 20, ACTIVE_BORDER);
            List<SonicPulseConfig.HistoryEntry> hist = showOnlyFavorites ? config.getFavoriteHistory() : new ArrayList<>(config.history);
            String url = SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null ? SonicPulseClient.getEngine().getPlayer().getPlayingTrack().getInfo().uri : "";
            for (int i = historyScrollOffset; i < Math.min(hist.size(), historyScrollOffset + 6); i++) {
                if (hist.get(i).url.equals(url)) context.drawBorder(contentX - 1, y + 47 + ((i - historyScrollOffset) * 22), contentW - 118, 22, ACTIVE_BORDER);
            }
        }

        super.render(context, mx, my, d);
        context.drawText(textRenderer, "SONICPULSE", x + 10, y + 8, 0xFFFF00FF, false);
        context.drawText(textRenderer, "♫", x + BOX_WIDTH - 15, y + 8, 0xFFFF00FF, false);

        if (currentTab == 0) context.drawText(textRenderer, "Enter Stream URL (MP3, Web Radio, or YouTube)", contentX, y + 28, 0xFFFF00FF, false);
        if (currentTab == 1) { 
            context.drawText(textRenderer, "HUD APPEARANCE", contentX, y + 28, 0xFFFF00FF, false);
            context.drawText(textRenderer, "BAR BEHAVIOR", contentX + (contentW/2) + 10, y + 28, 0xFFFF00FF, false);
        }

        if (currentTab == 0 && urlField != null) urlField.render(context, mx, my, d);
        if (currentTab == 3 && renameField != null && renamingIndex != -1) renameField.render(context, mx, my, d);
        if (currentTab == 4 && radioUrlField != null) radioUrlField.render(context, mx, my, d);
    }
}