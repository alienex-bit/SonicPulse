package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class TrackLoadHandler implements AudioLoadResultHandler {
    private final AudioPlayer player;
    private final SonicPulseEngine engine;

    public TrackLoadHandler(AudioPlayer player, SonicPulseEngine engine) {
        this.player = player;
        this.engine = engine;
    }

    @Override 
    public void trackLoaded(AudioTrack track) { 
        // Hook into History!
        SonicPulseConfig.get().addHistory(track.getInfo().author, track.getInfo().title, track.getInfo().uri);
        player.playTrack(track); 
        engine.clearPending(); 
    }

    @Override 
    public void playlistLoaded(AudioPlaylist playlist) { 
        if (!playlist.getTracks().isEmpty()) {
            AudioTrack track = playlist.getTracks().get(0);
            // Hook into History for playlists too!
            SonicPulseConfig.get().addHistory(track.getInfo().author, track.getInfo().title, track.getInfo().uri);
            player.playTrack(track); 
        }
        engine.clearPending(); 
    }

    @Override public void noMatches() { engine.clearPending(); }
    @Override public void loadFailed(FriendlyException exception) { engine.clearPending(); }
}
