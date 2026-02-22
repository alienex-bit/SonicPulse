package org.steve.sonicpulse.client.screen;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
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
    private static final int BOX_WIDTH = 360, BOX_HEIGHT = 220, SIDEBAR_WIDTH = 75, ACTIVE_BORDER = 0xFFFF00FF;
    private TextFieldWidget urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, radioScrollOffset = 0, historyScrollOffset = 0, favScrollOffset = 0, localScrollOffset = 0, renamingIndex = -1;
    private final List<String[]> radioStreams = new ArrayList<>();
    private final List<File> localFiles = new ArrayList<>();
    private final SonicPulseHud hudRenderer = new SonicPulseHud();
    private boolean isShuffling = false;
    private int recentCount = 0;

    private static final String CURRENT_VERSION = "1.3.4";
    private static String fetchedVersion = null;
    private static boolean updateChecked = false;

    public ConfigScreen() { 
        super(Text.literal("SonicPulse Config")); 
        for(int i=0; i<SonicPulseConfig.PALETTE.length; i++) { 
            if((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.barColor)) colorIndex=i; 
            if((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.titleColor)) titleColorIndex=i;
        }

        // --- ASYNC UPDATE CHECKER ---
        if (!updateChecked) {
            updateChecked = true;
            new Thread(() -> {
                try {
                    // We pause for 1 second to simulate a network request to the Modrinth API
                    Thread.sleep(1000); 
                    
                    // In the future, you swap this string with an actual URL text reader
                    fetchedVersion = "1.3.4"; 
                    
                    // Force the UI to refresh to show the button if the screen is still open
                    if (MinecraftClient.getInstance().currentScreen instanceof ConfigScreen) {
                        MinecraftClient.getInstance().execute(this::refreshWidgets);
                    }
                } catch (Exception e) {}
            }).start();
        }
    }

    private static final String[] TAB_LABELS = {"REMOTE", "VISUALS", "LAYOUT", "HISTORY", "FAVORITES", "RADIO", "LOCAL", "ABOUT"};

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;
        recentCount = 0;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[idx]), b -> { 
                currentTab = idx; renamingIndex = -1; 
                if (idx == 6) { localScrollOffset = 0; scanLocalFiles(); }
                refreshWidgets(); 
            }).dimensions(x + 5, y + 28 + (i * 22), SIDEBAR_WIDTH - 10, 20).build());
        }

        int headerX = x + BOX_WIDTH - 110;
        boolean playing = SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null;
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();

        addDrawableChild(new SliderWidget(headerX - 100, y + 6, 95, 14, Text.literal("🔊 " + config.volume + "%"), config.volume / 100.0) { 
            @Override protected void updateMessage() { setMessage(Text.literal("🔊 " + (int)(value * 100) + "%")); } 
            @Override protected void applyValue() { config.volume = (int)(value * 100); SonicPulseConfig.save(); } 
        });

        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> { if (paused) SonicPulseClient.getEngine().getPlayer().setPaused(false); isShuffling = false; refreshWidgets(); }).dimensions(headerX, y + 6, 20, 14).tooltip(Tooltip.of(Text.literal("Play"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏸"), b -> { if (playing) SonicPulseClient.getEngine().getPlayer().setPaused(!paused); refreshWidgets(); }).dimensions(headerX + 22, y + 6, 20, 14).tooltip(Tooltip.of(Text.literal("Pause"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> { SonicPulseClient.getEngine().stop(); isShuffling = false; refreshWidgets(); }).dimensions(headerX + 44, y + 6, 20, 14).tooltip(Tooltip.of(Text.literal("Stop"))).build());
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
        }).dimensions(headerX + 66, y + 6, 20, 14).tooltip(Tooltip.of(Text.literal("Shuffle Favorites"))).build());

        int rowH = 19, listVisibleCount = 9, colW = (contentW / 2) - 5;

        switch (currentTab) {
            case 0: // REMOTE
                urlField = new TextFieldWidget(textRenderer, contentX, y + 55, contentW, 20, Text.literal("URL"));
                urlField.setMaxLength(1024); addSelectableChild(urlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD & PLAY URL"), b -> { 
                    if(!urlField.getText().isEmpty()) { isShuffling = false; SonicPulseClient.getEngine().playTrack(urlField.getText()); refreshWidgets(); }
                }).dimensions(contentX, y + 80, contentW, 20).build());

                List<SonicPulseConfig.HistoryEntry> recents = new ArrayList<>();
                for (int i = config.history.size() - 1; i >= 0 && recents.size() < 4; i--) {
                    SonicPulseConfig.HistoryEntry e = config.history.get(i);
                    if (e.url != null && !e.url.startsWith("file") && !e.url.matches("^[a-zA-Z]:\\\\.*")) {
                        boolean exists = false;
                        for (SonicPulseConfig.HistoryEntry r : recents) if (r.url.equals(e.url)) exists = true;
                        if (!exists) recents.add(e);
                    }
                }
                recentCount = recents.size();
                
                if (recentCount > 0) {
                    for (int i = 0; i < recentCount; i++) {
                        SonicPulseConfig.HistoryEntry e = recents.get(i);
                        int bx = contentX + (i % 2) * (contentW / 2 + 2);
                        int bw = (contentW / 2) - 2;
                        addDrawableChild(ButtonWidget.builder(Text.literal("▶ " + textRenderer.trimToWidth(e.label, bw - 15)), b -> { 
                            isShuffling = false; config.currentTitle = e.label; SonicPulseClient.getEngine().playTrack(e.url); refreshWidgets(); 
                        }).dimensions(bx, y + 138 + (i / 2) * 22, bw, 20).tooltip(Tooltip.of(Text.literal("Track: " + e.label + "\nURL: " + e.url))).build());
                    }
                }
                break;
            case 1: // VISUALS
                addDrawableChild(ButtonWidget.builder(Text.literal("HUD: " + (config.hudVisible ? "VISIBLE" : "HIDDEN")), b -> { config.hudVisible = !config.hudVisible; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 50, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Skin: " + config.skin.getName()), b -> { config.nextSkin(); refreshWidgets(); }).dimensions(contentX, y + 75, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Logo: " + (config.showLogo ? "ON" : "OFF")), b -> { config.showLogo = !config.showLogo; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 100, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Track: " + (config.showTrack ? "ON" : "OFF")), b -> { config.showTrack = !config.showTrack; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 125, colW, 20).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("BG: " + config.bgEffect.name().replace("_", " ")), b -> { config.nextBgEffect(); refreshWidgets(); }).dimensions(contentX, y + 150, colW, 20).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Style: " + config.visStyle.name().replace("_", " ")), b -> { config.nextVisStyle(); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 50, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bar: " + SonicPulseConfig.COLOR_NAMES[colorIndex]), b -> { colorIndex = (colorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setColor(SonicPulseConfig.PALETTE[colorIndex]); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 75, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Hud Title: " + SonicPulseConfig.COLOR_NAMES[titleColorIndex]), b -> { titleColorIndex = (titleColorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setTitleColor(SonicPulseConfig.PALETTE[titleColorIndex]); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 100, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bars: " + (config.showBars ? "ON" : "OFF")), b -> { config.showBars = !config.showBars; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + colW + 10, y + 125, colW, 20).build());
                break;
            case 2: // LAYOUT
                addDrawableChild(ButtonWidget.builder(Text.literal("Order: " + config.ribbonLayout.getDisplayName()), b -> { config.nextRibbonLayout(); refreshWidgets(); }).dimensions(contentX, y + 45, contentW, 20).build());
                addDrawableChild(new SliderWidget(contentX, y + 70, contentW, 20, Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%"), (config.hudScale - 0.25) / 0.75) { 
                    @Override protected void updateMessage() { setMessage(Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%")); } 
                    @Override protected void applyValue() { config.hudScale = (float)(0.25 + (value * 0.75)); SonicPulseConfig.save(); } 
                });
                break;
            case 3: // HISTORY
                List<SonicPulseConfig.HistoryEntry> historyOnly = config.history.stream().filter(e -> !e.favorite).toList();
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear History"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 32, contentW, 16).build());
                for (int i = historyScrollOffset; i < Math.min(historyOnly.size(), historyScrollOffset + 8); i++) {
                    final int hIdx = i; SonicPulseConfig.HistoryEntry e = historyOnly.get(hIdx);
                    int rowY = y + 51 + ((hIdx - historyScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> { isShuffling = false; config.currentTitle = e.label; SonicPulseClient.getEngine().playTrack(e.url); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 40, rowH).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("☆"), b -> { e.favorite = true; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 38, rowY, 18, rowH).tooltip(Tooltip.of(Text.literal("Add to Favorites"))).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 18, rowY, 18, rowH).tooltip(Tooltip.of(Text.literal("Remove"))).build());
                }
                if (historyScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { historyScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 51, 12, 18).build());
                if (historyOnly.size() > historyScrollOffset + 8) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { historyScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 51 + 7 * rowH, 12, 18).build());
                break;
            case 4: // FAVORITES
                List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(favs.size(), favScrollOffset + listVisibleCount); i++) {
                    final int fIdx = i; SonicPulseConfig.HistoryEntry e = favs.get(fIdx);
                    int rowY = y + 34 + ((fIdx - favScrollOffset) * rowH);
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
                if (favScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { favScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 34, 12, 18).build());
                if (favs.size() > favScrollOffset + listVisibleCount) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { favScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 34 + (listVisibleCount - 1) * rowH, 12, 18).build());
                break;
            case 5: // RADIO
                String[] pNames = {"BBC", "EU", "TuneIn", "LBC", "News"};
                String[] pUrls = {
                    "https://gist.githubusercontent.com/bpsib/67089b959e4fa898af69fea59ad74bc3/raw/c7255834f326bc6a406080eed104ebaa9d3bc85d/BBC-Radio-HLS.m3u",
                    "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/online_radio.eu/---randomized.m3u",
                    "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/tune_in/---randomized.m3u",
                    "https://media-ssl.musicradio.com/LBCUK", 
                    "https://vs-hls-push-ww-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_news_channel_hd/t=3840/v=pv14/b=5070016/main.m3u8"
                };
                int pW = contentW / 5;
                for (int i = 0; i < 5; i++) {
                    final int pIdx = i;
                    addDrawableChild(ButtonWidget.builder(Text.literal(pNames[i]), b -> {
                        radioUrlField.setText(pUrls[pIdx]);
                        loadRadioM3U(pUrls[pIdx]);
                    }).dimensions(contentX + (i * pW), y + 32, pW - 2, 20).tooltip(Tooltip.of(Text.literal("Load Preset: " + pNames[i]))).build());
                }

                radioUrlField = new TextFieldWidget(textRenderer, contentX, y + 55, contentW - 50, 20, Text.literal("M3U URL")); 
                radioUrlField.setMaxLength(1024);
                addSelectableChild(radioUrlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD"), b -> loadRadioM3U(radioUrlField.getText())).dimensions(contentX + contentW - 45, y + 55, 45, 20).build());
                
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 6); i++) {
                    final int rsIdx = i; String[] rs = radioStreams.get(rsIdx);
                    String label = textRenderer.trimToWidth(rs[0], contentW - 15);
                    addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> { 
                        isShuffling = false; config.currentTitle = rs[0]; config.addHistory("Radio", rs[0], rs[1]); SonicPulseClient.getEngine().playTrack(rs[1]); refreshWidgets(); 
                    }).dimensions(contentX, y + 80 + ((rsIdx - radioScrollOffset) * rowH), contentW, rowH).tooltip(Tooltip.of(Text.literal(rs[0]))).build());
                }
                if (radioScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { radioScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 80, 12, 18).build());
                if (radioStreams.size() > radioScrollOffset + 6) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { radioScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW + 2, y + 80 + 5 * rowH, 12, 18).build());
                break;
            case 6: // LOCAL
                addDrawableChild(ButtonWidget.builder(Text.literal("SELECT FOLDER"), b -> { 
                    new Thread(() -> { try { System.setProperty("java.awt.headless", "false"); String script = "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if($f.ShowDialog() -eq 'OK'){$f.SelectedPath}"; Process p = Runtime.getRuntime().exec(new String[]{"powershell", "-Command", script}); Scanner s = new Scanner(p.getInputStream()); if(s.hasNextLine()) { String path = s.nextLine().trim(); if(!path.isEmpty()) MinecraftClient.getInstance().execute(() -> { config.localMusicPath = path; SonicPulseConfig.save(); scanLocalFiles(); refreshWidgets(); }); } } catch (Exception e) {} }).start();
                }).dimensions(contentX, y + 32, colW, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("SHUFFLE ALL"), b -> { if (!localFiles.isEmpty()) { List<File> s = new ArrayList<>(localFiles); Collections.shuffle(s); File f = s.get(0); config.currentTitle = f.getName(); SonicPulseClient.getEngine().playTrack(f.getAbsolutePath()); isShuffling = false; refreshWidgets(); } }).dimensions(contentX + colW + 10, y + 32, colW, 18).build());
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    File f = localFiles.get(i);
                    int rowY = y + 55 + ((i - localScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal("♫ " + f.getName()), b -> { isShuffling = false; config.currentTitle = f.getName(); SonicPulseClient.getEngine().playTrack(f.getAbsolutePath()); refreshWidgets(); }).dimensions(contentX, rowY, contentW - 15, rowH).build());
                }
                if (localScrollOffset > 0) addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { localScrollOffset--; refreshWidgets(); }).dimensions(contentX + contentW - 12, y + 55, 12, 18).build());
                if (localFiles.size() > localScrollOffset + 7) addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { localScrollOffset++; refreshWidgets(); }).dimensions(contentX + contentW - 12, y + 55 + 6 * rowH, 12, 18).build());
                break;
            case 7: // ABOUT
                if (fetchedVersion != null && !CURRENT_VERSION.equals(fetchedVersion)) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("§6⭐ UPDATE TO v" + fetchedVersion + " ⭐"), b -> {
                        try { net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create("https://modrinth.com/mod/sonicpulse")); } catch (Exception e) {}
                    }).dimensions(contentX + (contentW / 2) - 85, y + 160, 170, 20).tooltip(Tooltip.of(Text.literal("Opens Modrinth in your browser"))).build());
                }
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
        new Thread(() -> { 
            try { 
                String lowerUrl = url.toLowerCase();
                if (!lowerUrl.endsWith(".m3u") && !lowerUrl.endsWith(".pls") || lowerUrl.endsWith(".m3u8")) {
                    String display = url.replaceFirst("^(http[s]?://www\\.|http[s]?://)", "");
                    radioStreams.add(new String[]{display, url});
                } else {
                    java.net.URL u = new java.net.URI(url).toURL(); 
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream())); 
                    String l, lt = null; 
                    while ((l = r.readLine()) != null) { 
                        l = l.trim(); 
                        if (l.isEmpty() || l.startsWith("#EXTM3U")) continue; 
                        if (l.startsWith("#EXTINF")) { 
                            int c = l.indexOf(","); 
                            if (c != -1) lt = l.substring(c + 1).trim(); 
                        } else if (!l.startsWith("#")) { 
                            String display = lt != null ? lt : l.replaceFirst("^(http[s]?://www\\.|http[s]?://)", "");
                            radioStreams.add(new String[]{display, l}); 
                            lt = null; 
                        } 
                    } 
                    r.close(); 
                }
            } catch (Exception e) {} 
            MinecraftClient.getInstance().execute(this::refreshWidgets); 
        }).start();
    }

    @Override public boolean mouseClicked(double mx, double my, int b) { 
        if (currentTab == 0 && urlField != null && urlField.isMouseOver(mx, my)) urlField.setText(""); 
        if (currentTab == 5 && radioUrlField != null && radioUrlField.isMouseOver(mx, my)) radioUrlField.setText("");
        return super.mouseClicked(mx, my, b); 
    }

    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10;
        int contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        if (currentTab == 3) {
            for (int i = 0; i < this.children().size(); i++) {
                if (this.children().get(i) instanceof ButtonWidget btn && btn.getMessage().getString().equals("☆")) {
                    btn.setAlpha((mx >= contentX && mx <= contentX + contentW && my >= btn.getY() && my <= btn.getY() + 19) ? 1.0f : 0.15f);
                }
            }
        }

        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.fill(x, y, x + BOX_WIDTH, y + 25, 0x44000000); 
        context.fill(x, y + 25, x + BOX_WIDTH, y + 26, config.skin.getBorderColor()); 
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 26, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 5, 0x44FFFFFF);
        
        super.render(context, mx, my, d);
        
        int headerX = x + BOX_WIDTH - 110;
        AudioTrack track = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        boolean playing = track != null;
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();
        
        if (playing && !paused) context.fill(headerX, y + 20, headerX + 20, y + 22, 0xFF00FF00); 
        if (playing && paused) context.fill(headerX + 22, y + 20, headerX + 42, y + 22, 0xFFFFA500); 
        if (!playing) context.fill(headerX + 44, y + 20, headerX + 64, y + 22, 0xFFFF0000); 
        if (isShuffling) context.fill(headerX + 66, y + 20, headerX + 86, y + 22, 0xFF00FFFF); 

        if (playing) {
            String uri = track.getInfo().uri.toLowerCase();
            boolean isLocal = uri.startsWith("file") || uri.matches("^[a-zA-Z]:\\\\.*");
            
            String tag = "[ 🌐 WEB AUDIO ]";
            int tagColor = 0xFF00FFFF;

            if (isLocal) {
                tag = "[ 📁 LOCAL ]";
                tagColor = 0xFFFF00FF;
            } else if (track.getInfo().isStream) {
                if (uri.contains("twitch.tv")) { tag = "[ 📺 TWITCH ]"; tagColor = 0xFFA020F0; }
                else if (uri.contains("youtube.com") || uri.contains("youtu.be")) { tag = "[ 🔴 YT LIVE ]"; tagColor = 0xFFFF0000; }
                else { tag = "[ 📻 STREAM ]"; tagColor = 0xFF00FFFF; }
            } else {
                if (uri.contains("youtube.com") || uri.contains("youtu.be")) { tag = "[ ► YOUTUBE ]"; tagColor = 0xFFFF0000; }
                else if (uri.contains("soundcloud.com")) { tag = "[ ☁ SOUNDCLOUD ]"; tagColor = 0xFFFFA500; }
                else if (uri.contains("bandcamp.com")) { tag = "[ 🎧 BANDCAMP ]"; tagColor = 0xFF00CED1; }
                else if (uri.contains("vimeo.com")) { tag = "[ 🎬 VIMEO ]"; tagColor = 0xFF1E90FF; }
            }

            String timeStr = "";
            if (!track.getInfo().isStream) {
                long pos = track.getPosition() / 1000, dur = track.getDuration() / 1000;
                timeStr = String.format("  %02d:%02d / %02d:%02d", pos/60, pos%60, dur/60, dur%60);
            }
            context.drawText(textRenderer, Text.literal(tag), x + 10, y + 9, tagColor, false);
            context.drawText(textRenderer, Text.literal(timeStr), x + 10 + textRenderer.getWidth(tag), y + 9, 0xFFFFFFFF, false);
        }

        if (currentTab == 0) {
            context.drawText(textRenderer, Text.literal("Enter audio URL to stream:"), contentX, y + 40, 0xFFFFFFFF, false);
            if (urlField != null) urlField.render(context, mx, my, d);
            int ty = y + 110;
            if (recentCount > 0) {
                context.drawText(textRenderer, Text.literal("§aTip: Streamed URLs auto-save to your History!"), contentX, ty, 0xFFFFFFFF, false);
                context.drawText(textRenderer, Text.literal("§eRecently Streamed:"), contentX, ty + 16, 0xFFFFFFFF, false);
            } else {
                context.drawText(textRenderer, Text.literal("§eSupported Platforms:"), contentX, ty, 0xFFFFFFFF, false);
                context.drawText(textRenderer, Text.literal("YouTube, SoundCloud, Bandcamp, Vimeo, Twitch"), contentX, ty + 12, 0xBBBBBB, false);
                context.drawText(textRenderer, Text.literal("§eSupported Audio Formats:"), contentX, ty + 28, 0xFFFFFFFF, false);
                context.drawText(textRenderer, Text.literal("MP3, FLAC, WAV, WebM, MP4/M4A, OGG, AAC, M3U"), contentX, ty + 40, 0xBBBBBB, false);
                context.drawText(textRenderer, Text.literal("§aTip: Streamed URLs auto-save to your History!"), contentX, ty + 60, 0xFFFFFFFF, false);
            }
        }
        
        if (currentTab == 1) {
            context.fill(contentX + (contentW / 2), y + 45, contentX + (contentW / 2) + 1, y + 170, 0x44FFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("HUD THEME"), contentX + (contentW / 4), y + 36, 0xFFFF00FF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("BAR VISUALS"), contentX + (contentW / 4) * 3, y + 36, 0xFFFF00FF);
        }
        
        if (currentTab == 5 && radioUrlField != null) radioUrlField.render(context, mx, my, d);

        if (currentTab == 7) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("SONICPULSE"), contentX + contentW/2, y + 45, 0xFFFF00FF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Created by Steve"), contentX + contentW/2, y + 60, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Version: " + CURRENT_VERSION), contentX + contentW/2, y + 75, 0xAAAAAA);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Stream URLs, play local music, and vibe."), contentX + contentW/2, y + 105, 0xFFFFFFFF);
            
            if (fetchedVersion != null && !CURRENT_VERSION.equals(fetchedVersion)) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("§eA new update is available!"), contentX + contentW/2, y + 140, 0xFFFFFF);
            } else if (fetchedVersion != null) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("§aYou are running the latest version!"), contentX + contentW/2, y + 140, 0xFFFFFF);
            } else {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Checking for updates..."), contentX + contentW/2, y + 140, 0xFFFFFF);
            }
        }
        
        context.drawBorder(x + 4, y + 27 + (currentTab * 22), SIDEBAR_WIDTH - 8, 22, ACTIVE_BORDER);
        if (currentTab == 4 && renameField != null && renamingIndex != -1) renameField.render(context, mx, my, d);
        hudRenderer.render(context, true, 0, 0);
    }
}