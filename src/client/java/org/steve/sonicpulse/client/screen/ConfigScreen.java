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
import java.util.List;
import java.util.ArrayList;

public class ConfigScreen extends Screen {
    private static final int BOX_WIDTH = 270, BOX_HEIGHT = 245;
    private TextFieldWidget urlField, radioUrlField, renameField;
    private final SonicPulseConfig config = SonicPulseConfig.get();
    private int currentTab = 0, colorIndex = 0, titleColorIndex = 0, renamingIndex = -1, radioScrollOffset = 0;
    private final List<String[]> radioStreams = new ArrayList<>();
    private static final String[] COLOR_NAMES = {"Green", "Cyan", "Magenta", "Yellow", "Orange", "Blue", "Red", "White"};
    private static final int[] BAR_COLORS = {0xFF00FF00, 0xFF00FFFF, 0xFFFF00FF, 0xFFFFFF00, 0xFFFF5500, 0xFF0000FF, 0xFFFF0000, 0xFFFFFFFF};
    public ConfigScreen() { super(Text.literal("SonicPulse Config")); for(int i=0; i<BAR_COLORS.length; i++) { if(BAR_COLORS[i]==config.barColor) colorIndex=i; if(BAR_COLORS[i]==config.titleColor) titleColorIndex=i; } }

    @Override protected void init() { refreshWidgets(); }
    
