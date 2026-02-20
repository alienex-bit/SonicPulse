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
import java.io.File;

public class ConfigScreen extends Screen {
    private static final int BOX_WIDTH = 360, BOX_HEIGHT = 220;
    private static final int SIDEBAR_WIDTH = 75;
    private TextFieldWidget urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, renamingIndex = -1, radioScrollOffset = 0, historyScrollOffset = 0;
    private boolean showOnlyFavorites = false; 
    private final List<String[]> radioStreams = new ArrayList<>();
    
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
            String label = (currentTab == index) ? "§a" + TAB_LABELS[index] : TAB_LABELS[index];
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> { 
                currentTab = index; 
                renamingIndex = -1;
                historyScrollOffset = 0;
                radioScrollOffset = 0;
                if (index == 3) showOnlyFavorites = false; 
                refreshWidgets(); 
            }).dimensions(x + 5, y + 25 + (i * 22), SIDEBAR_WIDTH - 10, 20).build());
        }

        switch (currentTab) {
            case 0: // PLAY
                urlField = new TextFieldWidget(textRenderer, contentX, y + 40, contentW, 20, Text.literal("URL"));
                urlField.setMaxLength(256); addSelectableChild(urlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("▶ PLAY"), b -> {
                    String u = urlField.getText();
                    if (!u.isEmpty()) {
                        config.currentTitle = null; 
                        SonicPulseClient.getEngine().stop();
                        SonicPulseClient.getEngine().playTrack(u);
                    }
                }).dimensions(contentX, y + 65, 60, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("⏸"), b -> SonicPulseClient.getEngine().getPlayer().setPaused(!SonicPulseClient.getEngine().getPlayer().isPaused())).dimensions(contentX + 65, y + 65, 30, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> SonicPulseClient.getEngine().stop()).dimensions(contentX + 100, y + 65, 30, 20).build());
                addDrawableChild(new SliderWidget(contentX, y + 95, contentW, 20, Text.literal("Volume: " + config.volume + "%"), config.volume / 100.0) { 
                    @Override protected void updateMessage() { setMessage(Text.literal("Volume: " + (int)(value * 100) + "%")); } 
                    @Override protected void applyValue() { config.volume = (int)(value * 100); SonicPulseConfig.save(); } 
                });
                break;
            case 1: // VISUALS
                addDrawableChild(ButtonWidget.builder(Text.literal("Theme: " + config.skin.getName()), b -> { config.nextSkin(); refreshWidgets(); }).dimensions(contentX, y + 30, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Type: " + config.visStyle.name()), b -> { config.nextVisStyle(); refreshWidgets(); }).dimensions(contentX, y + 55, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Color: " + COLOR_NAMES[colorIndex]), b -> { colorIndex = (colorIndex+1)%BAR_COLORS.length; config.setColor(BAR_COLORS[colorIndex]); refreshWidgets(); }).dimensions(contentX, y + 80, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bar Mode: " + config.colorMode.name()), b -> { config.nextColorMode(); refreshWidgets(); }).dimensions(contentX, y + 105, contentW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Title: " + COLOR_NAMES[titleColorIndex]), b -> { titleColorIndex = (titleColorIndex+1)%BAR_COLORS.length; config.setTitleColor(BAR_COLORS[titleColorIndex]); refreshWidgets(); }).dimensions(contentX, y + 130, contentW, 20).build());
                break;
            case 3: // HISTORY
                int filterBtnW = (contentW / 3) - 2;
                addDrawableChild(ButtonWidget.builder(Text.literal("§e★ Favourites"), b -> { showOnlyFavorites = true; historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX, y + 25, filterBtnW, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("§eAll History"), b -> { showOnlyFavorites = false; historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX + filterBtnW + 2, y + 25, filterBtnW, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX + (filterBtnW * 2) + 4, y + 25, filterBtnW, 18).build());

                List<SonicPulseConfig.HistoryEntry> hist = showOnlyFavorites ? config.getFavoriteHistory() : new ArrayList<>(config.history);
                int hVisible = 6;
                if (hist.size() > hVisible) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { if (historyScrollOffset > 0) { historyScrollOffset--; refreshWidgets(); } }).dimensions(contentX + contentW - 20, y + 48, 20, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { if (historyScrollOffset < hist.size() - hVisible) { historyScrollOffset++; refreshWidgets(); } }).dimensions(contentX + contentW - 20, y + 48 + (hVisible - 1) * 22, 20, 20).build());
                }

                for (int i = historyScrollOffset; i < Math.min(hist.size(), historyScrollOffset + hVisible); i++) {
                    final int eIdx = i; SonicPulseConfig.HistoryEntry e = hist.get(i);
                    int rowY = y + 48 + ((i - historyScrollOffset) * 22);
                    if (renamingIndex == eIdx) {
                        renameField = new TextFieldWidget(textRenderer, contentX, rowY, contentW - 50, 20, Text.literal("Rename"));
                        renameField.setText(e.label); addSelectableChild(renameField);
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> { e.label = renameField.getText(); renamingIndex = -1; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 45, rowY, 20, 20).build());
                    } else {
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> { config.currentTitle = e.label; SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(e.url); }).dimensions(contentX, rowY, contentW - 90, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.favorite ? "★" : "☆"), b -> { e.favorite = !e.favorite; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 87, rowY, 20, 20).build());
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
                
                if (radioStreams.size() > 5) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { if (radioScrollOffset > 0) { radioScrollOffset--; refreshWidgets(); } }).dimensions(contentX + contentW - 20, y + 75, 20, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { if (radioScrollOffset < radioStreams.size() - 5) { radioScrollOffset++; refreshWidgets(); } }).dimensions(contentX + contentW - 20, y + 75 + 88, 20, 20).build());
                }
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 5); i++) {
                    String[] rs = radioStreams.get(i);
                    addDrawableChild(ButtonWidget.builder(Text.literal(rs[0]), b -> { 
                        config.currentTitle = rs[0]; 
                        // Force save history with the Station Name immediately to avoid metadata overwrite
                        config.addHistory("Radio", rs[0], rs[1]); 
                        SonicPulseClient.getEngine().stop(); 
                        SonicPulseClient.getEngine().playTrack(rs[1]); 
                    }).dimensions(contentX, y + 75 + ((i - radioScrollOffset) * 22), contentW - 25, 20).build());
                }
                break;
        }
    }

    private void loadRadioM3U(String url) {
        config.lastRadioUrl = url; SonicPulseConfig.save(); radioStreams.clear();
        new Thread(() -> { try { java.net.URL u = new java.net.URI(url).toURL(); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream())); String l, lt = null; while ((l = r.readLine()) != null) { l = l.trim(); if (l.isEmpty() || l.startsWith("#EXTM3U")) continue; if (l.startsWith("#EXTINF")) { int c = l.indexOf(","); if (c != -1) lt = l.substring(c + 1).trim(); } else if (!l.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : "Station", l}); lt = null; } } r.close(); } catch (Exception e) {} MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }

    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 20, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 5, 0x44FFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("SONICPULSE"), x + 10, y + 8, 0xFF55FF);
        
        if (currentTab == 3) {
            int contentX = x + SIDEBAR_WIDTH + 10;
            int filterBtnW = ((BOX_WIDTH - SIDEBAR_WIDTH - 20) / 3) - 2;
            int highlightX = showOnlyFavorites ? contentX : contentX + filterBtnW + 2;
            context.drawBorder(highlightX - 1, y + 24, filterBtnW + 2, 20, 0xFF00FFFF); 
        }

        super.render(context, mx, my, d);
        if (currentTab == 0 && urlField != null) urlField.render(context, mx, my, d);
        if (currentTab == 3 && renameField != null && renamingIndex != -1) renameField.render(context, mx, my, d);
        if (currentTab == 4 && radioUrlField != null) radioUrlField.render(context, mx, my, d);
    }
}