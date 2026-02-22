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
import net.minecraft.util.Util;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import org.steve.sonicpulse.client.gui.SonicPulseHud;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigScreen extends Screen {
    private static final int BOX_WIDTH = 360, BOX_HEIGHT = 220, SIDEBAR_WIDTH = 75, ACTIVE_BORDER = 0xFFFF00FF;
    private TextFieldWidget urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, radioScrollOffset = 0, historyScrollOffset = 0, favScrollOffset = 0, localScrollOffset = 0;
    private SonicPulseConfig.HistoryEntry renamingEntry = null;
    private final List<String[]> radioStreams = new ArrayList<>();
    private final List<File> localFiles = new ArrayList<>();
    private final SonicPulseHud hudRenderer = new SonicPulseHud();
    private static final String CURRENT_VERSION = "1.3.4";

    public ConfigScreen() { 
        super(Text.literal("SonicPulse Config")); 
        for(int i=0; i<SonicPulseConfig.PALETTE.length; i++) { 
            if((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.barColor)) colorIndex=i; 
            if((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.titleColor)) titleColorIndex=i;
        }
    }

    private static final String[] TAB_LABELS = {"📡 REMOTE", "🎨 VISUAL", "📐 LAYOUT", "🕒 HIST", "⭐️ FAVS", "📻 RADIO", "♫ LOCAL", "ℹ️ ABOUT"};

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[idx]), b -> { currentTab = idx; renamingEntry = null; if (idx == 6) { localScrollOffset = 0; scanLocalFiles(); } refreshWidgets(); }).dimensions(x + 5, y + 38 + (i * 22), SIDEBAR_WIDTH - 10, 20).build());
        }

        int loaderW = (contentW - 10) / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("LOAD FAVS"), b -> { config.activeMode = SonicPulseConfig.SessionMode.FAVOURITES; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, y + 20, loaderW, 13).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("LOAD HIST"), b -> { config.activeMode = SonicPulseConfig.SessionMode.HISTORY; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + loaderW + 5, y + 20, loaderW, 13).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("LOAD LOCAL"), b -> { config.activeMode = SonicPulseConfig.SessionMode.LOCAL; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + (loaderW * 2) + 10, y + 20, loaderW, 13).build());

        int deckX = x + BOX_WIDTH - 85;
        boolean playing = SonicPulseClient.getEngine().getPlayer().getPlayingTrack() != null;
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();
        addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), b -> {}).dimensions(deckX, y + 4, 20, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(paused ? "▶" : "⏸"), b -> { if (playing) SonicPulseClient.getEngine().getPlayer().setPaused(!paused); refreshWidgets(); }).dimensions(deckX + 21, y + 4, 20, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> { SonicPulseClient.getEngine().stop(); refreshWidgets(); }).dimensions(deckX + 42, y + 4, 20, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), b -> { handleSkip(); }).dimensions(deckX + 63, y + 4, 20, 12).build());

        int rowH = 19, tabY = y + 42; 
        switch (currentTab) {
            case 0:
                urlField = new TextFieldWidget(textRenderer, contentX, tabY + 15, contentW - 85, 20, Text.literal("URL")); urlField.setMaxLength(1024); addDrawableChild(urlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD & PLAY"), b -> { if(!urlField.getText().isEmpty()) { SonicPulseClient.getEngine().playTrack(urlField.getText(), urlField.getText(), "Web"); refreshWidgets(); } }).dimensions(contentX + contentW - 80, tabY + 15, 80, 20).build());
                List<SonicPulseConfig.HistoryEntry> webH = config.history.stream().filter(he -> !he.url.startsWith("file") && !he.url.matches("^[a-zA-Z]:\\\\.*")).sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed()).limit(4).collect(Collectors.toList());
                for (int i = 0; i < webH.size(); i++) {
                    SonicPulseConfig.HistoryEntry e = webH.get(i); int bx = contentX + (i % 2) * (contentW / 2 + 2);
                    addDrawableChild(ButtonWidget.builder(Text.literal("▶ " + textRenderer.trimToWidth(e.label, (contentW / 2) - 25)), b -> { SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type); refreshWidgets(); }).dimensions(bx, tabY + 122 + (i / 2) * 22, (contentW / 2) - 2, 20).build());
                }
                break;
            case 1:
                int colW = (contentW / 2) - 5;
                addDrawableChild(ButtonWidget.builder(Text.literal("HUD: " + (config.hudVisible ? "VISIBLE" : "HIDDEN")), b -> { config.hudVisible = !config.hudVisible; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, tabY + 5, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Skin: " + config.skin.getName()), b -> { config.nextSkin(); refreshWidgets(); }).dimensions(contentX, tabY + 30, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Logo: " + (config.showLogo ? "ON" : "OFF")), b -> { config.showLogo = !config.showLogo; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, tabY + 55, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Track: " + (config.showTrack ? "ON" : "OFF")), b -> { config.showTrack = !config.showTrack; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, tabY + 80, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("BG: " + config.bgEffect.name().replace("_", " ")), b -> { config.nextBgEffect(); refreshWidgets(); }).dimensions(contentX, tabY + 105, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Style: " + config.visStyle.name().replace("_", " ")), b -> { config.nextVisStyle(); refreshWidgets(); }).dimensions(contentX + colW + 10, tabY + 5, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bar: " + SonicPulseConfig.COLOR_NAMES[colorIndex]), b -> { colorIndex = (colorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setColor(SonicPulseConfig.PALETTE[colorIndex]); refreshWidgets(); }).dimensions(contentX + colW + 10, tabY + 30, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Hud Title: " + SonicPulseConfig.COLOR_NAMES[titleColorIndex]), b -> { titleColorIndex = (titleColorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setTitleColor(SonicPulseConfig.PALETTE[titleColorIndex]); refreshWidgets(); }).dimensions(contentX + colW + 10, tabY + 55, colW, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bars: " + (config.showBars ? "ON" : "OFF")), b -> { config.showBars = !config.showBars; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + colW + 10, tabY + 80, colW, 20).build());
                break;
            case 2:
                addDrawableChild(ButtonWidget.builder(Text.literal("Order: " + config.ribbonLayout.getDisplayName()), b -> { config.nextRibbonLayout(); refreshWidgets(); }).dimensions(contentX, tabY + 5, contentW, 20).build());
                addDrawableChild(new SliderWidget(contentX, tabY + 30, contentW, 20, Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%"), (config.hudScale - 0.25) / 0.75) { @Override protected void updateMessage() { setMessage(Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%")); } @Override protected void applyValue() { config.hudScale = (float)(0.25 + (value * 0.75)); SonicPulseConfig.save(); } });
                break;
            case 3:
                List<SonicPulseConfig.HistoryEntry> hSorted = config.history.stream().sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed()).limit(20).collect(Collectors.toList());
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear History"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX, tabY, contentW, 16).build());
                for (int i = historyScrollOffset; i < Math.min(hSorted.size(), historyScrollOffset + 7); i++) {
                    final int hIdx = i; SonicPulseConfig.HistoryEntry e = hSorted.get(hIdx); int rY = tabY + 20 + ((hIdx - historyScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(e.label, contentW - 55)), b -> { SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type); refreshWidgets(); }).dimensions(contentX, rY, contentW - 40, rowH).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal(e.favorite ? "★" : "☆"), b -> { e.favorite = !e.favorite; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 38, rY, 18, rowH).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 18, rY, 18, rowH).build());
                }
                break;
            case 4:
                List<SonicPulseConfig.HistoryEntry> fvs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(fvs.size(), favScrollOffset + 8); i++) {
                    final int fIdx = i; SonicPulseConfig.HistoryEntry e = fvs.get(fIdx); int rY = tabY + ((fIdx - favScrollOffset) * rowH);
                    if (renamingEntry != null && renamingEntry.url.equals(e.url)) {
                        renameField = new TextFieldWidget(textRenderer, contentX + 20, rY, contentW - 40, rowH, Text.literal("")); renameField.setText(e.label); renameField.setFocused(true); addDrawableChild(renameField);
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> { 
                            e.label = renameField.getText(); 
                            AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
                            if (cur != null && cur.getInfo().uri.equals(e.url)) { config.currentTitle = e.label; }
                            renamingEntry = null; SonicPulseConfig.save(); refreshWidgets(); 
                        }).dimensions(contentX + contentW - 18, rY, 18, rowH).build());
                    } else {
                        addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(e.label, contentW - 110)), b -> { SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type); refreshWidgets(); }).dimensions(contentX + 20, rY, contentW - 95, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> { moveFav(e, -1); }).dimensions(contentX + contentW - 73, rY, 17, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> { moveFav(e, 1); }).dimensions(contentX + contentW - 55, rY, 17, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> { renamingEntry = e; refreshWidgets(); }).dimensions(contentX + contentW - 36, rY, 17, rowH).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { e.favorite = false; SonicPulseConfig.save(); refreshWidgets(); }).dimensions(contentX + contentW - 18, rY, 17, rowH).build());
                    }
                }
                break;
            case 5:
                String[] pN = {"BBC", "EU", "TuneIn", "LBC", "News"}; String[] pU = { "https://gist.githubusercontent.com/bpsib/67089b959e4fa898af69fea59ad74bc3/raw/c7255834f326bc6a406080eed104ebaa9d3bc85d/BBC-Radio-HLS.m3u", "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/online_radio.eu/---randomized.m3u", "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/tune_in/---randomized.m3u", "https://media-ssl.musicradio.com/LBCUK", "https://vs-hls-push-ww-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_news_channel_hd/t=3840/v=pv14/b=5070016/main.m3u8" };
                for (int i = 0; i < 5; i++) { final int pIdx = i; addDrawableChild(ButtonWidget.builder(Text.literal(pN[i]), b -> { radioUrlField.setText(pU[pIdx]); loadRadioM3U(pU[pIdx]); }).dimensions(contentX + (i * (contentW/5)), tabY, (contentW/5) - 2, 16).build()); }
                radioUrlField = new TextFieldWidget(textRenderer, contentX, tabY + 20, contentW - 50, 20, Text.literal("M3U URL")); radioUrlField.setMaxLength(1024); addDrawableChild(radioUrlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD"), b -> loadRadioM3U(radioUrlField.getText())).dimensions(contentX + contentW - 45, tabY + 20, 45, 20).build());
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 6); i++) {
                    final int rI = i; String[] rs = radioStreams.get(rI);
                    addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(rs[0], contentW - 15)), b -> { config.activeMode = SonicPulseConfig.SessionMode.RADIO; SonicPulseClient.getEngine().playTrack(rs[1], rs[0], "Radio"); refreshWidgets(); }).dimensions(contentX, tabY + 45 + ((rI - radioScrollOffset) * rowH), contentW, rowH).tooltip(Tooltip.of(Text.literal(rs[0]))).build());
                }
                break;
            case 6:
                addDrawableChild(ButtonWidget.builder(Text.literal("SELECT MUSIC FOLDER"), b -> pickFolder()).dimensions(contentX, tabY + 5, contentW, 16).build());
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    final int lIdx = i; File fl = localFiles.get(lIdx);
                    int rY = tabY + 26 + ((lIdx - localScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal("♫ " + textRenderer.trimToWidth(fl.getName(), contentW-25)), b -> { config.activeMode = SonicPulseConfig.SessionMode.LOCAL; SonicPulseClient.getEngine().playTrack(fl.getAbsolutePath(), fl.getName(), "Local"); refreshWidgets(); }).dimensions(contentX, rY, contentW - 15, rowH).build());
                }
                break;
        }
    }

    private void pickFolder() {
        if (Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS) {
            new Thread(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if($f.ShowDialog() -eq 'OK') { $f.SelectedPath }");
                    Process p = pb.start();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String path = rd.readLine();
                    if (path != null && !path.trim().isEmpty()) { config.localMusicPath = path.trim(); SonicPulseConfig.save(); MinecraftClient.getInstance().execute(this::scanLocalFiles); MinecraftClient.getInstance().execute(this::refreshWidgets); }
                } catch (Exception e) {}
            }).start();
        }
    }

    private void handleSkip() {
        if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES) {
            List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
            if (!favs.isEmpty()) { AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack(); int ni = 0; if (cur != null) { for (int i = 0; i < favs.size(); i++) { if (favs.get(i).url.equals(cur.getInfo().uri)) { ni = (i + 1) % favs.size(); break; } } } SonicPulseConfig.HistoryEntry n = favs.get(ni); SonicPulseClient.getEngine().playTrack(n.url, n.label, n.type); }
        }
        refreshWidgets();
    }

    private void moveFav(SonicPulseConfig.HistoryEntry e, int dir) {
        List<SonicPulseConfig.HistoryEntry> fL = config.getFavoriteHistory();
        int idx = fL.indexOf(e); int tIdx = idx + dir;
        if (tIdx >= 0 && tIdx < fL.size()) { Collections.swap(config.history, config.history.indexOf(fL.get(idx)), config.history.indexOf(fL.get(tIdx))); config.save(); refreshWidgets(); }
    }

    private void scanLocalFiles() {
        localFiles.clear(); 
        String path = config.localMusicPath.isEmpty() ? MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse").resolve("music").toString() : config.localMusicPath;
        File dr = new File(path);
        if (dr.exists() && dr.isDirectory()) { 
            File[] fls = dr.listFiles((d, n) -> { String nm = n.toLowerCase(); return nm.endsWith(".mp3") || nm.endsWith(".wav") || nm.endsWith(".flac"); }); 
            if (fls != null) Collections.addAll(localFiles, fls); 
        }
    }

    private void loadRadioM3U(String radioInput) {
        radioStreams.clear(); new Thread(() -> { try { java.net.URL urlObj = new URI(radioInput).toURL(); java.io.BufferedReader rd = new java.io.BufferedReader(new java.io.InputStreamReader(urlObj.openStream())); String ln, lt = null; while ((ln = rd.readLine()) != null) { ln = ln.trim(); if (ln.isEmpty() || ln.startsWith("#EXTM3U")) continue; if (ln.startsWith("#EXTINF")) { int c = ln.indexOf(","); if (c != -1) lt = ln.substring(c + 1).trim(); } else if (!ln.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : ln, ln}); lt = null; } } rd.close(); } catch (Exception e) {} MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        int d = (v > 0) ? -1 : 1;
        if (currentTab == 3) historyScrollOffset = Math.max(0, Math.min(config.history.size() - 7, historyScrollOffset + d));
        if (currentTab == 4) favScrollOffset = Math.max(0, Math.min(config.getFavoriteHistory().size() - 8, favScrollOffset + d));
        if (currentTab == 5) radioScrollOffset = Math.max(0, Math.min(radioStreams.size() - 6, radioScrollOffset + d));
        if (currentTab == 6) localScrollOffset = Math.max(0, Math.min(localFiles.size() - 7, localScrollOffset + d));
        refreshWidgets(); return true;
    }

    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20, tabY = y + 42;
        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.fill(x, y, x + BOX_WIDTH, y + 38, 0x44000000); 
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 38, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 5, 0x44FFFFFF);
        super.render(context, mx, my, d);
        String trk = (config.currentTitle != null) ? " | " + textRenderer.trimToWidth(config.currentTitle, 150) : "";
        context.drawText(textRenderer, Text.literal("• STATUS: " + config.activeMode.name() + trk), x + 5, y + 6, 0xAAFFFFFF, false);
        context.drawBorder(x + 4, y + 37 + (currentTab * 22), SIDEBAR_WIDTH - 8, 22, ACTIVE_BORDER);

        if (currentTab >= 3 && currentTab <= 6) {
            int total = 0, offset = 0, visible = 0;
            if (currentTab == 3) { total = config.history.size(); offset = historyScrollOffset; visible = 7; }
            if (currentTab == 4) { total = config.getFavoriteHistory().size(); offset = favScrollOffset; visible = 8; }
            if (currentTab == 5) { total = radioStreams.size(); offset = radioScrollOffset; visible = 6; }
            if (currentTab == 6) { total = localFiles.size(); offset = localScrollOffset; visible = 7; }
            if (total > visible) {
                int barX = x + BOX_WIDTH - 4, barY = tabY + 5, barH = BOX_HEIGHT - 52;
                context.fill(barX, barY, barX + 2, barY + barH, 0x44000000);
                int thumbH = Math.max(10, (visible * barH) / total);
                int thumbY = barY + (offset * (barH - thumbH)) / (total - visible);
                context.fill(barX, thumbY, barX + 2, thumbY + thumbH, ACTIVE_BORDER);
            }
        }

        if (currentTab == 0) {
            context.drawText(textRenderer, Text.literal("Enter audio URL to stream:"), contentX, tabY + 3, 0xFFFFFFFF, false);
            context.drawText(textRenderer, Text.literal("§ePlatforms: YouTube, SoundCloud, Bandcamp, Vimeo"), contentX, tabY + 45, 0xBBBBBB, false);
            context.drawText(textRenderer, Text.literal("§eFormats: MP3, FLAC, WAV, WebM, MP4, M3U"), contentX, tabY + 65, 0xBBBBBB, false);
            context.drawText(textRenderer, Text.literal("§aRecently Streamed:"), contentX, tabY + 105, 0xFFFFFFFF, false);
        }
        if (currentTab == 7) {
            int cx = contentX + (BOX_WIDTH - SIDEBAR_WIDTH - 20)/2;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("SONICPULSE"), cx, tabY + 10, 0xFFFF00FF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Version: " + CURRENT_VERSION), cx, tabY + 25, 0xAAAAAA);
        }
        hudRenderer.render(context, true, 0, 0);
    }
}