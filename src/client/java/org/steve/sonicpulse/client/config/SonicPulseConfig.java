package org.steve.sonicpulse.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import org.steve.sonicpulse.client.gui.SonicPulseSkin;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class SonicPulseConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static SonicPulseConfig INSTANCE;

    public enum VisualizerStyle { SOLID, SEGMENTED, MIRRORED, WAVEFORM, PEAK_DOTS }
    public enum ColorMode { SOLID, HORIZONTAL, VERTICAL, RAINBOW, HEATMAP, VAPORWAVE, MATRIX, PULSING_DUAL, NEON_OUTLINE }

    public int hudX = 10, hudY = 10, barColor = 0xFF00FF00, volume = 100;
    // Title color is configurable independently from the visualizer bar color
    public int titleColor = 0xFFFF55FF;
    public float hudScale = 0.75f;
    public SonicPulseSkin skin = SonicPulseSkin.MODERN;
    public VisualizerStyle visStyle = VisualizerStyle.SOLID;
    public ColorMode colorMode = ColorMode.SOLID;
    public String lastUrl = "";
    public String lastRadioUrl = "";
    public List<HistoryEntry> history = new ArrayList<>();

    // NEW: Transient field to force the HUD title (Not saved to disk)
    public transient String currentTitle = null; 

    public static class HistoryEntry {
        public String label = "";
        public String url = "";
        public boolean favorite = false;
        
        public HistoryEntry() {}
        public HistoryEntry(String label, String url) { this.label = label; this.url = url; }
    }

    public static SonicPulseConfig get() {
        if (INSTANCE == null) {
            configFile = new File(MinecraftClient.getInstance().runDirectory, "config/sonicpulse.json");
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                INSTANCE = GSON.fromJson(reader, SonicPulseConfig.class);
                if (INSTANCE.history == null) INSTANCE.history = new ArrayList<>();
            } catch (Exception e) {
                INSTANCE = new SonicPulseConfig();
            }
        } else {
            INSTANCE = new SonicPulseConfig();
            save();
        }
    }

    public static void save() {
        try {
            if (configFile.getParentFile() != null) configFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addHistory(String artist, String title, String url) {
        String label = (artist != null && !artist.isEmpty() ? artist : "Unknown") + " - " + (title != null ? title : "Track");
        if (history.stream().anyMatch(e -> e.url.equals(url))) return;
        history.add(0, new HistoryEntry(label, url));
        if (history.size() > 50) history.remove(history.size() - 1);
        save();
    }

    public void nextVisStyle() { visStyle = VisualizerStyle.values()[(visStyle.ordinal() + 1) % VisualizerStyle.values().length]; save(); }
    public void nextColorMode() { colorMode = ColorMode.values()[(colorMode.ordinal() + 1) % ColorMode.values().length]; save(); }
    public void nextSkin() { skin = SonicPulseSkin.values()[(skin.ordinal() + 1) % SonicPulseSkin.values().length]; save(); }
    public void setPos(int x, int y) { this.hudX = x; this.hudY = y; save(); }
    public void setScale(float s) { this.hudScale = s; save(); }
    public void setColor(int c) { this.barColor = c; save(); }
    public void setTitleColor(int c) { this.titleColor = c; save(); }
    public void setUrl(String url) { this.lastUrl = url; save(); }
}
