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

    public enum VisualizerStyle { SOLID, FLOATING_PEAKS }
    
    // NEW: The Master HUD Mode
    public enum HudMode { CLASSIC, CINEMATIC }

    public HudMode hudMode = HudMode.CLASSIC;
    public int hudX = 2, hudY = 2; 
    public float hudScale = 1.0f; 
    public int barColor = 0xFF00BFFF; 
    public int titleColor = 0xFFFF00FF;
    public int volume = 50;
    public Skin skin = Skin.DEFAULT;
    public VisualizerStyle visStyle = VisualizerStyle.SOLID;
    public String currentTitle = null;
    public String lastRadioUrl = "";
    public String localMusicPath = "";
    public List<HistoryEntry> history = new ArrayList<>();
    public boolean hudVisible = true;
    
    public boolean showTopZone = true;
    public boolean showMidZone = true;
    public boolean showBotZone = true;

    public static final int[] PALETTE = {
        0x00BFFF, 0x00CED1, 0x00FFC6, 0x32CD32, 0x7FFF00, 0xFFD300, 
        0xFFBF00, 0xFF8C00, 0xFF5F00, 0xFF2400, 0xDC143C, 0xFF1493, 
        0xFF00FF, 0x8A2BE2, 0x6A0DAD, 0x4B0082, 0x008B8B, 0x4682B4, 0xB0FF00
    };
    
    public static final String[] COLOR_NAMES = {
        "Electric Blue", "Deep Sky Cyan", "Neon Aqua", "Lime Green", "Chartreuse", "Vivid Yellow",
        "Amber", "Orange", "Neon Orange", "Signal Red", "Crimson", "Hot Pink",
        "Magenta", "Violet", "Deep Purple", "Indigo", "Cyber Teal", "Steel Blue", "Acid Green"
    };

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

    public void nextVisStyle() {
        VisualizerStyle[] v = VisualizerStyle.values();
        visStyle = v[(visStyle.ordinal() + 1) % v.length];
        save();
    }

    // Cycles the Master HUD Mode
    public void nextHudMode() {
        HudMode[] m = HudMode.values();
        hudMode = m[(hudMode.ordinal() + 1) % m.length];
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