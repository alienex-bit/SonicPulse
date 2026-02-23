package org.steve.sonicpulse.client.screen;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    private static final Identifier QR_CODE = Identifier.of("sonicpulse", "textures/coffee_qr.png");
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

    private static final String[] TAB_LABELS = {"📡 REMOTE", "🎨 VISUAL", "📐 LAYOUT", "🕒 HIST", "★ FAVS", "📻 RADIO", "♫ LOCAL", "i ABOUT"};
    
    private static final String[] TAB_TOOLTIPS = {
        "Stream audio from web links (YouTube, SoundCloud, etc.)",
        "Customize HUD colors, skins, and reactive effects",
        "Adjust HUD scale and element sequence",
        "View and replay recently streamed tracks",
        "Manage and play your saved favorite tracks",
        "Listen to live internet radio streams",
        "Play music files from your local computer",
        "View mod information and credits"
    };

    private Tooltip tt(String text) {
        return config.showTooltips ? Tooltip.of(Text.literal(text)) : null;
    }

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TAB_LABELS[idx]), b -> { 
                currentTab = idx; renamingEntry = null; if (idx == 6) { localScrollOffset = 0; scanLocalFiles(); } refreshWidgets(); 
            }).dimensions(x + 5, y + 38 + (i * 22), SIDEBAR_WIDTH - 10, 20)
              .tooltip(tt(TAB_TOOLTIPS[idx]))
              .build());
        }

        int playBtnW = 65;
        int playLocalX = x + BOX_WIDTH - playBtnW - 8;
        int playFavsX = playLocalX - playBtnW - 5;

        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> { 
            config.activeMode = SonicPulseConfig.SessionMode.FAVOURITES; SonicPulseConfig.save(); 
            if(!config.getFavoriteHistory().isEmpty()) { SonicPulseConfig.HistoryEntry e = config.getFavoriteHistory().get(0); SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type); }
            currentTab = 4;
            refreshWidgets(); 
        }).dimensions(playFavsX, y + 20, playBtnW, 13).tooltip(tt("Instantly start playing your Favorites list")).build());
        
        addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> { 
            config.activeMode = SonicPulseConfig.SessionMode.LOCAL; SonicPulseConfig.save(); 
            if(localFiles.isEmpty()) scanLocalFiles(); 
            if(!localFiles.isEmpty()) { File fl = localFiles.get(0); SonicPulseClient.getEngine().playTrack(fl.getAbsolutePath(), fl.getName(), "Local"); }
            currentTab = 6; 
            refreshWidgets(); 
        }).dimensions(playLocalX, y + 20, playBtnW, 13).tooltip(tt("Instantly start playing your Local music folder")).build());

        int deckX = x + BOX_WIDTH - 85;
        boolean playing = SonicPulseClient.getEngine().isActiveOrPending();
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();
        
        addDrawableChild(ButtonWidget.builder(Text.literal("⏮"), b -> {}).dimensions(deckX, y + 4, 20, 12).tooltip(tt("Previous Track")).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(playing && !paused ? "⏸" : "▶"), b -> { handlePlayPause(); }).dimensions(deckX + 21, y + 4, 20, 12).tooltip(tt("Play / Pause")).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏹"), b -> { SonicPulseClient.getEngine().stop(); refreshWidgets(); }).dimensions(deckX + 42, y + 4, 20, 12).tooltip(tt("Stop Engine")).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⏭"), b -> { handleSkip(); }).dimensions(deckX + 63, y + 4, 20, 12).tooltip(tt("Next Track")).build());

        int rowH = 19, tabY = y + 42; 
        switch (currentTab) {
            case 0: // REMOTE
                urlField = new TextFieldWidget(textRenderer, contentX, tabY + 15, contentW - 85, 20, Text.literal("URL")); 
                urlField.setMaxLength(1024); 
                urlField.setTooltip(tt("Enter or paste a media URL here (YouTube, SoundCloud, etc.)"));
                addDrawableChild(urlField);
                
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD & PLAY"), b -> { if(!urlField.getText().isEmpty()) { SonicPulseClient.getEngine().playTrack(urlField.getText(), urlField.getText(), "Remote"); refreshWidgets(); } })
                    .dimensions(contentX + contentW - 80, tabY + 15, 80, 20).tooltip(tt("Fetch and immediately play the entered URL")).build());
                
                List<SonicPulseConfig.HistoryEntry> webH = config.history.stream().filter(he -> he.type.equalsIgnoreCase("Remote")).sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed()).limit(4).collect(Collectors.toList());
                for (int i = 0; i < webH.size(); i++) {
                    SonicPulseConfig.HistoryEntry e = webH.get(i); int bx = contentX + (i % 2) * (contentW / 2 + 2);
                    addDrawableChild(ButtonWidget.builder(Text.literal("▶ " + textRenderer.trimToWidth(e.label, (contentW / 2) - 25)), b -> { SonicPulseClient.getEngine().playTrack(e.url, e.label, "Remote"); refreshWidgets(); })
                        .dimensions(bx, tabY + 122 + (i / 2) * 22, (contentW / 2) - 2, 20).tooltip(tt("Click to replay this recent stream")).build());
                }
                break;
                
            case 1: // VISUAL
                int colW = (contentW / 2) - 5;
                
                addDrawableChild(ButtonWidget.builder(Text.literal("HUD: " + (config.hudVisible ? "§aVisible" : "§cHidden")), b -> { config.hudVisible = !config.hudVisible; SonicPulseConfig.save(); refreshWidgets(); })
                    .dimensions(contentX, tabY + 5, colW, 20).tooltip(tt("Enable or disable the entire SonicPulse ribbon")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Skin: " + config.skin.getName().toUpperCase()), b -> { config.nextSkin(); refreshWidgets(); })
                    .dimensions(contentX, tabY + 30, colW, 20).tooltip(tt("Change the background and border styling")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Logo: " + (config.showLogo ? "§aOn" : "§cOff")), b -> { config.showLogo = !config.showLogo; SonicPulseConfig.save(); refreshWidgets(); })
                    .dimensions(contentX, tabY + 55, colW, 20).tooltip(tt("Show or hide the SonicPulse text logo")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Track: " + (config.showTrack ? "§aOn" : "§cOff")), b -> { config.showTrack = !config.showTrack; SonicPulseConfig.save(); refreshWidgets(); })
                    .dimensions(contentX, tabY + 80, colW, 20).tooltip(tt("Show or hide the currently playing track name")).build());
                
                String fxText = config.bgEffect == SonicPulseConfig.BgEffect.OFF ? "§cOff" : config.bgEffect.name();
                addDrawableChild(ButtonWidget.builder(Text.literal("FX: " + fxText), b -> { config.nextBgEffect(); refreshWidgets(); })
                    .dimensions(contentX, tabY + 105, colW, 20).tooltip(tt("Select an audio-reactive background effect")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Style: " + config.visStyle.name().replace("_", " ")), b -> { config.nextVisStyle(); refreshWidgets(); })
                    .dimensions(contentX + colW + 10, tabY + 5, colW, 20).tooltip(tt("Change how the audio equalizer bars are drawn")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Bar Color: " + SonicPulseConfig.COLOR_NAMES[colorIndex].toUpperCase()), b -> { colorIndex = (colorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setColor(SonicPulseConfig.PALETTE[colorIndex]); refreshWidgets(); })
                    .dimensions(contentX + colW + 10, tabY + 30, colW, 20).tooltip(tt("Change the primary color of the equalizer bars")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Title Color: " + SonicPulseConfig.COLOR_NAMES[titleColorIndex].toUpperCase()), b -> { titleColorIndex = (titleColorIndex + 1) % SonicPulseConfig.PALETTE.length; config.setTitleColor(SonicPulseConfig.PALETTE[titleColorIndex]); refreshWidgets(); })
                    .dimensions(contentX + colW + 10, tabY + 55, colW, 20).tooltip(tt("Change the color of the SonicPulse text logo")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Bars: " + (config.showBars ? "§aOn" : "§cOff")), b -> { config.showBars = !config.showBars; SonicPulseConfig.save(); refreshWidgets(); })
                    .dimensions(contentX + colW + 10, tabY + 80, colW, 20).tooltip(tt("Show or hide the audio equalizer bars")).build());
                
                addDrawableChild(ButtonWidget.builder(Text.literal("Tooltips: " + (config.showTooltips ? "§aOn" : "§cOff")), b -> { config.showTooltips = !config.showTooltips; SonicPulseConfig.save(); refreshWidgets(); })
                    .dimensions(contentX + colW + 10, tabY + 105, colW, 20).tooltip(tt("Enable or disable these hover descriptions globally")).build());
                
                if (config.bgEffect != SonicPulseConfig.BgEffect.OFF) {
                    int subY = tabY + 145;
                    if (config.bgEffect == SonicPulseConfig.BgEffect.PULSE) {
                        addDrawableChild(ButtonWidget.builder(Text.literal("Intensity: " + config.pulseIntensity.name()), b -> { config.nextPulseIntensity(); refreshWidgets(); })
                            .dimensions(contentX, subY, colW, 20).tooltip(tt("Adjust the maximum brightness of the flash")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("Decay: " + config.pulseDecay.name()), b -> { config.nextPulseDecay(); refreshWidgets(); })
                            .dimensions(contentX + colW + 10, subY, colW, 20).tooltip(tt("Adjust how quickly the flash fades out")).build());
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.AURA) {
                        addDrawableChild(ButtonWidget.builder(Text.literal("Speed: " + config.auraSpeed.name()), b -> { config.nextAuraSpeed(); refreshWidgets(); })
                            .dimensions(contentX, subY, colW, 20).tooltip(tt("Adjust how fast the gradient colors drift")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("Palette: " + config.auraPalette.name()), b -> { config.nextAuraPalette(); refreshWidgets(); })
                            .dimensions(contentX + colW + 10, subY, colW, 20).tooltip(tt("Toggle between full spectrum or Cyberpunk colors")).build());
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.VHS) {
                        addDrawableChild(ButtonWidget.builder(Text.literal("Glitch: " + config.vhsGlitch.name()), b -> { config.nextVhsGlitch(); refreshWidgets(); })
                            .dimensions(contentX, subY, colW, 20).tooltip(tt("Adjust the intensity of the beat-reactive pixel split")).build());
                        
                        String scanText = config.vhsScanlines == SonicPulseConfig.VhsScanlines.OFF ? "§cOff" : config.vhsScanlines.name();
                        addDrawableChild(ButtonWidget.builder(Text.literal("Scanlines: " + scanText), b -> { config.nextVhsScanlines(); refreshWidgets(); })
                            .dimensions(contentX + colW + 10, subY, colW, 20).tooltip(tt("Adjust the darkness of the static CRT lines")).build());
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.HEATMAP) {
                        addDrawableChild(ButtonWidget.builder(Text.literal("Color Map: " + config.heatmapScale.name().replace("_", " ")), b -> { config.nextHeatmapScale(); refreshWidgets(); })
                            .dimensions(contentX, subY, colW, 20).tooltip(tt("Change the thermal color gradient")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("Spread: " + config.heatmapSpread.name()), b -> { config.nextHeatmapSpread(); refreshWidgets(); })
                            .dimensions(contentX + colW + 10, subY, colW, 20).tooltip(tt("Constrain the heat to the center or let it stretch")).build());
                    }
                }
                break;
                
            case 2: // LAYOUT
                addDrawableChild(ButtonWidget.builder(Text.literal("Element Sequence: " + config.ribbonLayout.getDisplayName()), b -> { config.nextRibbonLayout(); refreshWidgets(); })
                    .dimensions(contentX, tabY + 5, contentW, 20).tooltip(tt("Change the left-to-right order of the logo, track name, and equalizer bars")).build());
                
                SliderWidget scaleSlider = new SliderWidget(contentX, tabY + 30, contentW, 20, Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%"), (config.hudScale - 0.25) / 0.75) { 
                    @Override protected void updateMessage() { setMessage(Text.literal("HUD Scale: " + (int)(config.hudScale * 100) + "%")); } 
                    @Override protected void applyValue() { config.hudScale = (float)(0.25 + (value * 0.75)); SonicPulseConfig.save(); } 
                };
                scaleSlider.setTooltip(tt("Adjust the overall size of the HUD"));
                addDrawableChild(scaleSlider);
                break;
                
            case 3: // HISTORY
                List<SonicPulseConfig.HistoryEntry> hSorted = config.history.stream().filter(e -> !e.favorite).sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed()).limit(20).collect(Collectors.toList());
                addDrawableChild(ButtonWidget.builder(Text.literal("§cClear History"), b -> { config.history.removeIf(e -> !e.favorite); SonicPulseConfig.save(); refreshWidgets(); })
                    .dimensions(contentX, tabY, contentW, 16).tooltip(tt("Permanently delete all non-favorited history entries")).build());
                
                for (int i = historyScrollOffset; i < Math.min(hSorted.size(), historyScrollOffset + 7); i++) {
                    final int hIdx = i; SonicPulseConfig.HistoryEntry e = hSorted.get(hIdx); int rY = tabY + 20 + ((hIdx - historyScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(e.label, contentW - 55)), b -> { SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type); refreshWidgets(); })
                        .dimensions(contentX, rY, contentW - 40, rowH).tooltip(tt("Click to replay this track")).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("☆"), b -> { e.favorite = true; SonicPulseConfig.save(); refreshWidgets(); })
                        .dimensions(contentX + contentW - 38, rY, 18, rowH).tooltip(tt("Save this track to your Favorites list")).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); })
                        .dimensions(contentX + contentW - 18, rY, 18, rowH).tooltip(tt("Remove this track from your history")).build());
                }
                break;
                
            case 4: // FAVES
                List<SonicPulseConfig.HistoryEntry> fvs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(fvs.size(), favScrollOffset + 8); i++) {
                    final int fIdx = i; SonicPulseConfig.HistoryEntry e = fvs.get(fIdx); int rY = tabY + ((fIdx - favScrollOffset) * rowH);
                    if (renamingEntry != null && renamingEntry.url.equals(e.url)) {
                        renameField = new TextFieldWidget(textRenderer, contentX + 20, rY, contentW - 40, rowH, Text.literal("")); 
                        renameField.setText(e.label); 
                        renameField.setFocused(true); 
                        renameField.setTooltip(tt("Enter a new name for this track"));
                        addDrawableChild(renameField);
                        
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> { 
                            e.label = renameField.getText(); AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
                            if (cur != null && cur.getInfo().uri.equals(e.url)) config.currentTitle = e.label;
                            renamingEntry = null; SonicPulseConfig.save(); refreshWidgets(); 
                        }).dimensions(contentX + contentW - 18, rY, 18, rowH).tooltip(tt("Save the new track name")).build());
                    } else {
                        addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(e.label, contentW - 110)), b -> { SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type); refreshWidgets(); })
                            .dimensions(contentX + 20, rY, contentW - 95, rowH).tooltip(tt("Click to play this favorite track")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> { moveFav(e, -1); })
                            .dimensions(contentX + contentW - 73, rY, 17, rowH).tooltip(tt("Move this track up in the list")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> { moveFav(e, 1); })
                            .dimensions(contentX + contentW - 55, rY, 17, rowH).tooltip(tt("Move this track down in the list")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> { renamingEntry = e; refreshWidgets(); })
                            .dimensions(contentX + contentW - 36, rY, 17, rowH).tooltip(tt("Rename this track")).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { e.favorite = false; SonicPulseConfig.save(); refreshWidgets(); })
                            .dimensions(contentX + contentW - 18, rY, 17, rowH).tooltip(tt("Remove this track from your Favorites")).build());
                    }
                }
                break;
                
            case 5: // RADIO
                String[] pN = {"BBC", "EU", "TuneIn", "LBC", "News"}; 
                String[] pU = { "https://gist.githubusercontent.com/bpsib/67089b959e4fa898af69fea59ad74bc3/raw/c7255834f326bc6a406080eed104ebaa9d3bc85d/BBC-Radio-HLS.m3u", "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/online_radio.eu/---randomized.m3u", "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/tune_in/---randomized.m3u", "https://media-ssl.musicradio.com/LBCUK", "https://vs-hls-push-ww-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_news_channel_hd/t=3840/v=pv14/b=5070016/main.m3u8" };
                for (int i = 0; i < 5; i++) { 
                    final int pIdx = i; 
                    addDrawableChild(ButtonWidget.builder(Text.literal(pN[i]), b -> { radioUrlField.setText(pU[pIdx]); loadRadioM3U(pU[pIdx]); })
                        .dimensions(contentX + (i * (contentW/5)), tabY, (contentW/5) - 2, 16).tooltip(tt("Load this preset radio playlist")).build()); 
                }
                
                radioUrlField = new TextFieldWidget(textRenderer, contentX, tabY + 20, contentW - 50, 20, Text.literal("M3U URL")); 
                radioUrlField.setMaxLength(1024); 
                radioUrlField.setTooltip(tt("Enter or paste an M3U stream URL here"));
                addDrawableChild(radioUrlField);
                
                addDrawableChild(ButtonWidget.builder(Text.literal("LOAD"), b -> loadRadioM3U(radioUrlField.getText()))
                    .dimensions(contentX + contentW - 45, tabY + 20, 45, 20).tooltip(tt("Fetch the radio streams from the URL above")).build());
                
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 6); i++) {
                    final int rI = i; String[] rs = radioStreams.get(rI);
                    addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(rs[0], contentW - 15)), b -> { config.activeMode = SonicPulseConfig.SessionMode.RADIO; SonicPulseClient.getEngine().playTrack(rs[1], rs[0], "Radio"); refreshWidgets(); })
                        .dimensions(contentX, tabY + 45 + ((rI - radioScrollOffset) * rowH), contentW, rowH).tooltip(tt("Click to play this radio stream")).build());
                }
                break;
                
            case 6: // LOCAL
                int btnW = 120;
                int btnX = contentX + (contentW - btnW) / 2;
                addDrawableChild(ButtonWidget.builder(Text.literal("Choose Folder"), b -> pickFolder())
                    .dimensions(btnX, tabY + 5, btnW, 16).tooltip(tt("Select a folder on your computer containing audio files")).build());
                
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    final int lIdx = i; File fl = localFiles.get(lIdx);
                    int rY = tabY + 26 + ((lIdx - localScrollOffset) * rowH);
                    addDrawableChild(ButtonWidget.builder(Text.literal("♫ " + textRenderer.trimToWidth(fl.getName(), contentW-25)), b -> { config.activeMode = SonicPulseConfig.SessionMode.LOCAL; SonicPulseClient.getEngine().playTrack(fl.getAbsolutePath(), fl.getName(), "Local"); refreshWidgets(); })
                        .dimensions(contentX, rY, contentW - 15, rowH).tooltip(tt("Click to play this local audio file")).build());
                }
                break;
        }
    }

    private void handlePlayPause() {
        boolean playing = SonicPulseClient.getEngine().isActiveOrPending();
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();
        if (playing) {
            SonicPulseClient.getEngine().getPlayer().setPaused(!paused);
        } else {
            if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES && !config.getFavoriteHistory().isEmpty()) {
                SonicPulseConfig.HistoryEntry e = config.getFavoriteHistory().get(0);
                SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
            } else if (config.activeMode == SonicPulseConfig.SessionMode.HISTORY) {
                List<SonicPulseConfig.HistoryEntry> hist = config.history.stream().filter(e -> !e.favorite).collect(Collectors.toList());
                if (!hist.isEmpty()) {
                    SonicPulseConfig.HistoryEntry e = hist.get(0);
                    SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
                }
            } else if (config.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
                if (localFiles.isEmpty()) scanLocalFiles();
                if (!localFiles.isEmpty()) SonicPulseClient.getEngine().playTrack(localFiles.get(0).getAbsolutePath(), localFiles.get(0).getName(), "Local");
            } else if (config.activeMode == SonicPulseConfig.SessionMode.RADIO && !radioStreams.isEmpty()) {
                String[] rs = radioStreams.get(0);
                SonicPulseClient.getEngine().playTrack(rs[1], rs[0], "Radio");
            }
        }
        refreshWidgets();
    }

    private void pickFolder() {
        if (Util.getOperatingSystem() == Util.OperatingSystem.WINDOWS) {
            new Thread(() -> { try { ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if($f.ShowDialog() -eq 'OK') { $f.SelectedPath }"); Process p = pb.start(); BufferedReader rd = new BufferedReader(new InputStreamReader(p.getInputStream())); String path = rd.readLine(); if (path != null && !path.trim().isEmpty()) { config.localMusicPath = path.trim(); SonicPulseConfig.save(); MinecraftClient.getInstance().execute(this::scanLocalFiles); MinecraftClient.getInstance().execute(this::refreshWidgets); } } catch (Exception e) {} }).start();
        }
    }

    private void handleSkip() {
        AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        String curUri = cur != null ? cur.getInfo().uri : null;

        if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES) {
            List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
            if (!favs.isEmpty()) { 
                int ni = 0; if (curUri != null) { for (int i = 0; i < favs.size(); i++) { if (favs.get(i).url.equals(curUri)) { ni = (i + 1) % favs.size(); break; } } } 
                SonicPulseConfig.HistoryEntry n = favs.get(ni); SonicPulseClient.getEngine().playTrack(n.url, n.label, n.type); 
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.HISTORY) {
            List<SonicPulseConfig.HistoryEntry> hist = config.history.stream().filter(e -> !e.favorite).sorted(Comparator.comparing(e -> e.label)).collect(Collectors.toList());
            if (!hist.isEmpty()) {
                int ni = 0; if (curUri != null) { for (int i = 0; i < hist.size(); i++) { if (hist.get(i).url.equals(curUri)) { ni = (i + 1) % hist.size(); break; } } }
                SonicPulseConfig.HistoryEntry n = hist.get(ni); SonicPulseClient.getEngine().playTrack(n.url, n.label, n.type);
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
            if (localFiles.isEmpty()) scanLocalFiles();
            if (!localFiles.isEmpty()) {
                int ni = 0; 
                if (curUri != null) { 
                    for (int i = 0; i < localFiles.size(); i++) { 
                        File fl = localFiles.get(i);
                        if (curUri.equals(fl.getAbsolutePath()) || curUri.equals(fl.toURI().toString()) || curUri.replace("\\", "/").endsWith(fl.getName())) { 
                            ni = (i + 1) % localFiles.size(); break; 
                        } 
                    } 
                }
                File n = localFiles.get(ni); SonicPulseClient.getEngine().playTrack(n.getAbsolutePath(), n.getName(), "Local");
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.RADIO) {
            if (!radioStreams.isEmpty()) {
                int ni = 0; if (curUri != null) { for (int i = 0; i < radioStreams.size(); i++) { if (radioStreams.get(i)[1].equals(curUri)) { ni = (i + 1) % radioStreams.size(); break; } } }
                String[] n = radioStreams.get(ni); SonicPulseClient.getEngine().playTrack(n[1], n[0], "Radio");
            }
        }
        refreshWidgets();
    }

    private void moveFav(SonicPulseConfig.HistoryEntry e, int dir) {
        List<SonicPulseConfig.HistoryEntry> fL = config.getFavoriteHistory();
        int idx = fL.indexOf(e); int tIdx = idx + dir;
        if (tIdx >= 0 && tIdx < fL.size()) { Collections.swap(config.history, config.history.indexOf(fL.get(idx)), config.history.indexOf(fL.get(tIdx))); config.save(); refreshWidgets(); }
    }

    private void scanLocalFiles() {
        localFiles.clear(); String path = config.localMusicPath.isEmpty() ? MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse").resolve("music").toString() : config.localMusicPath;
        File dr = new File(path); if (dr.exists() && dr.isDirectory()) { File[] fls = dr.listFiles((d, n) -> { String nm = n.toLowerCase(); return nm.endsWith(".mp3") || nm.endsWith(".wav") || nm.endsWith(".flac"); }); if (fls != null) Collections.addAll(localFiles, fls); }
    }

    private void loadRadioM3U(String radioInput) {
        radioStreams.clear(); new Thread(() -> { try { java.net.URL urlObj = new URI(radioInput).toURL(); java.io.BufferedReader rd = new java.io.BufferedReader(new java.io.InputStreamReader(urlObj.openStream())); String ln, lt = null; while ((ln = rd.readLine()) != null) { ln = ln.trim(); if (ln.isEmpty() || ln.startsWith("#EXTM3U")) continue; if (ln.startsWith("#EXTINF")) { int c = ln.indexOf(","); if (c != -1) lt = ln.substring(c + 1).trim(); } else if (!ln.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : ln, ln}); lt = null; } } rd.close(); } catch (Exception e) {} MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        int d = (v > 0) ? -1 : 1;
        if (currentTab == 3) historyScrollOffset = Math.max(0, Math.min((int)config.history.stream().filter(e -> !e.favorite).count() - 7, historyScrollOffset + d));
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
        
        boolean isEnginePlaying = SonicPulseClient.getEngine().isActiveOrPending();
        boolean isEnginePaused = SonicPulseClient.getEngine().getPlayer().isPaused();
        String stateTxt = isEnginePlaying ? (isEnginePaused ? "§e[ ⏸ ]" : "§a[ ♫ ]") : "§7[ ■ ]";
        String trkTxt = (config.currentTitle != null && isEnginePlaying) ? " » §f" + textRenderer.trimToWidth(config.currentTitle, 115) : "";
        context.drawText(textRenderer, Text.literal(stateTxt + " §8(" + config.activeMode.name() + ")" + trkTxt), x + 5, y + 6, 0xFFFFFFFF, false);
        
        context.drawBorder(x + 4, y + 37 + (currentTab * 22), SIDEBAR_WIDTH - 8, 22, ACTIVE_BORDER);

        int playBtnW = 65;
        int playLocalX = x + BOX_WIDTH - playBtnW - 8;
        int playFavsX = playLocalX - playBtnW - 5;
        context.fill(playFavsX, y + 20, playFavsX + playBtnW, y + 33, 0x5555FF55);
        context.fill(playLocalX, y + 20, playLocalX + playBtnW, y + 33, 0x5555FF55);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Play Favs"), playFavsX + playBtnW / 2, y + 23, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Play Local"), playLocalX + playBtnW / 2, y + 23, 0xFFFFFF);

        int deckX = x + BOX_WIDTH - 85;
        if (isEnginePlaying && !isEnginePaused) {
            context.fill(deckX + 21, y + 4, deckX + 41, y + 16, 0x5500FF00); 
        } else if (isEnginePlaying && isEnginePaused) {
            context.fill(deckX + 21, y + 4, deckX + 41, y + 16, 0x55FFA500); 
        } else {
            context.fill(deckX + 42, y + 4, deckX + 62, y + 16, 0x55FF0000); 
        }

        if (currentTab == 1 && config.bgEffect != SonicPulseConfig.BgEffect.OFF) {
            int divY = tabY + 132;
            context.fill(contentX, divY, contentX + contentW, divY + 1, 0x44FFFFFF);
            context.drawText(textRenderer, Text.literal("§e" + config.bgEffect.name() + " TWEAKS"), contentX, divY + 4, 0xFFFFFFFF, false);
        }

        AudioTrack playingTrack = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        String activeUri = playingTrack != null ? playingTrack.getInfo().uri : null;
        if (activeUri != null) {
            int highlightColor = 0xFF55FF55;
            if (currentTab == 3) {
                List<SonicPulseConfig.HistoryEntry> hSorted = config.history.stream().filter(e -> !e.favorite).sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed()).limit(20).collect(Collectors.toList());
                for (int i = historyScrollOffset; i < Math.min(hSorted.size(), historyScrollOffset + 7); i++) {
                    if (hSorted.get(i).url.equals(activeUri)) {
                        int rY = tabY + 20 + ((i - historyScrollOffset) * 19);
                        context.drawBorder(contentX - 1, rY - 1, contentW - 38, 21, highlightColor);
                    }
                }
            } else if (currentTab == 4) {
                List<SonicPulseConfig.HistoryEntry> fvs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(fvs.size(), favScrollOffset + 8); i++) {
                    if (fvs.get(i).url.equals(activeUri)) {
                        int rY = tabY + ((i - favScrollOffset) * 19);
                        context.drawBorder(contentX + 19, rY - 1, contentW - 93, 21, highlightColor);
                    }
                }
            } else if (currentTab == 6) {
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    File fl = localFiles.get(i);
                    if (activeUri.equals(fl.getAbsolutePath()) || activeUri.equals(fl.toURI().toString()) || activeUri.replace("\\", "/").endsWith(fl.getName())) {
                        int rY = tabY + 26 + ((i - localScrollOffset) * 19);
                        context.drawBorder(contentX - 1, rY - 1, contentW - 13, 21, highlightColor);
                    }
                }
            }
        }

        if (currentTab >= 3 && currentTab <= 6) {
            int total = 0, offset = 0, visible = 0;
            if (currentTab == 3) { total = (int)config.history.stream().filter(e -> !e.favorite).count(); offset = historyScrollOffset; visible = 7; }
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
            int centerX = contentX + (contentW / 2);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("§l§nSONICPULSE"), centerX, tabY + 5, 0xFFFF00FF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Professional Media Control Unit"), centerX, tabY + 18, 0xAAAAAA);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Created by: §bSteve Watkins"), centerX, tabY + 40, 0xFFFFFFFF);
            
            int qrX = centerX - 25;
            int qrY = tabY + 55;
            context.drawTexture(RenderLayer::getGuiTextured, QR_CODE, qrX, qrY, 0, 0, 50, 50, 50, 50);
            
            if (config.showTooltips && mx >= qrX && mx <= qrX + 50 && my >= qrY && my <= qrY + 50) {
                context.drawTooltip(textRenderer, Text.literal("Scan this code to donate and buy Steve a coffee!"), mx, my);
            }

            context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Enjoying the vibes? Buy Steve a coffee!"), centerX, tabY + 110, 0xAAFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("§fVersion " + CURRENT_VERSION + " | Up to Date"), centerX, y + BOX_HEIGHT - 15, 0xFFFFFFFF); 
        }
        hudRenderer.render(context, true, 0, 0);
    }
}