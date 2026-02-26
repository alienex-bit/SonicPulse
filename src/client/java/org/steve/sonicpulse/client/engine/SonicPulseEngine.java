package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.LinkedBlockingQueue;

public class SonicPulseEngine {
    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final AudioOutput output;
    private boolean pending = false;
    private final LinkedBlockingQueue<byte[]> buffer = new LinkedBlockingQueue<>();
    private boolean buffering = false;
    private boolean trackUsesBuffer = false;
    private int bufferGoal = 0;

    public SonicPulseEngine() {
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        manager.registerSourceManager(new YoutubeAudioSourceManager());
        manager.registerSourceManager(new TwitchStreamAudioSourceManager());
        manager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        manager.registerSourceManager(new BandcampAudioSourceManager());
        manager.registerSourceManager(new VimeoAudioSourceManager());
        manager.registerSourceManager(new HttpAudioSourceManager());
        manager.registerSourceManager(new LocalAudioSourceManager());
        player = manager.createPlayer();
        output = new AudioOutput(player);
        player.addListener(new TrackScheduler());
    }

    public void playTrack(String url, String label, String type) {
        pending = true;
        trackUsesBuffer = SonicPulseConfig.get().enableStreamBuffering && !"Local".equalsIgnoreCase(type);
        buffering = trackUsesBuffer;
        bufferGoal = SonicPulseConfig.get().streamBufferSeconds * 50;
        buffer.clear();
        output.start();
        SonicPulseConfig.HistoryEntry savedFav = SonicPulseConfig.get().history.stream()
                .filter(e -> e.url.equals(url) && e.favorite).findFirst().orElse(null);
        String finalLabel = (savedFav != null) ? savedFav.label : ((label != null && !label.isEmpty()) ? label : url);
        if (finalLabel.equals(url) && savedFav == null) {
            if (url.contains("/") || url.contains("\\"))
                finalLabel = url.substring(Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1);
        }
        SonicPulseConfig.get().currentTitle = finalLabel;
        SonicPulseConfig.get().addHistory(type, finalLabel, url);
        manager.loadItem(url, new TrackLoadHandler(player, this));
    }

    private class TrackScheduler extends AudioEventAdapter {
        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            if (endReason.mayStartNext)
                playNextInList(track);
        }
    }

    public void playNextInList(AudioTrack endingTrack) {
        String curUri = endingTrack != null ? endingTrack.getInfo().uri : null;
        SonicPulseConfig config = SonicPulseConfig.get();
        if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES) {
            List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
            if (!favs.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < favs.size(); i++) {
                        if (favs.get(i).url.equals(curUri)) {
                            ni = (i + 1) % favs.size();
                            break;
                        }
                    }
                }
                SonicPulseConfig.HistoryEntry n = favs.get(ni);
                playTrack(n.url, n.label, n.type);
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
            List<File> localFiles = new ArrayList<>();
            String path = config.localMusicPath.isEmpty()
                    ? net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse")
                            .resolve("music").toString()
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
            if (!localFiles.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < localFiles.size(); i++) {
                        File fl = localFiles.get(i);
                        if (curUri.equals(fl.getAbsolutePath()) || curUri.equals(fl.toURI().toString())
                                || curUri.replace("\\", "/").endsWith(fl.getName())) {
                            ni = (i + 1) % localFiles.size();
                            break;
                        }
                    }
                }
                File n = localFiles.get(ni);
                playTrack(n.getAbsolutePath(), n.getName(), "Local");
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.HISTORY) {
            List<SonicPulseConfig.HistoryEntry> hist = config.history.stream().filter(e -> !e.favorite)
                    .sorted(Comparator.comparing(e -> e.label)).collect(Collectors.toList());
            if (!hist.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < hist.size(); i++) {
                        if (hist.get(i).url.equals(curUri)) {
                            ni = (i + 1) % hist.size();
                            break;
                        }
                    }
                }
                SonicPulseConfig.HistoryEntry n = hist.get(ni);
                playTrack(n.url, n.label, n.type);
            }
        }
    }

    public void playPreviousInList(AudioTrack currentTrack) {
        String curUri = currentTrack != null ? currentTrack.getInfo().uri : null;
        SonicPulseConfig config = SonicPulseConfig.get();
        if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES) {
            List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
            if (!favs.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < favs.size(); i++) {
                        if (favs.get(i).url.equals(curUri)) {
                            ni = (i - 1 + favs.size()) % favs.size();
                            break;
                        }
                    }
                }
                SonicPulseConfig.HistoryEntry n = favs.get(ni);
                playTrack(n.url, n.label, n.type);
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
            List<File> localFiles = new ArrayList<>();
            String path = config.localMusicPath.isEmpty()
                    ? net.minecraft.client.MinecraftClient.getInstance().runDirectory.toPath().resolve("sonicpulse")
                            .resolve("music").toString()
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
            if (!localFiles.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < localFiles.size(); i++) {
                        File fl = localFiles.get(i);
                        if (curUri.equals(fl.getAbsolutePath()) || curUri.equals(fl.toURI().toString())
                                || curUri.replace("\\", "/").endsWith(fl.getName())) {
                            ni = (i - 1 + localFiles.size()) % localFiles.size();
                            break;
                        }
                    }
                }
                File n = localFiles.get(ni);
                playTrack(n.getAbsolutePath(), n.getName(), "Local");
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.HISTORY) {
            List<SonicPulseConfig.HistoryEntry> hist = config.history.stream().filter(e -> !e.favorite)
                    .sorted(Comparator.comparing(e -> e.label)).collect(Collectors.toList());
            if (!hist.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < hist.size(); i++) {
                        if (hist.get(i).url.equals(curUri)) {
                            ni = (i - 1 + hist.size()) % hist.size();
                            break;
                        }
                    }
                }
                SonicPulseConfig.HistoryEntry n = hist.get(ni);
                playTrack(n.url, n.label, n.type);
            }
        }
    }

    public void updateEqualizer() {
        /* Logic moved to AudioOutput for real-time bypass */ }

    public void tick() {
        if (buffering && buffer.size() >= bufferGoal)
            buffering = false;
    }

    public void clearPending() {
        this.pending = false;
    }

    public boolean isActiveOrPending() {
        return player.getPlayingTrack() != null || pending || buffering;
    }

    public void stop() {
        player.stopTrack();
        pending = false;
        buffering = false;
        trackUsesBuffer = false;
        buffer.clear();
        output.stop();
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public float[] getVisualizerData() {
        return output.getAmplitudes();
    }

    public boolean doesTrackUseBuffer() {
        return trackUsesBuffer;
    }

    public boolean isBuffering() {
        return buffering;
    }

    public float getBufferProgress() {
        return bufferGoal == 0 ? 0 : Math.min(1.0f, (float) buffer.size() / bufferGoal);
    }

    public void pushFrame(byte[] data) {
        buffer.offer(data);
    }

    public byte[] popFrame() {
        return buffer.poll();
    }
}