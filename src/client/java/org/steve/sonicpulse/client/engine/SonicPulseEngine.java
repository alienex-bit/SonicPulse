package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.twitch.TwitchStreamAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.vimeo.VimeoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class SonicPulseEngine {
    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final AudioOutput output;
    private boolean pending = false;

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
    }

    public void playTrack(String url, String label, String type) { 
        pending = true;
        output.start();
        
        // ENGINE INTELLIGENCE: Check if track exists in Favs to use user's custom name
        SonicPulseConfig.HistoryEntry savedFav = SonicPulseConfig.get().history.stream()
            .filter(e -> e.url.equals(url) && e.favorite).findFirst().orElse(null);
            
        String finalLabel = (savedFav != null) ? savedFav.label : ((label != null && !label.isEmpty()) ? label : url);
        
        if (finalLabel.equals(url) && savedFav == null) {
            if (url.contains("/") || url.contains("\\")) {
                finalLabel = url.substring(Math.max(url.lastIndexOf("/"), url.lastIndexOf("\\")) + 1);
            }
        }
        
        SonicPulseConfig.get().currentTitle = finalLabel;
        SonicPulseConfig.get().addHistory(type, finalLabel, url);
        manager.loadItem(url, new TrackLoadHandler(player, this)); 
    }

    public void tick() {}
    public void clearPending() { this.pending = false; }
    public boolean isActiveOrPending() { return player.getPlayingTrack() != null || pending; }
    public void stop() { player.stopTrack(); pending = false; }
    public AudioPlayer getPlayer() { return player; }
    public float[] getVisualizerData() { return output.getAmplitudes(); }
}