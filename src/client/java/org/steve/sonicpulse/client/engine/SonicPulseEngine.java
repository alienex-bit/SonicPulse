package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

public class SonicPulseEngine {
    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final AudioOutput output;
    private boolean pending = false;

    public SonicPulseEngine() {
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        
        // Register the robust YouTube source
        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager();
        manager.registerSourceManager(youtube);
        
        AudioSourceManagers.registerRemoteSources(manager);
        
        // BUG FIX: Explicitly register Local Sources so the engine can play local mp3/wav files!
        AudioSourceManagers.registerLocalSource(manager);
        
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