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
        engine.clearPending();
        player.playTrack(track);
        
        String title = track.getInfo().title;
        String author = track.getInfo().author;
        
        // Only update history if we have real data that isn't "Unknown"
        if (title != null && !title.toLowerCase().contains("unknown") && !title.equalsIgnoreCase("stream")) {
            String label = title;
            if (author != null && !author.equalsIgnoreCase("unknown")) {
                label = author + " - " + title;
            }
            SonicPulseConfig.get().addHistory("Track", label, track.getInfo().uri);
        }
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        engine.clearPending();
        if (!playlist.getTracks().isEmpty()) {
            AudioTrack firstTrack = playlist.getTracks().get(0);
            player.playTrack(firstTrack);
            
            String title = firstTrack.getInfo().title;
            if (title != null && !title.toLowerCase().contains("unknown")) {
                SonicPulseConfig.get().addHistory("Playlist", title, firstTrack.getInfo().uri);
            }
        }
    }

    @Override
    public void noMatches() { engine.clearPending(); }

    @Override
    public void loadFailed(FriendlyException exception) { engine.clearPending(); }
}