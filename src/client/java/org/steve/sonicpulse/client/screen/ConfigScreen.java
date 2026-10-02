package org.steve.sonicpulse.client.screen;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.RenderPipelines;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import org.steve.sonicpulse.client.gui.SonicPulseHud;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import net.fabricmc.loader.api.FabricLoader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.net.HttpURLConnection;

public class ConfigScreen extends Screen {
    private static final int BOX_WIDTH = 360, BOX_HEIGHT = 220, SIDEBAR_WIDTH = 75, ACTIVE_BORDER = 0xFFFF00FF;
    private static final Identifier QR_CODE = Identifier.fromNamespaceAndPath("sonicpulse", "textures/coffee_qr.png");
    private EditBox urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, radioScrollOffset = 0, historyScrollOffset = 0,
            favScrollOffset = 0, localScrollOffset = 0;
    private SonicPulseConfig.HistoryEntry renamingEntry = null;
    private final List<String[]> radioStreams = new ArrayList<>();
    private final List<File> localFiles = new ArrayList<>();
    private final SonicPulseHud hudRenderer = new SonicPulseHud();

    private static final String CURRENT_VERSION = FabricLoader.getInstance().getModContainer("sonicpulse")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.1.0");
    private static String latestVersion = CURRENT_VERSION;
    private static boolean updateChecked = false;

    private static final String[] TAB_LABELS = { "📡 REMOTE", "🎨 VISUAL", "📐 LAYOUT", "🕒 HIST", "★ FAVS", "📻 RADIO",
            "♫ LOCAL", "⚙ ENGINE", "i ABOUT" };

    private static final int[] PASTEL = {
            0xFF82B1FF, // 0 REMOTE: Pastel Sky Blue
            0xFFFF8A80, // 1 VISUAL: Pastel Rose/Pink
            0xFFB9F6CA, // 2 LAYOUT: Pastel Mint Green
            0xFFFFD180, // 3 HIST: Pastel Peach/Orange
            0xFFFFFF8D, // 4 FAVS: Pastel Lemon/Gold
            0xFFB388FF, // 5 RADIO: Pastel Lavender/Purple
            0xFF84FFFF, // 6 LOCAL: Pastel Cyan
            0xFFCFD8DC, // 7 ENGINE: Pastel Slate/Grey
            0xFFF5F5F5 // 8 ABOUT: Pastel Pearl/White
    };

    private static class TintRecord {
        AbstractWidget widget;
        int color;

        TintRecord(AbstractWidget w, int c) {
            widget = w;
            color = c;
        }
    }

    private final List<TintRecord> widgetTints = new ArrayList<>();

    private <T extends AbstractWidget> T addTinted(T widget, int color) {
        addRenderableWidget(widget);
        widgetTints.add(new TintRecord(widget, color));
        return widget;
    }

    private static final String[] TAB_TOOLTIPS = {
            "Stream audio from web links (YouTube, SoundCloud, etc.)",
            "Customize HUD colors, skins, and reactive effects",
            "Adjust HUD dimensions and element sequence",
            "View and replay recently streamed tracks",
            "Manage and play your saved favorite tracks",
            "Listen to live internet radio streams",
            "Play music files from your local computer",
            "Configure audio engine and stream buffering",
            "View mod information and credits"
    };

    public ConfigScreen() {
        super(Component.literal("SonicPulse Config"));
        for (int i = 0; i < SonicPulseConfig.PALETTE.length; i++) {
            if ((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.barColor))
                colorIndex = i;
            if ((0xFF000000 | SonicPulseConfig.PALETTE[i]) == (0xFF000000 | config.titleColor))
                titleColorIndex = i;
        }
        checkUpdates();
    }

