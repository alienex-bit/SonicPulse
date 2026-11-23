package org.steve.sonicpulse.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

        Skin(int bg, int border, String name) {
            this.bgColor = bg;
            this.borderColor = border;
            this.name = name;
        }

        public int getBgColor() {
            return bgColor;
        }

        public int getBorderColor() {
            return borderColor;
        }

        public String getName() {
            return name;
        }
    }

    public enum SessionMode {
        NONE, FAVOURITES, HISTORY, LOCAL, RADIO, REMOTE
    }

    public enum VisualizerStyle {
        SOLID, FLOATING_PEAKS
    }

    public enum BgEffect {
        OFF, PULSE, AURA, VHS, HEATMAP, STARFIELD
    }

    public enum PulseIntensity {
        SUBTLE, NORMAL, OVERDRIVE
    }

    public enum PulseDecay {
        SNAPPY, FLUID
    }

    public enum AuraSpeed {
        CHILL, NORMAL, WARP
    }

    public enum AuraPalette {
        RAINBOW, AURORA
    }

    public enum VhsGlitch {
        MINOR, HEAVY, CORRUPTED
    }

    public enum VhsScanlines {
        FAINT, DARK, OFF
    }

    public enum HeatmapScale {
        FIRE, PLASMA, TOXIC
    }

    public enum HeatmapSpread {
        CONFINED, UNBOUND
    }

    public enum SparkCount {
        FEW, NORMAL, STORM
    }

    public enum SparkDecay {
        SNAP, DRIFT
    }

    public enum RibbonLayout {
        LOG_TRK_BAR("Logo → Track → Bars"),
        LOG_BAR_TRK("Logo → Bars → Track"),
        TRK_LOG_BAR("Track → Logo → Bars"),
        TRK_BAR_LOG("Track → Bars → Logo"),
        BAR_LOG_TRK("Bars → Logo → Track"),
        BAR_TRK_LOG("Bars → Track → Logo");

        private final String displayName;

        RibbonLayout(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /** Increment this when adding new fields so migration can set safe defaults. */
    public int configVersion = 1;

    public float hudScale = 1.0f;
    public float hudWidth = 1.0f;
    public int barColor = 0xFF00BFFF, titleColor = 0xFFFF00FF, volume = 50;
    public Skin skin = Skin.DEFAULT;
    public SessionMode activeMode = SessionMode.NONE;
    public VisualizerStyle visStyle = VisualizerStyle.SOLID;
    public BgEffect bgEffect = BgEffect.OFF;
    public RibbonLayout ribbonLayout = RibbonLayout.LOG_TRK_BAR;
    public String currentTitle = null, lastRadioUrl = "", localMusicPath = "";
    public List<HistoryEntry> history = new ArrayList<>();

    public String[] radioPresetNames = { "Preset 1", "Preset 2", "Preset 3", "Preset 4" };
    public String[] radioPresetUrls = { "", "", "", "" };

    public boolean hudVisible = true, showLogo = true, showTrack = true, showBars = true, showTooltips = true;

    public boolean enableStreamBuffering = true;
    public int streamBufferSeconds = 5;
    public boolean showBufferingBar = true;

    public float eqBass = 0.0f;
    public float eqTreble = 0.0f;
    public float stereoWidth = 1.0f;
    // New Toggle: Enables Automatic Muffling when head is underwater
    public boolean underwaterMuffle = true;
    // Security: Server-initiated playback is opt-in
    public boolean allowServerPlayback = false;

    public PulseIntensity pulseIntensity = PulseIntensity.NORMAL;
    public PulseDecay pulseDecay = PulseDecay.FLUID;
    public AuraSpeed auraSpeed = AuraSpeed.NORMAL;
    public AuraPalette auraPalette = AuraPalette.RAINBOW;
    public VhsGlitch vhsGlitch = VhsGlitch.HEAVY;
    public VhsScanlines vhsScanlines = VhsScanlines.FAINT;
    // FIXED: Added proper Enum prefixes to resolve build errors
    public HeatmapScale heatmapScale = HeatmapScale.FIRE;
    public HeatmapSpread heatmapSpread = HeatmapSpread.CONFINED;
    public SparkCount sparkCount = SparkCount.NORMAL;
    public SparkDecay sparkDecay = SparkDecay.DRIFT;

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

        public HistoryEntry(String t, String l, String u) {
            type = t;
            label = l;
            url = u;
            this.lastPlayed = System.currentTimeMillis();
        }
    }

    public synchronized void addHistory(String type, String label, String url) {
        HistoryEntry entry = history.stream().filter(e -> e.url.equals(url)).findFirst().orElse(null);
        if (entry != null) {
            if (!entry.favorite) {
                entry.label = label;
            }
            entry.lastPlayed = System.currentTimeMillis();
            if (!entry.favorite) {
                history.remove(entry);
                history.add(0, entry);
            }
        } else {
            history.add(0, new HistoryEntry(type, label, url));
        }
        List<HistoryEntry> nonFavs = history.stream().filter(e -> !e.favorite).collect(Collectors.toList());
        if (nonFavs.size() > 20) {
            nonFavs.sort(Comparator.comparingLong(e -> e.lastPlayed));
            history.remove(nonFavs.get(0));
        }
        save();
    }

    public synchronized List<HistoryEntry> getFavoriteHistory() {
        return history.stream().filter(e -> e.favorite).toList();
    }

    public void setColor(int c) {
        barColor = c;
        save();
    }

    public void setTitleColor(int c) {
        titleColor = c;
        save();
    }

    public void nextSkin() {
        skin = Skin.values()[(skin.ordinal() + 1) % Skin.values().length];
        save();
    }

    public void nextVisStyle() {
        visStyle = VisualizerStyle.values()[(visStyle.ordinal() + 1) % VisualizerStyle.values().length];
        save();
    }

    public void nextBgEffect() {
        bgEffect = BgEffect.values()[(bgEffect.ordinal() + 1) % BgEffect.values().length];
        save();
    }

    public void nextRibbonLayout() {
        ribbonLayout = RibbonLayout.values()[(ribbonLayout.ordinal() + 1) % RibbonLayout.values().length];
        save();
    }

    public void nextPulseIntensity() {
        pulseIntensity = PulseIntensity.values()[(pulseIntensity.ordinal() + 1) % PulseIntensity.values().length];
        save();
    }

    public void nextPulseDecay() {
        pulseDecay = PulseDecay.values()[(pulseDecay.ordinal() + 1) % PulseDecay.values().length];
        save();
    }

    public void nextAuraSpeed() {
        auraSpeed = AuraSpeed.values()[(auraSpeed.ordinal() + 1) % AuraSpeed.values().length];
        save();
    }

    public void nextAuraPalette() {
        auraPalette = AuraPalette.values()[(auraPalette.ordinal() + 1) % AuraPalette.values().length];
        save();
    }

    public void nextVhsGlitch() {
        vhsGlitch = VhsGlitch.values()[(vhsGlitch.ordinal() + 1) % VhsGlitch.values().length];
        save();
    }

    public void nextVhsScanlines() {
        vhsScanlines = VhsScanlines.values()[(vhsScanlines.ordinal() + 1) % VhsScanlines.values().length];
        save();
    }

    public void nextHeatmapScale() {
        heatmapScale = HeatmapScale.values()[(heatmapScale.ordinal() + 1) % HeatmapScale.values().length];
        save();
    }

    public void nextHeatmapSpread() {
        heatmapSpread = HeatmapSpread.values()[(heatmapSpread.ordinal() + 1) % HeatmapSpread.values().length];
        save();
    }

    public void nextSparkCount() {
        sparkCount = SparkCount.values()[(sparkCount.ordinal() + 1) % SparkCount.values().length];
        save();
    }

    public void nextSparkDecay() {
        sparkDecay = SparkDecay.values()[(sparkDecay.ordinal() + 1) % SparkDecay.values().length];
        save();
    }

    private static final File FILE = new File(Minecraft.getInstance().gameDirectory, "config/sonicpulse.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile SonicPulseConfig instance;
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SonicPulse-ConfigSave");
        t.setDaemon(true);
        return t;
    });

    public static SonicPulseConfig get() {
        SonicPulseConfig current = instance;
        if (current != null) {
            return current;
        }
        synchronized (SonicPulseConfig.class) {
            if (instance == null) {
                SonicPulseConfig loaded;
                if (FILE.exists()) {
                    try (FileReader r = new FileReader(FILE, StandardCharsets.UTF_8)) {
                        loaded = GSON.fromJson(r, SonicPulseConfig.class);
                        if (loaded == null) {
                            loaded = new SonicPulseConfig();
                        }
                    } catch (Exception e) {
                        // Config is corrupted — back it up and start fresh
                        File backup = new File(FILE.getPath() + ".bak");
                        backup.delete();
                        FILE.renameTo(backup);
                        System.err.println(
                                "[SonicPulse] Config corrupted, backed up to sonicpulse.json.bak and reset to defaults.");
                        loaded = new SonicPulseConfig();
                    }
                } else {
                    loaded = new SonicPulseConfig();
                }

                // --- Null-safety / schema migration ---
                if (loaded.activeMode == null)
                    loaded.activeMode = SessionMode.NONE;
                if (loaded.bgEffect == null)
                    loaded.bgEffect = BgEffect.OFF;
                if (loaded.skin == null)
                    loaded.skin = Skin.DEFAULT;
                if (loaded.visStyle == null)
                    loaded.visStyle = VisualizerStyle.SOLID;
                if (loaded.ribbonLayout == null)
                    loaded.ribbonLayout = RibbonLayout.LOG_TRK_BAR;
                if (loaded.history == null)
                    loaded.history = new ArrayList<>();
                if (loaded.hudWidth == 0.0f)
                    loaded.hudWidth = 1.0f;
                if (loaded.radioPresetNames == null)
                    loaded.radioPresetNames = new String[] { "Preset 1", "Preset 2", "Preset 3", "Preset 4" };
                if (loaded.radioPresetUrls == null)
                    loaded.radioPresetUrls = new String[] { "", "", "", "" };
                if (loaded.streamBufferSeconds == 0)
                    loaded.streamBufferSeconds = 5;
                // Future field migrations go here, gated on configVersion:
                // if (loaded.configVersion < 2) { loaded.newField = DEFAULT;
                // loaded.configVersion = 2; }

                instance = loaded;
            }
            return instance;
        }
    }

    public static void save() {
        SonicPulseConfig snapshot = get();
        final String json;
        synchronized (snapshot) {
            json = GSON.toJson(snapshot);
        }
        SAVE_EXECUTOR.submit(() -> {
            try {
                Path target = FILE.toPath();
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) {
                System.err.println("[SonicPulse] Failed to save config: " + e.getMessage());
            }
        });
    }
}