    private void refreshWidgets() {
        this.clearChildren();
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("PLAY"), b -> { currentTab = 0; refreshWidgets(); }).dimensions(x + 5, y + 25, 45, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("VISUALS"), b -> { currentTab = 1; refreshWidgets(); }).dimensions(x + 52, y + 25, 65, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("LAYOUT"), b -> { currentTab = 2; refreshWidgets(); }).dimensions(x + 119, y + 25, 45, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("HISTORY"), b -> { currentTab = 3; refreshWidgets(); }).dimensions(x + 166, y + 25, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("RADIO"), b -> { currentTab = 4; refreshWidgets(); }).dimensions(x + 228, y + 25, 37, 20).build());
        
        switch (currentTab) {
            case 0:
                // Start with an empty URL field; don't pre-fill with lastUrl (history handles persistence)
                urlField = new TextFieldWidget(textRenderer, x + 10, y + 60, BOX_WIDTH - 20, 20, Text.literal("URL"));
                urlField.setMaxLength(256); urlField.setText(""); addSelectableChild(urlField);
                // PLAY: do not persist manual URL to config.lastUrl; instead add it to history so it can be reopened
                addDrawableChild(ButtonWidget.builder(Text.literal("▶ PLAY"), b -> {
                    String u = urlField.getText();
                    if (!u.isEmpty()) {
                        config.currentTitle = null; // Reset forced title for manual URLs
                        // Add a friendly history entry derived from the URL (host and filename when possible)
                        try {
                            java.net.URI uri = new java.net.URI(u);
                            String host = uri.getHost();
                            String path = uri.getPath();
                            String file = (path != null && !path.isEmpty()) ? path.substring(path.lastIndexOf('/') + 1) : "";
                            if (host == null || host.isEmpty()) host = "Manual";
                            if (file == null || file.isEmpty()) file = uri.getScheme() != null ? uri.getScheme() : "URL";
                            config.addHistory(host, file, u);
                        } catch (Exception ex) {
                            // Fallback: add a generic manual entry
                            config.addHistory("Manual", "URL", u);
                        }
                        // Play immediately (but do not save as lastUrl)
                        SonicPulseClient.getEngine().stop();
                        SonicPulseClient.getEngine().playTrack(u);
                    }
                }).dimensions(x + 10, y + 85, 80, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("⏸ PAUSE"), b -> SonicPulseClient.getEngine().getPlayer().setPaused(!SonicPulseClient.getEngine().getPlayer().isPaused())).dimensions(x + 95, y + 85, 80, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("⏹ STOP"), b -> SonicPulseClient.getEngine().stop()).dimensions(x + 180, y + 85, 80, 20).build());
                addDrawableChild(new SliderWidget(x + 10, y + 115, BOX_WIDTH - 20, 20, Text.literal("Volume: " + config.volume + "%"), config.volume / 100.0) { @Override protected void updateMessage() { setMessage(Text.literal("Volume: " + (int)(value * 100) + "%")); } @Override protected void applyValue() { config.volume = (int)(value * 100); SonicPulseConfig.save(); } });
                break;
            case 1:
                // HUD Theme button (renamed)
                addDrawableChild(ButtonWidget.builder(Text.literal("HUD Theme: " + config.skin.getName()), b -> { config.nextSkin(); refreshWidgets(); }).tooltip(Tooltip.of(Text.literal("Change the HUD theme"))) .dimensions(x + 10, y + 60, BOX_WIDTH - 20, 20).build());
                // Visualiser Type (renamed from Bar Style)
                addDrawableChild(ButtonWidget.builder(Text.literal("Visualiser Type: " + config.visStyle.name()), b -> { config.nextVisStyle(); refreshWidgets(); }).tooltip(Tooltip.of(Text.literal("Cycle visualiser type"))) .dimensions(x + 10, y + 85, BOX_WIDTH - 20, 20).build());
                // Visualiser Colour (renamed and corrected spelling) - full width like other buttons
                ButtonWidget cBtn = ButtonWidget.builder(Text.literal("Visualiser Colour: " + COLOR_NAMES[colorIndex]), b -> { colorIndex = (colorIndex+1)%BAR_COLORS.length; config.setColor(BAR_COLORS[colorIndex]); refreshWidgets(); }).tooltip(Tooltip.of(Text.literal("Change the visualiser bar colour"))) .dimensions(x + 10, y + 110, BOX_WIDTH - 20, 20).build();
                addDrawableChild(cBtn);
                // Title Colour button (independent from bar colour) - full width
                ButtonWidget tBtn = ButtonWidget.builder(Text.literal("Title Colour: " + COLOR_NAMES[titleColorIndex]), b -> { titleColorIndex = (titleColorIndex+1)%BAR_COLORS.length; config.setTitleColor(BAR_COLORS[titleColorIndex]); refreshWidgets(); }).tooltip(Tooltip.of(Text.literal("Change the HUD title colour"))) .dimensions(x + 10, y + 135, BOX_WIDTH - 20, 20).build();
                addDrawableChild(tBtn);
                // Move scale slider down to make space for title color
                addDrawableChild(new SliderWidget(x + 10, y + 160, BOX_WIDTH - 20, 20, Text.literal("Scale: " + (int)(config.hudScale * 100) + "%"), (config.hudScale - 0.5) / 0.7) { @Override protected void updateMessage() { setMessage(Text.literal("Scale: " + (int)(config.hudScale * 100) + "%")); } @Override protected void applyValue() { config.setScale((float)(0.5 + value * 0.7)); } });
                break;
            case 2:
                addDrawableChild(ButtonWidget.builder(Text.literal("Top Left"), b -> config.setPos(10, 10)).dimensions(x+10, y+60, 120, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Top Right"), b -> config.setPos(-10, 10)).dimensions(x+140, y+60, 120, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bottom Left"), b -> config.setPos(10, -10)).dimensions(x+10, y+85, 120, 20).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Bottom Right"), b -> config.setPos(-10, -10)).dimensions(x+140, y+85, 120, 20).build());
                break;
            case 3:
                List<SonicPulseConfig.HistoryEntry> hist = config.history; int sY = y + 55;
                for (int i = 0; i < Math.min(hist.size(), 7); i++) {
                    final int eIdx = i; SonicPulseConfig.HistoryEntry e = hist.get(eIdx);
                    if (renamingIndex == eIdx) {
                        // Enforce a 24-character rename limit
                        final int MAX_RENAME = 24;
                        renameField = new TextFieldWidget(textRenderer, x + 10, sY + (i * 22), 170, 20, Text.literal("Rename"));
                        renameField.setText(e.label);
                        renameField.setMaxLength(MAX_RENAME);
                        addSelectableChild(renameField);
                        addDrawableChild(ButtonWidget.builder(Text.literal("✔"), b -> {
                            String newLabel = renameField.getText();
                            if (newLabel.length() > MAX_RENAME) newLabel = newLabel.substring(0, MAX_RENAME);
                            e.label = newLabel;
                            SonicPulseConfig.save();
                            renamingIndex = -1;
                            refreshWidgets();
                        }).dimensions(x + 185, sY + (i * 22), 25, 20).build());
                    } else {
                        // Display history labels truncated to 24 characters to match rename limit
                        addDrawableChild(ButtonWidget.builder(Text.literal(truncate(e.label, 24)), b -> {
                            config.currentTitle = e.label; // FORCE TITLE
                            config.setUrl(e.url);
                            SonicPulseClient.getEngine().stop();
                            SonicPulseClient.getEngine().playTrack(e.url);
                        }).dimensions(x + 10, sY + (i * 22), 170, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal(e.favorite ? "★" : "☆"), b -> { e.favorite = !e.favorite; refreshWidgets(); SonicPulseConfig.save(); }).dimensions(x + 185, sY + (i * 22), 25, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { config.history.remove(e); SonicPulseConfig.save(); refreshWidgets(); }).dimensions(x + 212, sY + (i * 22), 25, 20).build());
                        addDrawableChild(ButtonWidget.builder(Text.literal("R"), b -> { renamingIndex = eIdx; refreshWidgets(); }).dimensions(x + 239, sY + (i * 22), 25, 20).build());
                    }
                }
                break;
            case 4:
                radioUrlField = new TextFieldWidget(textRenderer, x + 10, y + 75, 190, 20, Text.literal("M3U URL")); radioUrlField.setMaxLength(512); radioUrlField.setText(config.lastRadioUrl); addSelectableChild(radioUrlField);
                addDrawableChild(ButtonWidget.builder(Text.literal("Load"), b -> loadRadioM3U(radioUrlField.getText())).dimensions(x + 205, y + 75, 55, 20).build());
                String[] predefinedUrls = {
                    "https://gist.githubusercontent.com/bpsib/67089b959e4fa898af69fea59ad74bc3/raw/c7255834f326bc6a406080eed104ebaa9d3bc85d/BBC-Radio-HLS.m3u",
                    "https://gist.githubusercontent.com/casaper/ddec35d21a0158628fccbab7876b7ef3/raw/somafm.m3u",
                    "https://raw.githubusercontent.com/lovehifi/playlist-radio/main/playlist_radio.tgz",
                    "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/all-online_radio/electronic.m3u",
                    "https://raw.githubusercontent.com/junguler/m3u-radio-music-playlists/main/all-online_radio/chillout.m3u"
                };
                int buttonSpacing = (BOX_WIDTH - (5 * 35)) / 6; 
                for (int i = 0; i < predefinedUrls.length; i++) {
                    final int index = i; int buttonX = x + buttonSpacing + (i * (35 + buttonSpacing));
                    addDrawableChild(ButtonWidget.builder(Text.literal(String.valueOf(i + 1)), b -> { radioUrlField.setText(predefinedUrls[index]); loadRadioM3U(predefinedUrls[index]); }).dimensions(buttonX, y + 45, 35, 20).build());
                }
                int rdY = y + 105, v = 5;
                for (int i = radioScrollOffset; i < Math.min(radioStreams.size(), radioScrollOffset + v); i++) {
                    String[] rs = radioStreams.get(i);
                    addDrawableChild(ButtonWidget.builder(Text.literal(truncate(rs[0], 24)), b -> {
                        String t = rs[0];
                        config.currentTitle = t; // FORCE TITLE
                        config.addHistory("Radio", t, rs[1]); 
                        config.setUrl(rs[1]);
                        SonicPulseClient.getEngine().stop();
                        SonicPulseClient.getEngine().playTrack(rs[1]);
                    }).dimensions(x + 10, rdY + ((i - radioScrollOffset) * 22), BOX_WIDTH - 45, 20).build());
                }
                if (radioStreams.size() > v) {
                    addDrawableChild(ButtonWidget.builder(Text.literal("▲"), b -> { if (radioScrollOffset > 0) { radioScrollOffset--; refreshWidgets(); } }).dimensions(x + BOX_WIDTH - 30, rdY, 25, 20).build());
                    addDrawableChild(ButtonWidget.builder(Text.literal("▼"), b -> { if (radioScrollOffset < radioStreams.size() - v) { radioScrollOffset++; refreshWidgets(); } }).dimensions(x + BOX_WIDTH - 30, rdY + (v - 1) * 22, 25, 20).build());
                }
                break;
        }
    }
    private void loadRadioM3U(String url) {
        config.lastRadioUrl = url; SonicPulseConfig.save(); radioStreams.clear();
        new Thread(() -> { try { java.net.URL u = new java.net.URI(url).toURL(); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream())); String l, lt = null; while ((l = r.readLine()) != null) { l = l.trim(); if (l.isEmpty() || l.startsWith("#EXTM3U")) continue; if (l.startsWith("#EXTINF")) { int c = l.indexOf(","); if (c != -1) lt = l.substring(c + 1).trim(); } else if (!l.startsWith("#")) { radioStreams.add(new String[]{lt != null ? lt : "Station", l}); lt = null; } } r.close(); } catch (Exception e) { SonicPulseClient.LOGGER.error("Error loading M3U", e); } MinecraftClient.getInstance().execute(this::refreshWidgets); }).start();
    }
    private String truncate(String t, int m) { return (t != null && t.length() > m) ? t.substring(0, m - 3) + "..." : t; }
    @Override public void render(DrawContext context, int mx, int my, float d) {
        int x = (width - BOX_WIDTH) / 2, y = (height - BOX_HEIGHT) / 2;
        context.fill(x, y, x + BOX_WIDTH, y + BOX_HEIGHT, config.skin.getBgColor());
        context.drawBorder(x, y, BOX_WIDTH, BOX_HEIGHT, config.skin.getBorderColor());
        context.drawTextWithShadow(textRenderer, Text.literal("SONICPULSE CONFIG"), x + (BOX_WIDTH - textRenderer.getWidth("SONICPULSE CONFIG")) / 2, y + 8, 0xFF55FF);
        super.render(context, mx, my, d);
        if (currentTab == 0 && urlField != null) urlField.render(context, mx, my, d); if (currentTab == 3 && renameField != null && renamingIndex != -1) renameField.render(context, mx, my, d); if (currentTab == 4 && radioUrlField != null) radioUrlField.render(context, mx, my, d);
    }
    @Override public boolean mouseClicked(double mx, double my, int b) { if (currentTab == 0 && urlField != null && urlField.isMouseOver(mx, my) && !urlField.isFocused()) urlField.setText(""); if (currentTab == 4 && radioUrlField != null && radioUrlField.isMouseOver(mx, my) && !radioUrlField.isFocused()) radioUrlField.setText(""); return super.mouseClicked(mx, my, b); }
}
