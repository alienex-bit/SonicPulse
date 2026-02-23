package org.steve.sonicpulse.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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

    public enum SessionMode { NONE, FAVOURITES, HISTORY, LOCAL, RADIO, REMOTE }
    public enum VisualizerStyle { SOLID, FLOATING_PEAKS }
    
    public enum BgEffect { OFF, PULSE, AURA, VHS, HEATMAP }
    
    public enum PulseIntensity { SUBTLE, NORMAL, OVERDRIVE }
    public enum PulseDecay { SNAPPY, FLUID }
    public enum AuraSpeed { CHILL, NORMAL, WARP }
    public enum AuraPalette { RAINBOW, AURORA } 
    public enum VhsGlitch { MINOR, HEAVY, CORRUPTED }
    public enum VhsScanlines { FAINT, DARK, OFF }
    public enum HeatmapScale { FIRE, PLASMA, TOXIC }
    public enum HeatmapSpread { CONFINED, UNBOUND }

    public enum RibbonLayout {
        LOG_TRK_BAR("Logo → Track → Bars"), 
        LOG_BAR_TRK("Logo → Bars → Track"), 
        TRK_LOG_BAR("Track → Logo → Bars"),
        TRK_BAR_LOG("Track → Bars → Logo"), 
        BAR_LOG_TRK("Bars → Logo → Track"), 
        BAR_TRK_LOG("Bars → Track → Logo");
        private final String displayName;
        RibbonLayout(String name) { this.displayName = name; }
        public String getDisplayName() { return displayName; }
    }

    public float hudScale = 1.0f; 
    public float hudWidth = 1.0f; // NEW: Controls the horizontal squeeze
    public int barColor = 0xFF00BFFF, titleColor = 0xFFFF00FF, volume = 50;
    public Skin skin = Skin.DEFAULT;
    public SessionMode activeMode = SessionMode.NONE;
    public VisualizerStyle visStyle = VisualizerStyle.SOLID;
    public BgEffect bgEffect = BgEffect.OFF;
    public RibbonLayout ribbonLayout = RibbonLayout.LOG_TRK_BAR;
    public String currentTitle = null, lastRadioUrl = "", localMusicPath = "";
    public List<HistoryEntry> history = new ArrayList<>();
    
    public boolean hudVisible = true, showLogo = true, showTrack = true, showBars = true, showTooltips = true;

    public PulseIntensity pulseIntensity = PulseIntensity.NORMAL;
    public PulseDecay pulseDecay = PulseDecay.FLUID;
    public AuraSpeed auraSpeed = AuraSpeed.NORMAL;
    public AuraPalette auraPalette = AuraPalette.RAINBOW;
    public VhsGlitch vhsGlitch = VhsGlitch.HEAVY;
    public VhsScanlines vhsScanlines = VhsScanlines.FAINT;
    public HeatmapScale heatmapScale = HeatmapScale.FIRE;
    public HeatmapSpread heatmapSpread = HeatmapSpread.CONFINED;

    public static final int[] PALETTE = {
        0x00BFFF, 0x00CED1, 0x00FFC6, 0x32CD32, 0x7FFF00, 0xFFD300, 0xFFBF00, 0xFF8C00, 0xFF5F00, 
        0xFF2400, 0xDC143C, 0xFF1493, 0xFF00FF, 0x8A2BE2, 0x6A0DAD, 0x4B0082, 0x008B8B, 0x4682B4, 0xB0FF00
    };
    public static final String[] COLOR_NAMES = {
        "Elec Blue", "Cyan", "Aqua", "Green", "Chart", "Yellow", "Amber", "Orange", "N Orange", 
        "Red", "Crimson", "Pink", "Magenta", "Violet", "Purple", "Indigo", "Teal", "Steel", "Acid"
    };

    public static class HistoryEntry {
        public String type, label, url;
        public boolean favorite = false;
        public long lastPlayed = 0;
        public HistoryEntry(String t, String l, String u) { type=t; label=l; url=u; this.lastPlayed = System.currentTimeMillis(); }
    }

    public void addHistory(String type, String label, String url) {
        HistoryEntry entry = history.stream().filter(e -> e.url.equals(url)).findFirst().orElse(null);
        if (entry != null) {
            if (!entry.favorite) { entry.label = label; }
            entry.lastPlayed = System.currentTimeMillis();
            if (!entry.favorite) { history.remove(entry); history.add(0, entry); }
        } else {
            history.add(0, new HistoryEntry(type, label, url));
        }
        List<HistoryEntry> nonFavs = history.stream().filter(e -> !e.favorite).collect(Collectors.toList());
        if (nonFavs.size() > 20) { nonFavs.sort(Comparator.comparingLong(e -> e.lastPlayed)); history.remove(nonFavs.get(0)); }
        save();
    }

    public List<HistoryEntry> getFavoriteHistory() { return history.stream().filter(e -> e.favorite).toList(); }
    public void setColor(int c) { barColor = c; save(); }
    public void setTitleColor(int c) { titleColor = c; save(); }
    public void nextSkin() { skin = Skin.values()[(skin.ordinal() + 1) % Skin.values().length]; save(); }
    public void nextVisStyle() { visStyle = VisualizerStyle.values()[(visStyle.ordinal() + 1) % VisualizerStyle.values().length]; save(); }
    public void nextBgEffect() { bgEffect = BgEffect.values()[(bgEffect.ordinal() + 1) % BgEffect.values().length]; save(); }
    public void nextRibbonLayout() { ribbonLayout = RibbonLayout.values()[(ribbonLayout.ordinal() + 1) % RibbonLayout.values().length]; save(); }

    public void nextPulseIntensity() { pulseIntensity = PulseIntensity.values()[(pulseIntensity.ordinal() + 1) % PulseIntensity.values().length]; save(); }
    public void nextPulseDecay() { pulseDecay = PulseDecay.values()[(pulseDecay.ordinal() + 1) % PulseDecay.values().length]; save(); }
    public void nextAuraSpeed() { auraSpeed = AuraSpeed.values()[(auraSpeed.ordinal() + 1) % AuraSpeed.values().length]; save(); }
    public void nextAuraPalette() { auraPalette = AuraPalette.values()[(auraPalette.ordinal() + 1) % AuraPalette.values().length]; save(); }
    public void nextVhsGlitch() { vhsGlitch = VhsGlitch.values()[(vhsGlitch.ordinal() + 1) % VhsGlitch.values().length]; save(); }
    public void nextVhsScanlines() { vhsScanlines = VhsScanlines.values()[(vhsScanlines.ordinal() + 1) % VhsScanlines.values().length]; save(); }
    public void nextHeatmapScale() { heatmapScale = HeatmapScale.values()[(heatmapScale.ordinal() + 1) % HeatmapScale.values().length]; save(); }
    public void nextHeatmapSpread() { heatmapSpread = HeatmapSpread.values()[(heatmapSpread.ordinal() + 1) % HeatmapSpread.values().length]; save(); }

    private static final File FILE = new File(MinecraftClient.getInstance().runDirectory, "config/sonicpulse.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SonicPulseConfig instance;

    public static SonicPulseConfig get() {
        if (instance == null) {
            if (FILE.exists()) { try (FileReader r = new FileReader(FILE)) { instance = GSON.fromJson(r, SonicPulseConfig.class); } catch (Exception e) { instance = new SonicPulseConfig(); } }
            else { instance = new SonicPulseConfig(); }
        }
        
        if (instance.activeMode == null) instance.activeMode = SessionMode.NONE;
        if (instance.bgEffect == null) instance.bgEffect = BgEffect.OFF;
        if (instance.skin == null) instance.skin = Skin.DEFAULT;
        if (instance.visStyle == null) instance.visStyle = VisualizerStyle.SOLID;
        if (instance.ribbonLayout == null) instance.ribbonLayout = RibbonLayout.LOG_TRK_BAR;
        if (instance.history == null) instance.history = new ArrayList<>();
        if (instance.hudWidth == 0.0f) instance.hudWidth = 1.0f; // Armor plate for missing width data
        
        if (instance.pulseIntensity == null) instance.pulseIntensity = PulseIntensity.NORMAL;
        if (instance.pulseDecay == null) instance.pulseDecay = PulseDecay.FLUID;
        if (instance.auraSpeed == null) instance.auraSpeed = AuraSpeed.NORMAL;
        if (instance.auraPalette == null) instance.auraPalette = AuraPalette.RAINBOW;
        if (instance.vhsGlitch == null) instance.vhsGlitch = VhsGlitch.HEAVY;
        if (instance.vhsScanlines == null) instance.vhsScanlines = VhsScanlines.FAINT;
        if (instance.heatmapScale == null) instance.heatmapScale = HeatmapScale.FIRE;
        if (instance.heatmapSpread == null) instance.heatmapSpread = HeatmapSpread.CONFINED;

        return instance;
    }
    
    public static void save() { try { FILE.getParentFile().mkdirs(); try (FileWriter w = new FileWriter(FILE)) { GSON.toJson(get(), w); } } catch (Exception e) { e.printStackTrace(); } }
}