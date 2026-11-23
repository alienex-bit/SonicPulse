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
import dev.lavalink.youtube.clients.Android;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.Music;
import dev.lavalink.youtube.clients.TvHtml5Simply;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

public class SonicPulseEngine {
    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final AudioOutput output;
    private volatile boolean pending = false;
    private final LinkedBlockingQueue<byte[]> buffer = new LinkedBlockingQueue<>();
    private final Queue<AudioTrack> playlistQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean buffering = false;
    private volatile boolean trackUsesBuffer = false;
    private volatile int bufferGoal = 0;
    private volatile boolean trackEnded = false;

    public SonicPulseEngine() {
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

        try {
            // Pinned Android client version to work around YouTube's playback restrictions;
            // may need bumping when YouTube deprecates older Android client versions.
            Android.CLIENT_VERSION = "21.02.35";
            Android.BASE_CONFIG = new ClientConfig()
                    .withUserAgent(String.format("com.google.android.youtube/%s (Linux; U; Android %s) gzip",
                            Android.CLIENT_VERSION, Android.ANDROID_VERSION.getOsVersion()))
                    .withClientName("ANDROID")
                    .withClientField("clientVersion", Android.CLIENT_VERSION)
                    .withClientField("androidSdkVersion", Android.ANDROID_VERSION.getSdkVersion())
                    .withUserField("lockedSafetyMode", false);
        } catch (Throwable t) {
            SonicPulseClient.LOGGER.warn("Failed to initialize custom Android client configuration for YouTube: {}", t.getMessage());
        }

        ClientOptions androidOptions = new ClientOptions();
        androidOptions.setPlayback(true);
        androidOptions.setVideoLoading(true);
        androidOptions.setPlaylistLoading(true);
        androidOptions.setSearching(true);

        manager.registerSourceManager(new YoutubeAudioSourceManager(
                true,
                new Android(androidOptions),
                new Music(),
                new TvHtml5Simply()
        ));
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
        playTrack(url, label, type, true);
    }

    private void playTrack(String url, String label, String type, boolean clearExistingBuffer) {
        pending = true;
        trackEnded = false;
        trackUsesBuffer = SonicPulseConfig.get().enableStreamBuffering && !"Local".equalsIgnoreCase(type);
        bufferGoal = SonicPulseConfig.get().streamBufferSeconds * 50;
        if (clearExistingBuffer) {
            playlistQueue.clear();
            buffer.clear();
            buffering = trackUsesBuffer;
            output.resetFilters();
        } else {
            // Preserve buffered tail frames of the previous track on auto-advance
            buffering = trackUsesBuffer && buffer.isEmpty();
        }
        output.start();
        manager.loadItem(url, new TrackLoadHandler(player, this, url, label, type));
    }

    public void enqueuePlaylist(List<AudioTrack> tracks) {
        playlistQueue.clear();
        if (tracks != null && tracks.size() > 1) {
            for (int i = 1; i < tracks.size(); i++) {
                playlistQueue.offer(tracks.get(i));
            }
        }
    }

    private class TrackScheduler extends AudioEventAdapter {
        @Override
        public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
            trackEnded = true;
            buffering = false;
            if (endReason.mayStartNext) {
                AudioTrack queued = playlistQueue.poll();
                if (queued != null) {
                    trackEnded = false;
                    player.playTrack(queued);
                    String title = TrackLoadHandler.resolveDisplayTitle(queued, null, null);
                    SonicPulseConfig.get().currentTitle = title;
                    SonicPulseConfig.get().addHistory("Playlist", title, queued.getInfo().uri);
                } else {
                    playNextInList(track, false);
                }
            }
        }

        @Override
        public void onTrackException(AudioPlayer player, AudioTrack track,
                com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
            SonicPulseClient.LOGGER.error("Track exception during playback: {}", exception.getMessage(), exception);
            trackEnded = true;
            clearBuffering();
            clearPending();
        }

