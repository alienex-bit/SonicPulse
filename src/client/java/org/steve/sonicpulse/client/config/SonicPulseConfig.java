package org.steve.sonicpulse.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class SonicPulseConfig {
    public enum Skin {
        DEFAULT(0x88000000, 0xFFFFFFFF, "Default"),
        TRANSPARENT(0x00000000, 0x44FFFFFF, "Glass"),
        VCR_OSD(0xFF000044, 0xFFFFFFFF, "VCR OSD"),
        CYBER_NOIR(0xFF000000, 0xFF00E8FF, "Cyber Noir"),
        MERMAID(0x9920948B, 0xFFBD93F9, "Mermaid"),
        NEO_BRUTAL(0xFFBFFF00, 0xFF000000, "Neobrutalist");

        private final int bgColor, borderColor;
        private final String name;
        Skin(int bg, int border, String name) { this.bgColor = bg; this.borderColor = border; this.name = name; }
        public int getBgColor() { return bgColor; }
        public int getBorderColor() { return borderColor; }
        public String getName() { return name; }
    }

    public enum ColorMode { SOLID, RAINBOW, MATRIX, HEATMAP, VAPORWAVE, HORIZONTAL, PULSING_DUAL, NEON_OUTLINE }
    public enum VisualizerStyle { SOLID, SEGMENTED, MIRRORED, WAVEFORM, PEAK_DOTS }

    public int hudX = 10, hudY = 10;
    public float hudScale = 1.0f;
    public int barColor = 0xFF00FF00;
    public int titleColor = 0xFFFF55FF;
    public int volume = 50;
    public Skin skin = Skin.DEFAULT;
    public ColorMode colorMode = ColorMode.SOLID;
    public VisualizerStyle visStyle = VisualizerStyle.SOLID;
    public String currentTitle = null;
    public String lastRadioUrl = "";
    public List<HistoryEntry> history = new ArrayList<>();
    public boolean hudVisible = true; // New Master Toggle

    public static class HistoryEntry {
        public String type, label, url;
        public boolean favorite = false;
        public HistoryEntry(String t, String l, String u) { type=t; label=l; url=u; }
    }

    public void addHistory(String type, String label, String url) {
        history.removeIf(e -> e.url.equals(url));
        history.add(0, new HistoryEntry(type, label, url));
        if (history.size() > 50) history.remove(history.size() - 1);
        save();
    }

    public List<HistoryEntry> getFavoriteHistory() {
        return history.stream().filter(e -> e.favorite).toList();
    }

    public void setPos(int x, int y) { hudX = x; hudY = y; save(); }
    public void setColor(int c) { barColor = c; save(); }
    public void setTitleColor(int c) { titleColor = c; save(); }
    
    public void nextSkin() {
        Skin[] s = Skin.values();
        skin = s[(skin.ordinal() + 1) % s.length];
        save();
    }

    public void nextColorMode() {
        ColorMode[] m = ColorMode.values();
        colorMode = m[(colorMode.ordinal() + 1) % m.length];
        save();
    }

    public void nextVisStyle() {
        VisualizerStyle[] v = VisualizerStyle.values();
        visStyle = v[(visStyle.ordinal() + 1) % v.length];
        save();
    }

    private static final File FILE = new File(MinecraftClient.getInstance().runDirectory, "config/sonicpulse.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SonicPulseConfig instance;

    public static SonicPulseConfig get() {
        if (instance == null) {
            if (FILE.exists()) {
                try (FileReader r = new FileReader(FILE)) { instance = GSON.fromJson(r, SonicPulseConfig.class); }
                catch (Exception e) { instance = new SonicPulseConfig(); }
            } else { instance = new SonicPulseConfig(); }
        }
        return instance;
    }

    public static void save() {
        try {
            FILE.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(FILE)) { GSON.toJson(get(), w); }
        } catch (Exception e) { e.printStackTrace(); }
    }
}