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
import org.steve.sonicpulse.client.gui.SonicPulseHud;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.File;
import java.util.Scanner;

public class ConfigScreen extends Screen {
    private static final int BOX_WIDTH = 360, BOX_HEIGHT = 220;
    private static final int SIDEBAR_WIDTH = 75;
    private TextFieldWidget urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, radioScrollOffset = 0, historyScrollOffset = 0, localScrollOffset = 0, renamingIndex = -1;
    private boolean showOnlyFavorites = false, shuffleEnabled = false; 
    private final List<String[]> radioStreams = new ArrayList<>();
    private final List<File> localFiles = new ArrayList<>();
    private static final int ACTIVE_BORDER = 0xFFFF00FF; 
    private final SonicPulseHud hudRenderer = new SonicPulseHud();
    private ButtonWidget playBtn, pauseBtn, stopBtn;

    public ConfigScreen() { 
        super(Text.literal("SonicPulse Config")); 
        updatePaletteIndices();
    }

    private void updatePaletteIndices() {
        for(int i=0; i<SonicPulseConfig.PALETTE.length; i++) { 
            if((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.barColor)) colorIndex=i; 
            if((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.titleColor)) titleColorIndex=i;
        }
    }

    private static final String[] TAB_LABELS = {"PLAY", "VISUALS", "LAYOUT", "HISTORY", "RADIO", "LOCAL"};

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[idx]), b -> { 
                currentTab = idx; historyScrollOffset = 0; radioScrollOffset = 0; localScrollOffset = 0; renamingIndex = -1;
                if (idx == 5) scanLocalFiles();
                refreshWidgets(); 
            }).dimensions(x + 5, y + 25 + (i * 22), SIDEBAR_WIDTH - 10, 20).build());
        }

        int colW = (contentW / 2) - 5;
        switch (currentTab) {
            case 0: // PLAY
                urlField = new TextFieldWidget(textRenderer, contentX, y + 35, contentW, 20, Text.literal("URL"));
                urlField.setMaxLength(256); addSelectableChild(urlField);
                int bW = 60, bGrpW = (bW * 3) + 10, gX = contentX + (contentW - bGrpW) / 2;
                playBtn = addDrawableChild(ButtonWidget.builder(Text.literal("▶ PLAY"), b -> { 
                    String u = urlField.getText(); 
                    if (!u.isEmpty()) { config.currentTitle = null; SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(u); }
                    else if (SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null) SonicPulseClient.getEngine().getPlayer().setPaused(false);
                }).dimensions(gX, y + 65, bW, 20).build());
                pauseBtn = addDrawableChild(ButtonWidget.builder(Text.literal("⏸ PAUSE"), b -> {
                    if (SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null) SonicPulseClient.getEngine().getPlayer().setPaused(!SonicPulseClient.getEngine().getPlayer().isPaused());
                }).dimensions(gX + 65, y + 65, bW, 20).build());
                stopBtn = addDrawableChild(ButtonWidget.builder(Text.literal("⏹ STOP"), b -> SonicPulseClient.getEngine().stop()).dimensions(gX + 130, y + 65, bW, 20).build());
                addDrawableChild(new SliderWidget(contentX, y + 95, contentW, 20, Text.literal("Volume: " + config.volume + "%"), config.volume / 100.0) { @Override protected void updateMessage() { setMessage(Text.literal("Volume: " + (int)(value * 100) + "%")); } @Override protected void applyValue() { config.volume = (int)(value * 100); SonicPulseConfig.save(); } });
                break;
            case 1: // VISUALS
                addDrawableChild(ButtonWidget.builder(Text.literal("HUD: " + (config.hudVisible ? "VISIBLE" : "HIDDEN")), b -> { config.hudVisible = !config.hudVisible; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 35, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Skin: " + config.skin.getName()), b -> { config.nextSkin(); refreshWidgets(); }).dimensions(contentX, y + 60, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Logo: " + (config.showLogo ? "ON" : "OFF")), b -> { config.showLogo = !config.showLogo; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 85, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Track: " + (config.showTrack ? "ON" : "OFF")), b -> { config.showTrack = !config.showTrack; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 110, colW, 20).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Style: " + config.visStyle.name().replace("_", " ")), b -> { config.nextVisStyle(); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 35, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bar: " + SonicPulseConfig.COLOR_NAMES[colorIndex]), b -> { colorIndex = (colorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setColor(SonicPulseConfig.PALETTE[colorIndex]); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 60, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Hud Title: " + SonicPulseConfig.COLOR_NAMES[titleColorIndex]), b -> { titleColorIndex = (titleColorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setTitleColor(SonicPulseConfig.PALETTE[titleColorIndex]); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 85, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bars: " + (config.showBars ? "ON" : "OFF")), b -> { config.showBars = !config.showBars; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 110, colW, 20).build());
                break;
            case 2: // LAYOUT
                addDrawableChild(ButtonWidget.builder(Text.literal("Order: " + config.ribbonLayout.getDisplayName()), b -> { config.nextRibbonLayout(); refreshWidgets(); }).dimensions(contentX, y + 35, contentW, 20).build());
                addDrawableChild(new SliderWidget(contentX, y + 60, contentW, 20, Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%"), (config.hudScale - 0.5) / 0.5) { 
                    @Override protected void updateMessage() { setMessage(Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%")); } 
                    @Override protected void applyValue() { config.hudScale = (float)(0.5 + (value * 0.5)); SonicPulseConfig.save(); } 
                });
                break;
            case 3: // HISTORY
                List<SonicPulseConfig.HistoryEntry> hist = showOnlyFavorites ? config.getFavoriteHistory() : new ArrayList<>(config.history);
                addDrawableChild(ButtonWidget.builder(Text.literal("§e★ Favs"), b -> { showOnlyFavorites = true; historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX, y + 30, (contentW/3)-2, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("§eAll"), b -> { showOnlyFavorites = false; historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX + (contentW/3), y + 30, (contentW/3)-2, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); historyScrollOffset = 0; refreshWidgets(); }).dimensions(contentX + (contentW/3)*2, y + 30, (contentW/3)-2, 18).build());
                
                int histVisibleCount = 7;
                for (int i = historyScrollOffset; i < Math.min(hist.size(), historyScrollOffset + histVisibleCount); i++) {
                    final int hIdx = i; SonicPulseConfig.HistoryEntry e = hist.get(hIdx);
                    int rowY = y + 52 + ((hIdx - historyScrollOffset) * 22);
                    
                    if (renamingIndex == hIdx) {
                        renameField = new TextFieldWidget(textRenderer, contentX, rowY, contentW - 25, 20, Text.literal("Rename"));
                        renameField.setText(e.label); addSelectableChild(renameField);
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> { e.label = renameField.getText(); renamingIndex = -1; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 20, rowY, 20, 20).build());
                    } else {
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> { config.currentTitle = e.label; SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(e.url); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 85, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.favorite ? "§e★" : "☆"), b -> { e.favorite = !e.favorite; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 82, rowY, 20, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> { renamingIndex = hIdx; refreshWidgets(); }).dimensions(contentX + contentW - 61, rowY, 20, 20).build());
                        if (showOnlyFavorites) {
                            addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> { moveHistoryItem(e, -1); }).dimensions(contentX + contentW - 40, rowY, 10, 20).build());
                            addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> { moveHistoryItem(e, 1); }).dimensions(contentX + contentW - 30, rowY, 10, 20).build());
                        }
                        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 20, rowY, 20, 20).build());
                    }
                }
                if (historyScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { historyScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 52, 12, 20).build());
                if (hist.size() > historyScrollOffset + histVisibleCount) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { historyScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 52 + (histVisibleCount - 1) * 22, 12, 20).build());
                break;
            case 4: // RADIO
                radioUrlField = new TextFieldWidget(textRenderer, contentX, y + 30, contentW - 50, 20, Text.literal("M3U URL")); addSelectableChild(radioUrlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD"), b -> loadRadioM3U(radioUrlField.getText())).dimensions(contentX + contentW - 45, y + 30, 45, 20).build());
                
                int radioVisibleCount = 7;
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + radioVisibleCount); i++) {
                    final int rsIdx = i; String[] rs = radioStreams.get(rsIdx);
                    addDrawableChild(ButtonWidget.builder(Text.literal(rs[0]), b -> { config.currentTitle = rs[0]; config.addHistory("Radio", rs[0], rs[1]); SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(rs[1]); refreshWidgets(); }).dimensions(contentX, y + 55 + ((rsIdx - radioScrollOffset) * 22), contentW, 20).build());
                }
                if (radioScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { radioScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 55, 12, 20).build());
                if (radioStreams.size() > radioScrollOffset + radioVisibleCount) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { radioScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 55 + (radioVisibleCount - 1) * 22, 12, 20).build());
                break;
            case 5: // LOCAL
                addDrawableChild(ButtonWidget.builder(Text.literal("SELECT FOLDER"), b -> { 
                    new Thread(() -> { try { System.setProperty("java.awt.headless", "false"); String script = "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if($f.ShowDialog() -eq 'OK'){$f.SelectedPath}"; Process p = Runtime.getRuntime().exec(new String[]{"powershell", "-Command", script}); Scanner s = new Scanner(p.getInputStream()); if(s.hasNextLine()) { String path = s.nextLine().trim(); if(!path.isEmpty()) MinecraftClient.getInstance().execute(() -> { config.localMusicPath = path; SonicPulseConfig.save(); scanLocalFiles(); refreshWidgets(); }); } } catch (Exception e) {} }).start();
                }).dimensions(contentX, y + 35, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("SHUFFLE: " + (shuffleEnabled ? "ON" : "OFF")), b -> { shuffleEnabled = !shuffleEnabled; scanLocalFiles(); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 35, colW, 20).build());
                
                int localVisibleCount = 7;
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + localVisibleCount); i++) {
                    final int lIdx = i; File f = localFiles.get(lIdx); String n = f.getName(); String icon = n.toLowerCase().endsWith(".m3u") ? "☰ " : "♫ ";
                    addDrawableChild(ButtonWidget.builder(Text.literal(icon + n), b -> { config.currentTitle = n; SonicPulseClient.getEngine().stop(); SonicPulseClient.getEngine().playTrack(f.getAbsolutePath()); refreshWidgets(); }).dimensions(contentX, y + 60 + ((i - localScrollOffset) * 22), contentW, 20).build());
                }
                if (localScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { localScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 60, 12, 20).build());
                if (localFiles.size() > localScrollOffset + localVisibleCount) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { localScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 60 + (localVisibleCount - 1) * 22, 12, 20).build());
                break;
        }
    }

    private void moveHistoryItem(SonicPulseConfig.HistoryEntry entry, int direction) {
        int oldIndex = config.history.indexOf(entry);
        int newIndex = oldIndex + direction;
        if (newIndex >= 0 && newIndex < config.history.size()) { Collections.swap(config.history, oldIndex, newIndex); SonicPulseConfig.save(); refreshWidgets(); }
    }

    private void scanLocalFiles() {
        localFiles.clear();
        String path = (config.localMusicPath != null && !config.localMusicPath.isEmpty()) ? config.localMusicPath : MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse/music").toString();
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> { String l = n.toLowerCase(); return l.endsWith(".mp3") || l.endsWith(".wav") || l.endsWith(".m3u") || l.endsWith(".flac"); });
            if (files != null) { Collections.addAll(localFiles, files); if (shuffleEnabled) Collections.shuffle(localFiles); }
        }
    }

    private void loadRadioM3U(String url) {
        config.lastRadioUrl = url; SonicPulseConfig.save(); radioStreams.clear();
        new Thread(() -> { try { java.net.URL u = new java.net.URI(url).toURL(); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream())); String l, lt = null; while ((l = r.readLine()) != null) { l = l.trim(); if (l.isEmpty() || l.startsWith("#EXTM3U")) continue; if (l.startsWith("#EXTINF")) { int c = l.indexOf(","); if (c != -1) lt = l.substring(c + 1).trim(); } else if (!l.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : "Station", l}); lt = null; } } r.close(); } catch (Exception e) {} MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) { if (currentTab == 0 && urlField != null && urlField.isMouseOver(mx, my)) urlField.setText(""); return super.mouseClicked(mx, my, b); }

    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 20, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 5, 0x44FFFFFF);
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;
        super.render(context, mx, my, d);
        
        boolean playing = SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null;
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();

        if (currentTab == 0) {
            // BUG FIX: Title added here directly above the URL box!
            context.drawText(textRenderer, Text.literal("Enter audio URL to stream:"), contentX, y + 23, 0xFFFFFFFF, false);
            
            int bW = 60, bGrpW = (bW * 3) + 10, gX = contentX + (contentW - bGrpW) / 2;
            if (playing && !paused) context.fill(gX, y + 65, gX + bW, y + 85, 0x6600FF00); 
            if (playing && paused) context.fill(gX + 65, y + 65, gX + 65 + bW, y + 85, 0x66FF8C00); 
            if (!playing) context.fill(gX + 130, y + 65, gX + 130 + bW, y + 85, 0x66FF0000); 
        }

        if (currentTab == 1) {
            context.fill(contentX + (contentW / 2), y + 35, contentX + (contentW / 2) + 1, y + 130, 0x44FFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("HUD THEME"), contentX + (contentW / 4), y + 23, 0xFFFF00FF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("BAR VISUALS"), contentX + (contentW / 4) * 3, y + 23, 0xFFFF00FF);
        }
        
        if (currentTab == 5) {
            String dp = (config.localMusicPath != null && !config.localMusicPath.isEmpty()) ? config.localMusicPath : "Default Folder";
            if (dp.length() > 30) dp = "..." + dp.substring(dp.length() - 27);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("FOLDER: §f" + dp), contentX + (contentW/2), y + 23, 0xFFFF00FF);
        }
        
        context.drawText(textRenderer, Text.literal("SONICPULSE"), x + 10, y + 8, 0xFFFF00FF, false);
        context.drawText(textRenderer, Text.literal("♫"), x + BOX_WIDTH - 15, y + 8, 0xFFFF00FF, false);
        context.drawBorder(x + 4, y + 24 + (currentTab * 22), SIDEBAR_WIDTH - 8, 22, ACTIVE_BORDER);
        if (currentTab == 0 && urlField != null) urlField.render(context, mx, my, d);
        if (currentTab == 3 && renameField != null && renamingIndex != -1) renameField.render(context, mx, my, d);
        
        hudRenderer.render(context, true, 0, 0);
    }
}