        @Override
        public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
            SonicPulseClient.LOGGER.warn("Track stuck for {}ms: {}", thresholdMs, track.getInfo().title);
            trackEnded = true;
            buffering = false;
        }
    }

    public void playNextInList(AudioTrack endingTrack) {
        playNextInList(endingTrack, true);
    }

    private void playNextInList(AudioTrack endingTrack, boolean clearBuffer) {
        playRelativeInList(endingTrack, 1, clearBuffer);
    }

    public void playPreviousInList(AudioTrack currentTrack) {
        playRelativeInList(currentTrack, -1, true);
    }

    private void playRelativeInList(AudioTrack currentTrack, int delta, boolean clearBuffer) {
        String curUri = currentTrack != null ? currentTrack.getInfo().uri : null;
        SonicPulseConfig config = SonicPulseConfig.get();
        if (config.activeMode == SonicPulseConfig.SessionMode.FAVOURITES) {
            List<SonicPulseConfig.HistoryEntry> favs = config.getFavoriteHistory();
            if (!favs.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < favs.size(); i++) {
                        if (favs.get(i).url.equals(curUri)) {
                            ni = Math.floorMod(i + delta, favs.size());
                            break;
                        }
                    }
                }
                SonicPulseConfig.HistoryEntry n = favs.get(ni);
                playTrack(n.url, n.label, n.type, clearBuffer);
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.LOCAL) {
            List<File> localFiles = new ArrayList<>();
            String path = config.localMusicPath.isEmpty()
                    ? Minecraft.getInstance().gameDirectory.toPath().resolve("sonicpulse")
                            .resolve("music").toString()
                    : config.localMusicPath;
            File dr = new File(path);
            if (dr.exists() && dr.isDirectory()) {
                File[] fls = dr.listFiles((d, n) -> {
                    String nm = n.toLowerCase();
                    return nm.endsWith(".mp3") || nm.endsWith(".wav") || nm.endsWith(".flac");
                });
                if (fls != null) {
                    Arrays.sort(fls, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                    Collections.addAll(localFiles, fls);
                }
            }
            if (!localFiles.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < localFiles.size(); i++) {
                        File fl = localFiles.get(i);
                        if (curUri.equals(fl.getAbsolutePath()) || curUri.equals(fl.toURI().toString())
                                || curUri.replace("\\", "/").endsWith(fl.getName())) {
                            ni = Math.floorMod(i + delta, localFiles.size());
                            break;
                        }
                    }
                }
                File n = localFiles.get(ni);
                playTrack(n.getAbsolutePath(), n.getName(), "Local", clearBuffer);
            }
        } else if (config.activeMode == SonicPulseConfig.SessionMode.HISTORY) {
            List<SonicPulseConfig.HistoryEntry> hist = config.history.stream().filter(e -> !e.favorite)
                    .sorted(Comparator.comparing(e -> e.label)).collect(Collectors.toList());
            if (!hist.isEmpty()) {
                int ni = 0;
                if (curUri != null) {
                    for (int i = 0; i < hist.size(); i++) {
                        if (hist.get(i).url.equals(curUri)) {
                            ni = Math.floorMod(i + delta, hist.size());
                            break;
                        }
                    }
                }
                SonicPulseConfig.HistoryEntry n = hist.get(ni);
                playTrack(n.url, n.label, n.type, clearBuffer);
            }
        }
    }

    public void updateEqualizer() {
        /* Logic moved to AudioOutput for real-time bypass */
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            output.setPlayerSubmerged(client.player != null && client.player.isUnderWater());
            output.setMinecraftVolume(client.options != null
                    ? client.options.getFinalSoundSourceVolume(SoundSource.MUSIC)
                    : 1.0f);
        }

        if (buffering && (buffer.size() >= bufferGoal || trackEnded || (!pending && player.getPlayingTrack() == null))) {
            buffering = false;
        }
    }

    public void clearPending() {
        this.pending = false;
    }

    public void clearBuffering() {
        this.buffering = false;
        this.trackUsesBuffer = false;
        this.buffer.clear();
    }

    public boolean isActiveOrPending() {
        return player.getPlayingTrack() != null || pending || buffering || !buffer.isEmpty();
    }

    public void stop() {
        playlistQueue.clear();
        player.stopTrack();
        pending = false;
        buffering = false;
        trackUsesBuffer = false;
        trackEnded = true;
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
