package org.steve.sonicpulse.client.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
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
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, radioScrollOffset = 0, historyScrollOffset = 0, favScrollOffset = 0, localScrollOffset = 0, renamingIndex = -1;
    private final List<String[]> radioStreams = new ArrayList<>();
    private final List<File> localFiles = new ArrayList<>();
    private static final int ACTIVE_BORDER = 0xFFFF00FF; 
    private final SonicPulseHud hudRenderer = new SonicPulseHud();
    private boolean isShuffling = false;

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

    private static final String[] TAB_LABELS = {"PLAY", "VISUALS", "LAYOUT", "HISTORY", "FAVORITES", "RADIO", "LOCAL"};

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[idx]), b -> { 
                currentTab = idx; renamingIndex = -1; 
                if (idx == 6) { localScrollOffset = 0; scanLocalFiles(); }
                refreshWidgets(); 
            }).dimensions(x + 5, y + 15 + (i * 22), SIDEBAR_WIDTH - 10, 20).build());
        }

        // --- GLOBAL PLAYBACK HEADER ---
        int headerX = x + BOX_WIDTH - 110;
        boolean playing = SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null;
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();

        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { if (paused) SonicPulseClient.getEngine().getPlayer().setPaused(false); isShuffling = false; refreshWidgets(); }).dimensions(headerX, y + 4, 20, 14).tooltip(Tooltip.of(Text.literal("Play"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏸"), b -> { if (playing) SonicPulseClient.getEngine().getPlayer().setPaused(!paused); refreshWidgets(); }).dimensions(headerX + 22, y + 4, 20, 14).tooltip(Tooltip.of(Text.literal("Pause"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> { SonicPulseClient.getEngine().stop(); isShuffling = false; refreshWidgets(); }).dimensions(headerX + 44, y + 4, 20, 14).tooltip(Tooltip.of(Text.literal("Stop"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("🔀"), b -> { 
            List<SonicPulseConfig.HistoryEntry> f = config.getFavoriteHistory(); 
            if (!f.isEmpty()) { 
                List<SonicPulseConfig.HistoryEntry> s = new ArrayList<>(f); 
                Collections.shuffle(s); 
                config.currentTitle = s.get(0).label; 
                SonicPulseClient.getEngine().playTrack(s.get(0).url); 
                isShuffling = true;
                refreshWidgets();
            } 
        }).dimensions(headerX + 66, y + 4, 20, 14).tooltip(Tooltip.of(Text.literal("Shuffle Favorites"))).build());

        int rowH = 19;
        int listVisibleCount = 9;
        int colW = (contentW / 2) - 5;

        switch (currentTab) {
            case 0: // PLAY
                urlField = new TextFieldWidget(textRenderer, contentX, y + 45, contentW, 20, Text.literal("URL"));
                urlField.setMaxLength(256); addSelectableChild(urlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD & PLAY URL"), b -> { 
                    if(!urlField.getText().isEmpty()) { isShuffling = false; SonicPulseClient.getEngine().playTrack(urlField.getText()); refreshWidgets(); }
                }).dimensions(contentX, y + 70, contentW, 20).build());
                addDrawableChild(new SliderWidget(contentX, y + 100, contentW, 20, Text.literal("Volume: " + config.volume + "%"), config.volume / 100.0) { @Override protected void updateMessage() { setMessage(Text.literal("Volume: " + (int)(value * 100) + "%")); } @Override protected void applyValue() { config.volume = (int)(value * 100); SonicPulseConfig.save(); } });
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
                addDrawableChild(new SliderWidget(contentX, y + 60, contentW, 20, Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%"), (config.hudScale - 0.5) / 0.5) { @Override protected void updateMessage() { setMessage(Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%")); } @Override protected void applyValue() { config.hudScale = (float)(0.5 + (value * 0.5)); SonicPulseConfig.save(); } });
                break;
            case 3: // HISTORY
                List<SonicPulseConfig.HistoryEntry> historyOnly = config.history.stream().filter(e -> !e.favorite).toList();
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear History"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 25, contentW, 16).build());
                for (int i = historyScrollOffset; i < Math.min(historyOnly.size(), historyScrollOffset + 8); i++) {
                    final int hIdx = i; SonicPulseConfig.HistoryEntry e = historyOnly.get(hIdx);
                    int rowY = y + 43 + ((hIdx - historyScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> { isShuffling = false; config.currentTitle = e.label; SonicPulseClient.getEngine().playTrack(e.url); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 40, rowH).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("☆"), b -> { e.favorite = true; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 38, rowY, 18, rowH).tooltip(Tooltip.of(Text.literal("Add to Favorites"))).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 18, rowY, 18, rowH).tooltip(Tooltip.of(Text.literal("Remove"))).build());
                }
                if (historyScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { historyScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 43, 12, 18).build());
                if (historyOnly.size() > historyScrollOffset + 8) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { historyScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 43 + 7 * rowH, 12, 18).build());
                break;
            case 4: // FAVORITES
                List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(favs.size(), favScrollOffset + listVisibleCount); i++) {
                    final int fIdx = i; SonicPulseConfig.HistoryEntry e = favs.get(fIdx);
                    int rowY = y + 30 + ((fIdx - favScrollOffset) * rowH);
                    if (renamingIndex == fIdx) {
                        renameField = new TextFieldWidget(textRenderer, contentX, rowY, contentW - 20, rowH, Text.literal(""));
                        renameField.setText(e.label); addSelectableChild(renameField);
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> { e.label = renameField.getText(); if(playing && SonicPulseClient.getEngine().getPlayer().getPlayingTrack().getInfo().uri.equals(e.url)) config.currentTitle = e.label; renamingIndex = -1; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 18, rowY, 18, rowH).build());
                    } else {
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> { isShuffling = false; config.currentTitle = e.label; SonicPulseClient.getEngine().playTrack(e.url); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 75, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> { moveFav(e, -1); }).dimensions(contentX + contentW - 73, rowY, 17, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> { moveFav(e, 1); }).dimensions(contentX + contentW - 55, rowY, 17, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> { renamingIndex = fIdx; refreshWidgets(); }).dimensions(contentX + contentW - 36, rowY, 17, rowH).tooltip(Tooltip.of(Text.literal("Rename"))).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { e.favorite = false; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 18, rowY, 17, rowH).tooltip(Tooltip.of(Text.literal("Remove"))).build());
                    }
                }
                if (favScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { favScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 30, 12, 18).build());
                if (favs.size() > favScrollOffset + listVisibleCount) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { favScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 30 + (listVisibleCount - 1) * rowH, 12, 18).build());
                break;
            case 5: // RADIO
                radioUrlField = new TextFieldWidget(textRenderer, contentX, y + 30, contentW - 50, 20, Text.literal("M3U URL")); addSelectableChild(radioUrlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD"), b -> loadRadioM3U(radioUrlField.getText())).dimensions(contentX + contentW - 45, y + 30, 45, 20).build());
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 7); i++) {
                    final int rsIdx = i; String[] rs = radioStreams.get(rsIdx);
                    addDrawableChild(ButtonWidget.builder(Text.literal(rs[0]), b -> { isShuffling = false; config.currentTitle = rs[0]; config.addHistory("Radio", rs[0], rs[1]); SonicPulseClient.getEngine().playTrack(rs[1]); refreshWidgets(); }).dimensions(contentX, y + 55 + ((rsIdx - radioScrollOffset) * rowH), contentW, rowH).build());
                }
                if (radioScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { radioScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 55, 12, 18).build());
                if (radioStreams.size() > radioScrollOffset + 7) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { radioScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 55 + 6 * rowH, 12, 18).build());
                break;
            case 6: // LOCAL
                addDrawableChild(ButtonWidget.builder(Text.literal("SELECT FOLDER"), b -> { 
                    new Thread(() -> { try { System.setProperty("java.awt.headless", "false"); String script = "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if($f.ShowDialog() -eq 'OK'){$f.SelectedPath}"; Process p = Runtime.getRuntime().exec(new String[]{"powershell", "-Command", script}); Scanner s = new Scanner(p.getInputStream()); if(s.hasNextLine()) { String path = s.nextLine().trim(); if(!path.isEmpty()) MinecraftClient.getInstance().execute(() -> { config.localMusicPath = path; SonicPulseConfig.save(); scanLocalFiles(); refreshWidgets(); }); } } catch (Exception e) {} }).start();
                }).dimensions(contentX, y + 25, colW, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("SHUFFLE ALL"), b -> { if (!localFiles.isEmpty()) { List<File> s = new ArrayList<>(localFiles); Collections.shuffle(s); File f = s.get(0); config.currentTitle = f.getName(); SonicPulseClient.getEngine().playTrack(f.getAbsolutePath()); isShuffling = false; refreshWidgets(); } }).dimensions(contentX + colW + 10, y + 25, colW, 18).build());
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    File f = localFiles.get(i);
                    int rowY = y + 48 + ((i - localScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal("♫ " + f.getName()), b -> { isShuffling = false; config.currentTitle = f.getName(); SonicPulseClient.getEngine().playTrack(f.getAbsolutePath()); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 15, rowH).build());
                }
                if (localScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { localScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW - 12, y + 48, 12, 18).build());
                if (localFiles.size() > localScrollOffset + 7) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { localScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW - 12, y + 48 + 6 * rowH, 12, 18).build());
                break;
        }
    }

    private void moveFav(SonicPulseConfig.HistoryEntry e, int dir) {
        int idx = config.history.indexOf(e);
        int target = idx + dir;
        if (target >= 0 && target < config.history.size()) { Collections.swap(config.history, idx, target); SonicPulseConfig.save(); refreshWidgets(); }
    }

    private void scanLocalFiles() {
        localFiles.clear();
        String path = (config.localMusicPath != null && !config.localMusicPath.isEmpty()) ? config.localMusicPath : MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse/music").toString();
        File dir = new File(path);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, n) -> { String l = n.toLowerCase(); return l.endsWith(".mp3") || l.endsWith(".wav") || l.endsWith(".m3u") || l.endsWith(".flac"); });
            if (files != null) Collections.addAll(localFiles, files);
        }
    }

    private void loadRadioM3U(String url) {
        radioStreams.clear();
        new Thread(() -> { try { java.net.URL u = new java.net.URI(url).toURL(); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream())); String l, lt = null; while ((l = r.readLine()) != null) { l = l.trim(); if (l.isEmpty() || l.startsWith("#EXTM3U")) continue; if (l.startsWith("#EXTINF")) { int c = l.indexOf(","); if (c != -1) lt = l.substring(c + 1).trim(); } else if (!l.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : "Station", l}); lt = null; } } r.close(); } catch (Exception e) {} MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) { if (currentTab == 0 && urlField != null && urlField.isMouseOver(mx, my)) urlField.setText(""); return super.mouseClicked(mx, my, b); }

    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10;
        int contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        // Apply Ghost Star logic BEFORE super.render draws the buttons
        if (currentTab == 3) {
            for (int i = 0; i < this.children().size(); i++) {
                if (this.children().get(i) instanceof ButtonWidget btn && btn.getMessage().getString().equals("☆")) {
                    boolean rowHovered = (mx >= contentX && mx <= contentX + contentW && my >= btn.getY() && my <= btn.getY() + 19);
                    btn.setAlpha(rowHovered ? 1.0f : 0.15f);
                }
            }
        }

        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 10, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 10, 0x44FFFFFF);
        
        super.render(context, mx, my, d);
        
        int headerX = x + BOX_WIDTH - 110;
        boolean playing = SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null;
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();
        if (playing && !paused) context.fill(headerX, y + 4, headerX + 20, y + 18, 0x4400FF00);
        if (playing && paused) context.fill(headerX + 22, y + 4, headerX + 42, y + 18, 0x44FFA500);
        if (!playing) context.fill(headerX + 44, y + 4, headerX + 64, y + 18, 0x44FF0000);
        if (isShuffling) context.fill(headerX + 66, y + 4, headerX + 86, y + 18, 0x44ADD8E6);

        if (currentTab == 0) context.drawText(textRenderer, Text.literal("Enter audio URL to stream:"), contentX, y + 33, 0xFFFFFFFF, false);
        if (currentTab == 1) {
            context.fill(contentX + (contentW / 2), y + 35, contentX + (contentW / 2) + 1, y + 130, 0x44FFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("HUD THEME"), contentX + (contentW / 4), y + 23, 0xFFFF00FF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("BAR VISUALS"), contentX + (contentW / 4) * 3, y + 23, 0xFFFF00FF);
        }
        
        context.drawText(textRenderer, Text.literal("SONICPULSE"), x + 10, y + 5, 0xFFFF00FF, false);
        context.drawBorder(x + 4, y + 14 + (currentTab * 22), SIDEBAR_WIDTH - 8, 22, ACTIVE_BORDER);
        if (currentTab == 4 && renameField != null && renamingIndex != -1) renameField.render(context, mx, my, d);
        hudRenderer.render(context, true, 0, 0);
    }
}