    private void checkUpdates() {
        if (updateChecked)
            return;
        new Thread(() -> {
            try {
                URL url = new URI("https://api.modrinth.com/v2/project/sonicpulse/version").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "steve-watkins/sonicpulse/mod-client");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
                    if (array.size() > 0) {
                        latestVersion = array.get(0).getAsJsonObject().get("version_number").getAsString();
                    }
                }
                updateChecked = true;
            } catch (Exception ignored) {
            }
        }).start();
    }

    private Tooltip tt(String text) {
        return config.showTooltips ? Tooltip.create(Component.literal(text)) : null;
    }

    @Override
    protected void init() {
        refreshWidgets();
    }

    private void refreshWidgets() {
        this.clearWidgets();
        this.widgetTints.clear();

        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20;
        int tc = PASTEL[currentTab];

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addTinted(Button.builder(Component.literal(TAB_LABELS[idx]), b -> {
                currentTab = idx;
                renamingEntry = null;
                if (idx == 6) {
                    localScrollOffset = 0;
                    scanLocalFiles();
                }
                refreshWidgets();
            }).bounds(x + 5, y + 40 + (i * 18), SIDEBAR_WIDTH - 10, 16).tooltip(tt(TAB_TOOLTIPS[idx])).build(),
                    PASTEL[idx]);
        }

        int playBtnW = 65, playLocalX = x + BOX_WIDTH - playBtnW - 8, playFavsX = playLocalX - playBtnW - 5;
        addRenderableWidget(Button.builder(Component.literal(""), b -> {
            config.activeMode = SonicPulseConfig.SessionMode.FAVOURITES;
            SonicPulseConfig.save();
            if (!config.getFavoriteHistory().isEmpty()) {
                SonicPulseConfig.HistoryEntry e = config.getFavoriteHistory().get(0);
                SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
            }
            currentTab = 4;
            refreshWidgets();
        }).bounds(playFavsX, y + 20, playBtnW, 13).tooltip(tt("Instantly start playing your Favorites list"))
                .build());

        addRenderableWidget(Button.builder(Component.literal(""), b -> {
            config.activeMode = SonicPulseConfig.SessionMode.LOCAL;
            SonicPulseConfig.save();
            if (localFiles.isEmpty())
                scanLocalFiles();
            if (!localFiles.isEmpty()) {
                File fl = localFiles.get(0);
                SonicPulseClient.getEngine().playTrack(fl.getAbsolutePath(), fl.getName(), "Local");
            }
            currentTab = 6;
            refreshWidgets();
        }).bounds(playLocalX, y + 20, playBtnW, 13).tooltip(tt("Instantly start playing your Local music folder"))
                .build());

        int deckX = x + BOX_WIDTH - 104;
        boolean playing = SonicPulseClient.getEngine().isActiveOrPending();
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();

        addRenderableWidget(Button.builder(Component.literal("⏮"), b -> {
            AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
            SonicPulseClient.getEngine().playPreviousInList(cur);
            refreshWidgets();
        }).bounds(deckX, y + 4, 18, 12).tooltip(tt("Previous Track")).build());

        addRenderableWidget(Button.builder(Component.literal(playing && !paused ? "⏸" : "▶"), b -> {
            handlePlayPause();
        }).bounds(deckX + 19, y + 4, 18, 12).tooltip(tt("Play / Pause")).build());
        addRenderableWidget(Button.builder(Component.literal("⏹"), b -> {
            SonicPulseClient.getEngine().stop();
            refreshWidgets();
        }).bounds(deckX + 38, y + 4, 18, 12).tooltip(tt("Stop Engine")).build());
        addRenderableWidget(Button.builder(Component.literal("⏭"), b -> {
            handleSkip();
        }).bounds(deckX + 57, y + 4, 18, 12).tooltip(tt("Next Track")).build());

        addTinted(Button.builder(Component.literal("✕"), b -> this.onClose())
                .bounds(x + BOX_WIDTH - 21, y + 4, 16, 12)
                .tooltip(tt("Close (ESC or P)"))
                .build(), 0xFFFF5555);

        int rowH = 19, tabY = y + 42;
        switch (currentTab) {
            case 0: // REMOTE
                urlField = new EditBox(this.font, contentX, tabY + 15, contentW - 85, 20,
                        Component.literal("URL"));
                urlField.setMaxLength(1024);
                urlField.setTooltip(tt("Enter or paste a media URL here (YouTube, SoundCloud, etc.)"));
                addTinted(urlField, tc);

                addTinted(Button.builder(Component.literal("LOAD & PLAY"), b -> {
                    if (!urlField.getValue().isEmpty()) {
                        config.activeMode = SonicPulseConfig.SessionMode.REMOTE;
                        SonicPulseClient.getEngine().playTrack(urlField.getValue(), urlField.getValue(), "Remote");
                        refreshWidgets();
                    }
                }).bounds(contentX + contentW - 80, tabY + 15, 80, 20)
                        .tooltip(tt("Fetch and immediately play the entered URL")).build(), tc);

                List<SonicPulseConfig.HistoryEntry> webH = config.history.stream()
                        .filter(he -> he.url != null && !he.url.toLowerCase().startsWith("file")
                                && !he.url.matches("^[a-zA-Z]:\\\\.*"))
                        .sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed())
                        .limit(4).collect(Collectors.toList());

                for (int i = 0; i < webH.size(); i++) {
                    SonicPulseConfig.HistoryEntry e = webH.get(i);
                    int bx = contentX + (i % 2) * (contentW / 2 + 2);
                    addTinted(Button
                            .builder(Component.literal("▶ " + this.font.plainSubstrByWidth(e.label, (contentW / 2) - 25)), b -> {
                                config.activeMode = SonicPulseConfig.SessionMode.REMOTE;
                                SonicPulseClient.getEngine().playTrack(e.url, e.label, "Remote");
                                refreshWidgets();
                            }).bounds(bx, tabY + 122 + (i / 2) * 22, (contentW / 2) - 2, 20)
                            .tooltip(tt("Click to replay this recent stream")).build(), tc);
                }
                break;

            case 1: // VISUAL
                int colW = (contentW / 2) - 5;
                addTinted(Button
                        .builder(Component.literal("HUD: " + (config.hudVisible ? "§aVisible" : "§cHidden")), b -> {
                            config.hudVisible = !config.hudVisible;
                            SonicPulseConfig.save();
                            refreshWidgets();
                        }).bounds(contentX, tabY + 5, colW, 20)
                        .tooltip(tt("Enable or disable the entire SonicPulse ribbon")).build(), tc);
                addTinted(Button.builder(Component.literal("Skin: " + config.skin.getName()), b -> {
                    config.nextSkin();
                    refreshWidgets();
                }).bounds(contentX, tabY + 30, colW, 20).tooltip(tt("Change the background and border styling"))
                        .build(), tc);
                addTinted(Button.builder(Component.literal("Logo: " + (config.showLogo ? "§aOn" : "§cOff")), b -> {
                    config.showLogo = !config.showLogo;
                    SonicPulseConfig.save();
                    refreshWidgets();
                }).bounds(contentX, tabY + 55, colW, 20).tooltip(tt("Show or hide the SonicPulse text logo"))
                        .build(), tc);
                addTinted(Button.builder(Component.literal("Track: " + (config.showTrack ? "§aOn" : "§cOff")), b -> {
                    config.showTrack = !config.showTrack;
                    SonicPulseConfig.save();
                    refreshWidgets();
                }).bounds(contentX, tabY + 80, colW, 20)
                        .tooltip(tt("Show or hide the currently playing track name")).build(), tc);
                String fxText = config.bgEffect == SonicPulseConfig.BgEffect.OFF ? "§cOff" : config.bgEffect.name();
                addTinted(Button.builder(Component.literal("FX: " + fxText), b -> {
                    config.nextBgEffect();
                    refreshWidgets();
                }).bounds(contentX, tabY + 105, colW, 20).tooltip(tt("Select an audio-reactive background effect"))
                        .build(), tc);

                addTinted(
                        Button.builder(Component.literal("Style: " + config.visStyle.name().replace("_", " ")), b -> {
                            config.nextVisStyle();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, tabY + 5, colW, 20)
                                .tooltip(tt("Change how the audio equalizer bars are drawn")).build(),
                        tc);
                addTinted(Button.builder(Component.literal("Bar: " + SonicPulseConfig.COLOR_NAMES[colorIndex]), b -> {
                    colorIndex = (colorIndex + 1) % SonicPulseConfig.PALETTE.length;
                    config.setColor(SonicPulseConfig.PALETTE[colorIndex]);
                    refreshWidgets();
                }).bounds(contentX + colW + 10, tabY + 30, colW, 20)
                        .tooltip(tt("Change the primary color of the equalizer bars")).build(), tc);
                addTinted(Button
                        .builder(Component.literal("Title: " + SonicPulseConfig.COLOR_NAMES[titleColorIndex]), b -> {
                            titleColorIndex = (titleColorIndex + 1) % SonicPulseConfig.PALETTE.length;
                            config.setTitleColor(SonicPulseConfig.PALETTE[titleColorIndex]);
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, tabY + 55, colW, 20)
                        .tooltip(tt("Change the color of the SonicPulse text logo")).build(), tc);
                addTinted(Button.builder(Component.literal("Bars: " + (config.showBars ? "§aOn" : "§cOff")), b -> {
                    config.showBars = !config.showBars;
                    SonicPulseConfig.save();
                    refreshWidgets();
                }).bounds(contentX + colW + 10, tabY + 80, colW, 20)
                        .tooltip(tt("Show or hide the audio equalizer bars")).build(), tc);
                addTinted(Button
                        .builder(Component.literal("Tooltips: " + (config.showTooltips ? "§aOn" : "§cOff")), b -> {
                            config.showTooltips = !config.showTooltips;
                            SonicPulseConfig.save();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, tabY + 105, colW, 20)
                        .tooltip(tt("Enable or disable these hover descriptions globally")).build(), tc);

                if (config.bgEffect != SonicPulseConfig.BgEffect.OFF) {
                    int subY = tabY + 145;
                    if (config.bgEffect == SonicPulseConfig.BgEffect.PULSE) {
                        addTinted(Button.builder(Component.literal("Int: " + config.pulseIntensity.name()), b -> {
                            config.nextPulseIntensity();
                            refreshWidgets();
                        }).bounds(contentX, subY, colW, 20)
                                .tooltip(tt("Adjust the maximum brightness of the flash")).build(), tc);
                        addTinted(Button.builder(Component.literal("Decay: " + config.pulseDecay.name()), b -> {
                            config.nextPulseDecay();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, subY, colW, 20)
                                .tooltip(tt("Adjust how quickly the flash fades out")).build(), tc);
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.AURA) {
                        addTinted(Button.builder(Component.literal("Spd: " + config.auraSpeed.name()), b -> {
                            config.nextAuraSpeed();
                            refreshWidgets();
                        }).bounds(contentX, subY, colW, 20).tooltip(tt("Adjust how fast the gradient colors drift"))
                                .build(), tc);
                        addTinted(Button.builder(Component.literal("Hue: " + config.auraPalette.name()), b -> {
                            config.nextAuraPalette();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, subY, colW, 20)
                                .tooltip(tt("Toggle between full spectrum or Cyberpunk colors")).build(), tc);
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.VHS) {
                        addTinted(Button.builder(Component.literal("Lvl: " + config.vhsGlitch.name()), b -> {
                            config.nextVhsGlitch();
                            refreshWidgets();
                        }).bounds(contentX, subY, colW, 20)
                                .tooltip(tt("Adjust the intensity of the beat-reactive pixel split")).build(), tc);
                        String scanText = config.vhsScanlines == SonicPulseConfig.VhsScanlines.OFF ? "§cOff"
                                : config.vhsScanlines.name();
                        addTinted(Button.builder(Component.literal("CRT: " + scanText), b -> {
                            config.nextVhsScanlines();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, subY, colW, 20)
                                .tooltip(tt("Adjust the darkness of the static CRT lines")).build(), tc);
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.HEATMAP) {
                        addTinted(Button.builder(Component.literal("Map: " + config.heatmapScale.name()), b -> {
                            config.nextHeatmapScale();
                            refreshWidgets();
                        }).bounds(contentX, subY, colW, 20).tooltip(tt("Change the thermal color gradient"))
                                .build(), tc);
                        addTinted(Button.builder(Component.literal("Rad: " + config.heatmapSpread.name()), b -> {
                            config.nextHeatmapSpread();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, subY, colW, 20)
                                .tooltip(tt("Constrain the heat to the center or let it stretch")).build(), tc);
                    } else if (config.bgEffect == SonicPulseConfig.BgEffect.STARFIELD) {
                        addTinted(Button.builder(Component.literal("Sparks: " + config.sparkCount.name()), b -> {
                            config.nextSparkCount();
                            refreshWidgets();
                        }).bounds(contentX, subY, colW, 20)
                                .tooltip(tt("FEW = 4, NORMAL = 8, STORM = 16 sparks per bass hit")).build(), tc);
                        addTinted(Button.builder(Component.literal("Decay: " + config.sparkDecay.name()), b -> {
                            config.nextSparkDecay();
                            refreshWidgets();
                        }).bounds(contentX + colW + 10, subY, colW, 20)
                                .tooltip(tt("SNAP = particles vanish fast, DRIFT = linger and fade slowly")).build(),
                                tc);
                    }
                }
                break;

            case 2: // LAYOUT
                addTinted(Button
                        .builder(Component.literal("Element Sequence: " + config.ribbonLayout.getDisplayName()), b -> {
                            config.nextRibbonLayout();
                            refreshWidgets();
                        })
                        .bounds(contentX, tabY + 5, contentW, 20)
                        .tooltip(tt("Change the left-to-right order of the logo, track name, and equalizer bars"))
                        .build(), tc);

                AbstractSliderButton scaleSlider = new AbstractSliderButton(contentX, tabY + 30, contentW, 20,
                        Component.literal("HUD Scale: " + (int) (config.hudScale * 100) + "%"),
                        (config.hudScale - 0.25) / 0.75) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal("HUD Scale: " + (int) (config.hudScale * 100) + "%"));
                    }

                    @Override
                    protected void applyValue() {
                        config.hudScale = (float) (0.25 + (value * 0.75));
                        SonicPulseConfig.save();
                    }
                };
                scaleSlider.setTooltip(tt("Adjust the overall size of the HUD"));
                addTinted(scaleSlider, tc);

                AbstractSliderButton widthSlider = new AbstractSliderButton(contentX, tabY + 55, contentW, 20,
                        Component.literal("HUD Width: " + (int) (config.hudWidth * 100) + "%"),
                        (config.hudWidth - 0.25) / 0.75) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal("HUD Width: " + (int) (config.hudWidth * 100) + "%"));
                    }

                    @Override
                    protected void applyValue() {
                        config.hudWidth = (float) (0.25 + (value * 0.75));
                        SonicPulseConfig.save();
                    }
                };
                widthSlider.setTooltip(tt("Shrink and center the HUD horizontally to fit between other mods"));
                addTinted(widthSlider, tc);
                break;

            case 3: // HISTORY
                List<SonicPulseConfig.HistoryEntry> hSorted = config.history.stream().filter(e -> !e.favorite)
                        .sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed())
                        .limit(20).collect(Collectors.toList());
                addTinted(Button.builder(Component.literal("§cClear History"), b -> {
                    config.history.removeIf(e -> !e.favorite);
                    SonicPulseConfig.save();
                    refreshWidgets();
                })
                        .bounds(contentX, tabY, contentW, 16)
                        .tooltip(tt("Permanently delete all non-favorited history entries")).build(), tc);

                for (int i = historyScrollOffset; i < Math.min(hSorted.size(), historyScrollOffset + 7); i++) {
                    final int hIdx = i;
                    SonicPulseConfig.HistoryEntry e = hSorted.get(hIdx);
                    int rY = tabY + 20 + ((hIdx - historyScrollOffset) * rowH);
                    addTinted(
                            Button.builder(Component.literal(this.font.plainSubstrByWidth(e.label, contentW - 55)), b -> {
                                config.activeMode = SonicPulseConfig.SessionMode.HISTORY;
                                SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
                                refreshWidgets();
                            }).bounds(contentX, rY, contentW - 40, rowH).tooltip(tt("Click to replay this track"))
                                    .build(),
                            tc);
                    addTinted(Button.builder(Component.literal("☆"), b -> {
                        e.favorite = true;
                        SonicPulseConfig.save();
                        refreshWidgets();
                    })
                            .bounds(contentX + contentW - 38, rY, 18, rowH)
                            .tooltip(tt("Save this track to your Favorites list")).build(), tc);
                    addTinted(Button.builder(Component.literal("X"), b -> {
                        config.history.remove(e);
                        SonicPulseConfig.save();
                        refreshWidgets();
                    })
                            .bounds(contentX + contentW - 18, rY, 18, rowH)
                            .tooltip(tt("Remove this track from your history")).build(), tc);
                }
                break;

            case 4: // FAVES
                List<SonicPulseConfig.HistoryEntry> fvs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(fvs.size(), favScrollOffset + 8); i++) {
                    final int fIdx = i;
                    SonicPulseConfig.HistoryEntry e = fvs.get(fIdx);
                    int rY = tabY + ((fIdx - favScrollOffset) * rowH);
                    if (renamingEntry != null && renamingEntry.url.equals(e.url)) {
                        renameField = new EditBox(this.font, contentX + 20, rY, contentW - 40, rowH,
                                Component.literal(""));
                        renameField.setValue(e.label);
                        renameField.setFocused(true);
                        renameField.setTooltip(tt("Enter a new name for this track"));
                        addTinted(renameField, tc);

                        addTinted(Button.builder(Component.literal("✔"), b -> {
                            e.label = renameField.getValue();
                            AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
                            if (cur != null && cur.getInfo().uri.equals(e.url))
                                config.currentTitle = e.label;
                            renamingEntry = null;
                            SonicPulseConfig.save();
                            refreshWidgets();
                        }).bounds(contentX + contentW - 18, rY, 18, rowH).tooltip(tt("Save the new track name"))
                                .build(), tc);
                    } else {
                        addTinted(Button
                                .builder(Component.literal(this.font.plainSubstrByWidth(e.label, contentW - 95)), b -> {
                                    SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
                                    refreshWidgets();
                                })
                                .bounds(contentX + 20, rY, contentW - 95, rowH)
                                .tooltip(tt("Click to play this favorite track")).build(), tc);
                        addTinted(Button.builder(Component.literal("↑"), b -> {
                            moveFav(e, -1);
                        })
                                .bounds(contentX + contentW - 73, rY, 17, rowH)
                                .tooltip(tt("Move this track up in the list")).build(), tc);
                        addTinted(Button.builder(Component.literal("↓"), b -> {
                            moveFav(e, 1);
                        })
                                .bounds(contentX + contentW - 55, rY, 17, rowH)
                                .tooltip(tt("Move this track down in the list")).build(), tc);
                        addTinted(Button.builder(Component.literal("R"), b -> {
                            renamingEntry = e;
                            refreshWidgets();
                        })
                                .bounds(contentX + contentW - 36, rY, 17, rowH).tooltip(tt("Rename this track"))
                                .build(), tc);
                        addTinted(Button.builder(Component.literal("X"), b -> {
                            e.favorite = false;
                            SonicPulseConfig.save();
                            refreshWidgets();
                        })
                                .bounds(contentX + contentW - 18, rY, 17, rowH)
                                .tooltip(tt("Remove this track from your Favorites")).build(), tc);
                    }
                }
                break;

            case 5: // RADIO
                String bbcUrl = "https://gist.githubusercontent.com/bpsib/67089b959e4fa898af69fea59ad74bc3/raw/c7255834f326bc6a406080eed104ebaa9d3bc85d/BBC-Radio-HLS.m3u";
                addTinted(Button.builder(Component.literal("BBC"), b -> {
                    radioUrlField.setValue(bbcUrl);
                    loadRadioM3U(bbcUrl);
                })
                        .bounds(contentX, tabY, (contentW / 5) - 2, 16).tooltip(tt("Load BBC Radio presets"))
                        .build(), tc);

                for (int i = 0; i < 4; i++) {
                    final int pIdx = i;
                    String pName = config.radioPresetNames[pIdx];
                    String pUrl = config.radioPresetUrls[pIdx];
                    addTinted(Button
                            .builder(Component.literal(this.font.plainSubstrByWidth(pName, (contentW / 5) - 6)), b -> {
                                if (pUrl != null && !pUrl.isEmpty()) {
                                    radioUrlField.setValue(pUrl);
                                    loadRadioM3U(pUrl);
                                }
                            }).bounds(contentX + ((i + 1) * (contentW / 5)), tabY, (contentW / 5) - 2, 16)
                            .tooltip(tt(pUrl.isEmpty() ? "Empty Slot" : "Load " + pName)).build(), tc);
                }

                radioUrlField = new EditBox(this.font, contentX, tabY + 20, contentW - 50, 20,
                        Component.literal("M3U URL"));
                radioUrlField.setMaxLength(1024);
                if (config.lastRadioUrl != null)
                    radioUrlField.setValue(config.lastRadioUrl);
                radioUrlField.setTooltip(tt("Enter or paste an M3U stream URL here"));
                addTinted(radioUrlField, tc);

                addTinted(Button.builder(Component.literal("LOAD"), b -> {
                    config.lastRadioUrl = radioUrlField.getValue();
                    SonicPulseConfig.save();
                    loadRadioM3U(radioUrlField.getValue());
                })
                        .bounds(contentX + contentW - 45, tabY + 20, 45, 20)
                        .tooltip(tt("Fetch the radio streams from the URL above")).build(), tc);

                Button fixedBtn = Button.builder(Component.literal("Fixed"), b -> {
                })
                        .bounds(contentX, tabY + 45, (contentW / 5) - 2, 16)
                        .tooltip(tt("BBC presets are fixed and cannot be overwritten")).build();
                fixedBtn.active = false;
                addTinted(fixedBtn, tc);

                for (int i = 0; i < 4; i++) {
                    final int sIdx = i;
                    addTinted(Button.builder(Component.literal("Save " + (sIdx + 1)), b -> {
                        String currentUrl = radioUrlField.getValue();
                        if (!currentUrl.isEmpty()) {
                            config.radioPresetUrls[sIdx] = currentUrl;
                            if (!radioStreams.isEmpty()) {
                                config.radioPresetNames[sIdx] = this.font.plainSubstrByWidth(radioStreams.get(0)[0], 40);
                            } else {
                                config.radioPresetNames[sIdx] = "Preset " + (sIdx + 1);
                            }
                            SonicPulseConfig.save();
                            refreshWidgets();
                        }
                    }).bounds(contentX + ((i + 1) * (contentW / 5)), tabY + 45, (contentW / 5) - 2, 16)
                            .tooltip(tt("Save current URL to slot " + (sIdx + 1))).build(), tc);
                }

                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + 5); i++) {
                    final int rI = i;
                    String[] rs = radioStreams.get(rI);
                    addTinted(Button.builder(Component.literal(this.font.plainSubstrByWidth(rs[0], contentW - 15)), b -> {
                        config.activeMode = SonicPulseConfig.SessionMode.RADIO;
                        SonicPulseClient.getEngine().playTrack(rs[1], rs[0], "Radio");
                        refreshWidgets();
                    })
                            .bounds(contentX, tabY + 65 + ((rI - radioScrollOffset) * rowH), contentW, rowH)
                            .tooltip(tt("Click to play this radio stream")).build(), tc);
                }
                break;

            case 6: // LOCAL
                int btnW = 120;
                int btnX = contentX + (contentW - btnW) / 2;
                addTinted(Button.builder(Component.literal("Choose Folder"), b -> pickFolder())
                        .bounds(btnX, tabY + 5, btnW, 16)
                        .tooltip(tt("Select a folder on your computer containing audio files")).build(), tc);

                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    final int lIdx = i;
                    File fl = localFiles.get(lIdx);
                    int rY = tabY + 26 + ((lIdx - localScrollOffset) * rowH);
                    addTinted(Button
                            .builder(Component.literal("♫ " + this.font.plainSubstrByWidth(fl.getName(), contentW - 25)), b -> {
                                config.activeMode = SonicPulseConfig.SessionMode.LOCAL;
                                SonicPulseClient.getEngine().playTrack(fl.getAbsolutePath(), fl.getName(), "Local");
                                refreshWidgets();
                            })
                            .bounds(contentX, rY, contentW - 15, rowH)
                            .tooltip(tt("Click to play this local audio file")).build(), tc);
                }
                break;

            case 7: // ENGINE
                int colW7 = (contentW / 2) - 5;
                addTinted(Button.builder(
                        Component.literal(
                                "Stream Buffering: " + (config.enableStreamBuffering ? "§aEnabled" : "§cDisabled")),
                        b -> {
                            config.enableStreamBuffering = !config.enableStreamBuffering;
                            SonicPulseConfig.save();
                            refreshWidgets();
                        }).bounds(contentX, tabY + 5, contentW, 20)
                        .tooltip(tt("Enable a reservoir to prevent stuttering on slow internet")).build(), tc);

                AbstractSliderButton bufSlider = new AbstractSliderButton(contentX, tabY + 30, contentW, 20,
                        Component.literal("Buffer Duration: " + config.streamBufferSeconds + "s"),
                        config.streamBufferSeconds / 15.0) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal("Buffer Duration: " + config.streamBufferSeconds + "s"));
                    }

                    @Override
                    protected void applyValue() {
                        config.streamBufferSeconds = (int) (value * 15);
                        SonicPulseConfig.save();
                    }
                };
                bufSlider.setTooltip(tt("Seconds to pre-load before playing (0-15s)"));
                addTinted(bufSlider, tc);

                addTinted(Button
                        .builder(Component.literal("Show Buffer Bar: " + (config.showBufferingBar ? "§aOn" : "§cOff")),
                                b -> {
                                    config.showBufferingBar = !config.showBufferingBar;
                                    SonicPulseConfig.save();
                                    refreshWidgets();
                                })
                        .bounds(contentX, tabY + 55, contentW, 20)
                        .tooltip(tt("Display the loading progress line at the bottom of the HUD")).build(), tc);

                AbstractSliderButton bassSlider = new AbstractSliderButton(contentX, tabY + 100, colW7, 20,
                        Component.literal("Bass: " + (int) (config.eqBass * 100) + "%"), (config.eqBass + 1.0) / 2.0) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal("Bass: " + (int) (config.eqBass * 100) + "%"));
                    }

                    @Override
                    protected void applyValue() {
                        config.eqBass = (float) (value * 2.0 - 1.0);
                        SonicPulseConfig.save();
                    }
                };
                bassSlider.setTooltip(tt("Boost or cut low frequency bass (-100% to +100%)"));
                addTinted(bassSlider, tc);

                AbstractSliderButton trebleSlider = new AbstractSliderButton(contentX + colW7 + 10, tabY + 100, colW7, 20,
                        Component.literal("Treble: " + (int) (config.eqTreble * 100) + "%"), (config.eqTreble + 1.0) / 2.0) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal("Treble: " + (int) (config.eqTreble * 100) + "%"));
                    }

                    @Override
                    protected void applyValue() {
                        config.eqTreble = (float) (value * 2.0 - 1.0);
                        SonicPulseConfig.save();
                    }
                };
                trebleSlider.setTooltip(tt("Boost or cut high frequency treble (-100% to +100%)"));
                addTinted(trebleSlider, tc);

                AbstractSliderButton widthSl = new AbstractSliderButton(contentX, tabY + 125, contentW, 20,
                        Component.literal("Stereo Width: " + (int) (config.stereoWidth * 100) + "%"),
                        (config.stereoWidth / 2.0)) {
                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal("Stereo Width: " + (int) (config.stereoWidth * 100) + "%"));
                    }

                    @Override
                    protected void applyValue() {
                        config.stereoWidth = (float) (value * 2.0);
                        SonicPulseConfig.save();
                    }
                };
                widthSl.setTooltip(tt("Expand the soundstage (0% = Mono, 100% = Normal, 200% = Super Wide)"));
                addTinted(widthSl, tc);

                // MASTER UNDERWATER MUFFLE TOGGLE
                addTinted(Button.builder(
                        Component.literal("Underwater Auto-Muffle: " + (config.underwaterMuffle ? "§aON" : "§cOFF")), b -> {
                            config.underwaterMuffle = !config.underwaterMuffle;
                            SonicPulseConfig.save();
                            refreshWidgets();
                        }).bounds(contentX, tabY + 150, contentW, 20)
                        .tooltip(tt("Toggle automatic low-pass filtering when submerged underwater.")).build(), tc);
                break;

            case 8: // ABOUT
                int coffeeBtnW = 150;
                int coffeeBtnX = contentX + (contentW / 2) - (coffeeBtnW / 2);
                try {
                    URI coffeeUri = URI.create("https://www.paypal.com/qrcodes/managed/6fa67be8-dd99-4e6b-8fd3-fcc8ee841bda?utm_source=consweb_more");
                    addTinted(Button.builder(Component.literal("☕ Buy Steve a Coffee"),
                            ConfirmLinkScreen.confirmLink(this, coffeeUri))
                            .bounds(coffeeBtnX, tabY + 130, coffeeBtnW, 20)
                            .tooltip(tt("Open donation link in browser"))
                            .build(), tc);
                } catch (Exception ignored) {}
                break;
        }
    }

    private void handlePlayPause() {
        boolean playing = SonicPulseClient.getEngine().isActiveOrPending();
        boolean paused = SonicPulseClient.getEngine().getPlayer().isPaused();
        if (playing) {
            SonicPulseClient.getEngine().getPlayer().setPaused(!paused);
        } else {
            if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES
                    && !config.getFavoriteHistory().isEmpty()) {
                SonicPulseConfig.HistoryEntry e = config.getFavoriteHistory().get(0);
                SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
            } else if (config.activeMode == SonicPulseConfig.SessionMode.HISTORY) {
                List<SonicPulseConfig.HistoryEntry> hist = config.history.stream().filter(e -> !e.favorite)
                        .collect(Collectors.toList());
                if (!hist.isEmpty()) {
                    SonicPulseConfig.HistoryEntry e = hist.get(0);
                    SonicPulseClient.getEngine().playTrack(e.url, e.label, e.type);
                }
            } else if (config.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
                if (localFiles.isEmpty())
                    scanLocalFiles();
                if (!localFiles.isEmpty())
                    SonicPulseClient.getEngine().playTrack(localFiles.get(0).getAbsolutePath(),
                            localFiles.get(0).getName(), "Local");
            } else if (config.activeMode == SonicPulseConfig.SessionMode.RADIO && !radioStreams.isEmpty()) {
                String[] rs = radioStreams.get(0);
                SonicPulseClient.getEngine().playTrack(rs[1], rs[0], "Radio");
            }
        }
        refreshWidgets();
    }

    private void pickFolder() {
        if (Util.getPlatform() == Util.OS.WINDOWS) {
            new Thread(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-Command",
                            "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; if($f.ShowDialog() -eq 'OK') { $f.SelectedPath }");
                    Process p = pb.start();
                    BufferedReader rd = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String path = rd.readLine();
                    if (path != null && !path.trim().isEmpty()) {
                        config.localMusicPath = path.trim();
                        SonicPulseConfig.save();
                        Minecraft.getInstance().execute(this::scanLocalFiles);
                        Minecraft.getInstance().execute(this::refreshWidgets);
                    }
                } catch (Exception e) {
                }
            }).start();
        }
    }

    private void handleSkip() {
        AudioTrack cur = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        SonicPulseClient.getEngine().playNextInList(cur);
        refreshWidgets();
    }

    private void moveFav(SonicPulseConfig.HistoryEntry e, int dir) {
        List<SonicPulseConfig.HistoryEntry> fL = config.getFavoriteHistory();
        int idx = fL.indexOf(e);
        int tIdx = idx + dir;
        if (tIdx >= 0 && tIdx < fL.size()) {
            Collections.swap(config.history, config.history.indexOf(fL.get(idx)), config.history.indexOf(fL.get(tIdx)));
            config.save();
            refreshWidgets();
        }
    }

    private void scanLocalFiles() {
        localFiles.clear();
        String path = config.localMusicPath.isEmpty()
                ? Minecraft.getInstance().gameDirectory.toPath().resolve("sonicpulse").resolve("music").toString()
                : config.localMusicPath;
        File dr = new File(path);
        if (dr.exists() && dr.isDirectory()) {
            File[] fls = dr.listFiles((d, n) -> {
                String nm = n.toLowerCase();
                return nm.endsWith(".mp3") || nm.endsWith(".wav") || nm.endsWith(".flac");
            });
            if (fls != null)
                Collections.addAll(localFiles, fls);
        }
    }

    private void loadRadioM3U(String radioInput) {
        radioStreams.clear();
        new Thread(() -> {
            try {
                java.net.URL urlObj = new URI(radioInput).toURL();
                java.io.BufferedReader rd = new java.io.BufferedReader(
                        new java.io.InputStreamReader(urlObj.openStream()));
                String ln, lt = null;
                while ((ln = rd.readLine()) != null) {
                    ln = ln.trim();
                    if (ln.isEmpty() || ln.startsWith("#EXTM3U"))
                        continue;
                    if (ln.startsWith("#EXTINF")) {
                        int c = ln.indexOf(",");
                        if (c != -1)
                            lt = ln.substring(c + 1).trim();
                    } else if (!ln.startsWith("#")) {
                        radioStreams.add(new String[] { lt != null ? lt : ln, ln });
                        lt = null;
                    }
                }
                rd.close();
            } catch (Exception e) {
            }
            Minecraft.getInstance().execute(this::refreshWidgets);
        }).start();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int d = (v > 0) ? -1 : 1;
        if (currentTab == 3)
            historyScrollOffset = Math.max(0, Math
                    .min((int) config.history.stream().filter(e -> !e.favorite).count() - 7, historyScrollOffset + d));
        if (currentTab == 4)
            favScrollOffset = Math.max(0, Math.min(config.getFavoriteHistory().size() - 8, favScrollOffset + d));
        if (currentTab == 5)
            radioScrollOffset = Math.max(0, Math.min(radioStreams.size() - 5, radioScrollOffset + d));
        if (currentTab == 6)
            localScrollOffset = Math.max(0, Math.min(localFiles.size() - 7, localScrollOffset + d));
        refreshWidgets();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        int contentX = x + SIDEBAR_WIDTH + 10, contentW = BOX_WIDTH - SIDEBAR_WIDTH - 20, tabY = y + 42;

        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.fill(x, y, x + BOX_WIDTH, y + 38, 0x44000000);

        context.fill(x + SIDEBAR_WIDTH, y + 38, x + BOX_WIDTH, y + BOX_HEIGHT - 5,
                (PASTEL[currentTab] & 0x00FFFFFF) | 0x22000000);

        context.outline(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.fill(x + SIDEBAR_WIDTH, y + 38, x + SIDEBAR_WIDTH + 1, y + BOX_HEIGHT - 5, 0x44FFFFFF);

        super.extractRenderState(context, mx, my, d);

        for (TintRecord tr : widgetTints) {
            context.fill(tr.widget.getX(), tr.widget.getY(), tr.widget.getX() + tr.widget.getWidth(),
                    tr.widget.getY() + tr.widget.getHeight(), (tr.color & 0x00FFFFFF) | 0x33000000);
        }

        boolean isEnginePlaying = SonicPulseClient.getEngine().isActiveOrPending();
        boolean isEnginePaused = SonicPulseClient.getEngine().getPlayer().isPaused();
        String stateTxt = isEnginePlaying ? (isEnginePaused ? "§e[ ⏸ ]" : "§a[ ♫ ]") : "§7[ ■ ]";
        String trkTxt = (config.currentTitle != null && isEnginePlaying)
                ? " » §f" + this.font.plainSubstrByWidth(config.currentTitle, 115)
                : "";
        context.text(this.font, Component.literal(stateTxt + " §8(" + config.activeMode.name() + ")" + trkTxt), x + 5,
                y + 6, 0xFFFFFFFF, false);

        context.outline(x + 4, y + 39 + (currentTab * 18), SIDEBAR_WIDTH - 8, 18, PASTEL[currentTab]);

        int playBtnW = 65, playLocalX = x + BOX_WIDTH - playBtnW - 8, playFavsX = playLocalX - playBtnW - 5;
        context.fill(playFavsX, y + 20, playFavsX + playBtnW, y + 33, 0x5555FF55);
        context.fill(playLocalX, y + 20, playLocalX + playBtnW, y + 33, 0x5555FF55);
        context.centeredText(this.font, Component.literal("Play Favs"), playFavsX + playBtnW / 2, y + 23, 0xFFFFFF);
        context.centeredText(this.font, Component.literal("Play Local"), playLocalX + playBtnW / 2, y + 23, 0xFFFFFF);

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
            context.text(this.font, Component.literal("§e" + config.bgEffect.name() + " TWEAKS"), contentX, divY + 4,
                    0xFFFFFFFF, false);
        }

        // DSP Equalizer Label
        if (currentTab == 7) {
            int divY = tabY + 84;
            context.fill(contentX, divY, contentX + contentW, divY + 1, 0x44FFFFFF);
            context.text(this.font, Component.literal("§eDSP EQUALIZER"), contentX, divY + 4, 0xFFFFFFFF, false);
        }

        AudioTrack playingTrack = SonicPulseClient.getEngine().getPlayer().getPlayingTrack();
        String activeUri = playingTrack != null ? playingTrack.getInfo().uri : null;
        if (activeUri != null) {
            int highlightColor = 0xFF55FF55;
            if (currentTab == 3) {
                List<SonicPulseConfig.HistoryEntry> hSorted = config.history.stream().filter(e -> !e.favorite)
                        .sorted(Comparator.comparingLong((SonicPulseConfig.HistoryEntry e) -> e.lastPlayed).reversed())
                        .limit(20).collect(Collectors.toList());
                for (int i = historyScrollOffset; i < Math.min(hSorted.size(), historyScrollOffset + 7); i++) {
                    if (hSorted.get(i).url.equals(activeUri)) {
                        int rY = tabY + 20 + ((i - historyScrollOffset) * 19);
                        context.outline(contentX - 1, rY - 1, contentW - 38, 21, highlightColor);
                    }
                }
            } else if (currentTab == 4) {
                List<SonicPulseConfig.HistoryEntry> fvs = config.getFavoriteHistory();
                for (int i = favScrollOffset; i < Math.min(fvs.size(), favScrollOffset + 8); i++) {
                    if (fvs.get(i).url.equals(activeUri)) {
                        int rY = tabY + ((i - favScrollOffset) * 19);
                        context.outline(contentX + 19, rY - 1, contentW - 93, 21, highlightColor);
                    }
                }
            } else if (currentTab == 6) {
                for (int i = localScrollOffset; i < Math.min(localFiles.size(), localScrollOffset + 7); i++) {
                    File fl = localFiles.get(i);
                    if (activeUri.equals(fl.getAbsolutePath()) || activeUri.equals(fl.toURI().toString())
                            || activeUri.replace("\\", "/").endsWith(fl.getName())) {
                        int rY = tabY + 26 + ((i - localScrollOffset) * 19);
                        context.outline(contentX - 1, rY - 1, contentW - 13, 21, highlightColor);
                    }
                }
            }
        }

        if (currentTab >= 3 && currentTab <= 6) {
            int total = 0, offset = 0, visible = 0;
            if (currentTab == 3) {
                total = (int) config.history.stream().filter(e -> !e.favorite).count();
                offset = historyScrollOffset;
                visible = 7;
            }
            if (currentTab == 4) {
                total = config.getFavoriteHistory().size();
                offset = favScrollOffset;
                visible = 8;
            }
            if (currentTab == 5) {
                total = radioStreams.size();
                offset = radioScrollOffset;
                visible = 5;
            }
            if (currentTab == 6) {
                total = localFiles.size();
                offset = localScrollOffset;
                visible = 7;
            }
            if (total > visible) {
                int barX = x + BOX_WIDTH - 4, barY = tabY + 5, barH = BOX_HEIGHT - 52;
                context.fill(barX, barY, barX + 2, barY + barH, 0x44000000);
                int thumbH = Math.max(10, (visible * barH) / total);
                int thumbY = barY + (offset * (barH - thumbH)) / (total - visible);
                context.fill(barX, thumbY, barX + 2, thumbY + thumbH, PASTEL[currentTab]);
            }
        }

        if (currentTab == 0) {
            context.text(this.font, Component.literal("Enter audio URL to stream:"), contentX, tabY + 3, 0xFFFFFFFF,
                    false);
            context.text(this.font, Component.literal("§ePlatforms: YouTube, SoundCloud, Bandcamp, Vimeo"), contentX,
                    tabY + 45, 0xBBBBBB, false);
            context.text(this.font, Component.literal("§eFormats: MP3, FLAC, WAV, WebM, MP4, M3U"), contentX,
                    tabY + 65, 0xBBBBBB, false);
            context.text(this.font, Component.literal("§aRecently Streamed:"), contentX, tabY + 105, 0xFFFFFFFF,
                    false);
        }

        if (currentTab == 8) {
            int centerX = contentX + (contentW / 2);
            context.centeredText(this.font, Component.literal("§l§nSONICPULSE"), centerX, tabY + 5,
                    0xFFFF00FF);
            context.centeredText(this.font, Component.literal("Professional Media Control Unit"), centerX,
                    tabY + 18, 0xAAAAAA);
            context.centeredText(this.font, Component.literal("Version: §a" + CURRENT_VERSION), centerX,
                    tabY + 30, 0xFFFFFFFF);
            context.centeredText(this.font, Component.literal("Created by: §bSteve Watkins"), centerX,
                    tabY + 44, 0xFFFFFFFF);

            int qrSize = 54;
            int qrX = centerX - (qrSize / 2);
            int qrY = tabY + 57;
            // White backing card for QR (matches printed QR background)
            context.fill(qrX - 3, qrY - 3, qrX + qrSize + 3, qrY + qrSize + 3, 0xFFFFFFFF);
            // Thin accent border
            context.outline(qrX - 4, qrY - 4, qrSize + 8, qrSize + 8, PASTEL[8]);
            // Blit QR texture via GUI_TEXTURED pipeline: (pipeline, id, x, y, u, v, w, h, texW, texH)
            context.blit(RenderPipelines.GUI_TEXTURED, QR_CODE, qrX, qrY, 0.0f, 0.0f, qrSize, qrSize, qrSize, qrSize);

            if (config.showTooltips && mx >= qrX - 3 && mx <= qrX + qrSize + 3 && my >= qrY - 3
                    && my <= qrY + qrSize + 3) {
                context.setTooltipForNextFrame(Component.literal("Scan to donate — or click \"Buy Steve a Coffee\" below!"), mx,
                        my);
            }
            context.centeredText(this.font, Component.literal("§7Enjoying the vibes? Buy Steve a coffee!"),
                    centerX, tabY + 116, 0xAAFFFFFF);
        }

        boolean isNew = isVersionNewer(latestVersion, CURRENT_VERSION);
        String verText = isNew ? "§6Update Avail: V" + latestVersion : "§8V" + CURRENT_VERSION;
        int verW = this.font.width(verText);
        context.text(this.font, Component.literal(verText), x + (SIDEBAR_WIDTH / 2) - (verW / 2), y + BOX_HEIGHT - 12,
                0xFFFFFFFF, false);
        hudRenderer.render(context, true, 0, 0);
    }

    private static boolean isVersionNewer(String latest, String current) {
        if (latest == null || current == null || latest.equals(current))
            return false;
        try {
            String[] lParts = latest.split("\\+")[0].split("\\.");
            String[] cParts = current.split("\\+")[0].split("\\.");
            int length = Math.max(lParts.length, cParts.length);
            for (int i = 0; i < length; i++) {
                int lVal = i < lParts.length ? Integer.parseInt(lParts[i].replaceAll("\\D+", "")) : 0;
                int cVal = i < cParts.length ? Integer.parseInt(cParts[i].replaceAll("\\D+", "")) : 0;
                if (lVal > cVal)
                    return true;
                if (lVal < cVal)
                    return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SonicPulseClient.getConfigKeyBinding() != null && SonicPulseClient.getConfigKeyBinding().matches(event)) {
            if (!(this.getFocused() instanceof EditBox)) {
                this.onClose();
                return true;
            }
        }
        return super.keyPressed(event);
    }
}