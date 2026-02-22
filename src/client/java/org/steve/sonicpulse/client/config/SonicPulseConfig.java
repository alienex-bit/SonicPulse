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
        NEO_BRUTAL(0xFFBFFF00, 0xFF000000, "Neobrutalist"),
        SYNTHWAVE(0xDD110033, 0xFFFF00FF, "Synthwave"),
        TERMINAL(0xFF000000, 0xFF00FF00, "Terminal"),
        ROYAL_GOLD(0xEE222222, 0xFFFFD700, "Royal Gold"),
        BLOOD_MOON(0xEE330000, 0xFFDC143C, "Blood Moon"),
        GLACIER(0xDD001133, 0xFF00FFFF, "Glacier");

        private final int bgColor, borderColor;
        private final String name;
        Skin(int bg, int border, String name) { this.bgColor = bg; this.borderColor = border; this.name = name; }
        public int getBgColor() { return bgColor; }
        public int getBorderColor() { return borderColor; }
        public String getName() { return name; }
    }

    public enum VisualizerStyle { SOLID, FLOATING_PEAKS }
    
    public enum BgEffect { OFF, BASS_PULSE, RGB_AURA }

    public enum RibbonLayout {
        LOG_TRK_BAR("Logo | Track | Bars"),
        LOG_BAR_TRK("Logo | Bars | Track"),
        TRK_LOG_BAR("Track | Logo | Bars"),
        TRK_BAR_LOG("Track | Bars | Logo"),
        BAR_LOG_TRK("Bars | Logo | Track"),
        BAR_TRK_LOG("Bars | Track | Logo");

        private final String displayName;
        RibbonLayout(String name) { this.displayName = name; }
        public String getDisplayName() { return displayName; }
    }

    public float hudScale = 1.0f; 
    public int barColor = 0xFF00BFFF; 
    public int titleColor = 0xFFFF00FF;
    public int volume = 50;
    public Skin skin = Skin.DEFAULT;
    public VisualizerStyle visStyle = VisualizerStyle.SOLID;
    public BgEffect bgEffect = BgEffect.OFF;
    public RibbonLayout ribbonLayout = RibbonLayout.LOG_TRK_BAR;
    public String currentTitle = null;
    public String lastRadioUrl = "";
    public String localMusicPath = "";
    public List<HistoryEntry> history = new ArrayList<>();
    public boolean hudVisible = true;
    
    public boolean showLogo = true;
    public boolean showTrack = true;
    public boolean showBars = true;

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
        boolean wasFav = false;
        String existingLabel = null;
        for (HistoryEntry e : history) {
            if (e.url.equals(url)) {
                wasFav = e.favorite;
                existingLabel = e.label;
                break;
            }
        }
        
        String finalLabel = (existingLabel != null) ? existingLabel : label;
        history.removeIf(e -> e.url.equals(url));
        HistoryEntry newEntry = new HistoryEntry(type, finalLabel, url);
        newEntry.favorite = wasFav;
        
        history.add(0, newEntry);
        if (history.size() > 50) history.remove(history.size() - 1);
        save();
    }

    public List<HistoryEntry> getFavoriteHistory() {
        return history.stream().filter(e -> e.favorite).toList();
    }

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
    
    public void nextBgEffect() {
        BgEffect[] v = BgEffect.values();
        bgEffect = v[(bgEffect.ordinal() + 1) % v.length];
        save();
    }

    public void nextRibbonLayout() {
        RibbonLayout[] r = RibbonLayout.values();
        ribbonLayout = r[(ribbonLayout.ordinal() + 1) % r.length];
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