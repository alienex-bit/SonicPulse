package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

public class SonicPulseEngine {
    private final DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
    private final AudioPlayer player;
    private final AudioOutput output;
    private boolean pending = false;

    public SonicPulseEngine() {
        // FIX: Force Lavaplayer to output 16-bit Little Endian PCM
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);
        
        AudioSourceManagers.registerRemoteSources(manager);
        player = manager.createPlayer();
        output = new AudioOutput(player);
    }

    public void playTrack(String url) { 
        pending = true;
        output.start(); 
        manager.loadItem(url, new TrackLoadHandler(player, this)); 
    }

    public void tick() { }
    public void clearPending() { this.pending = false; }
    public boolean isActiveOrPending() { return player.getPlayingTrack() != null || pending; }
    
    public void stop() { 
        player.stopTrack(); 
        pending = false; 
    }
    
    public AudioPlayer getPlayer() { return player; }
    public float[] getVisualizerData() { return output.getAmplitudes(); }
}
