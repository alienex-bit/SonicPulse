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

public class SonicPulseEngine {
    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final AudioOutput output;
    private boolean pending = false;

    public SonicPulseEngine() {
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        
        // --- BUG FIX: THE ROUTING CONFLICT ---
        // We manually register our source managers to prevent the old, broken
        // built-in YouTube manager from overriding our custom Lavalink one!
        
        // 1. Robust Custom YouTube Manager
        manager.registerSourceManager(new YoutubeAudioSourceManager());
        
        // 2. Explicitly register the Twitch Manager
        manager.registerSourceManager(new TwitchStreamAudioSourceManager());
        
        // 3. Register the rest of the web sources manually
        manager.registerSourceManager(SoundCloudAudioSourceManager.createDefault());
        manager.registerSourceManager(new BandcampAudioSourceManager());
        manager.registerSourceManager(new VimeoAudioSourceManager());
        manager.registerSourceManager(new HttpAudioSourceManager());
        
        // 4. Register Local Sources for mp3/wav files
        manager.registerSourceManager(new LocalAudioSourceManager());
        
        player = manager.createPlayer();
        output = new AudioOutput(player);
    }

    public void playTrack(String url) { 
        pending = true;
        output.start(); 
        manager.loadItem(url, new TrackLoadHandler(player, this)); 
    }

    public void tick() { 
        // Engine tick logic if needed in future
    }

    public void clearPending() { this.pending = false; }

    public boolean isActiveOrPending() { 
        return player.getPlayingTrack() != null || pending; 
    }
    
    public void stop() { 
        player.stopTrack(); 
        pending = false; 
    }
    
    public AudioPlayer getPlayer() { return player; }
    
    public float[] getVisualizerData() { return output.getAmplitudes(); }
}