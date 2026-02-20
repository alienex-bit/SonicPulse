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
        
        String displayTitle = "YouTube Track";
        if (title != null && !title.equalsIgnoreCase("stream") && !title.toLowerCase().contains("unknown")) {
            // Use | as a separator so the HUD can split Artist and Song into two lines
            if (author != null && !author.equalsIgnoreCase("unknown") && !author.isEmpty()) {
                displayTitle = author + "|" + title;
            } else {
                displayTitle = title;
            }
        }
        
        SonicPulseConfig.get().currentTitle = displayTitle;
        SonicPulseConfig.get().addHistory("Track", displayTitle.replace("|", " - "), track.getInfo().uri);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        engine.clearPending();
        if (!playlist.getTracks().isEmpty()) {
            AudioTrack firstTrack = playlist.getTracks().get(0);
            player.playTrack(firstTrack);
            
            String title = firstTrack.getInfo().title;
            if (title != null && !title.equalsIgnoreCase("stream")) {
                SonicPulseConfig.get().currentTitle = title;
                SonicPulseConfig.get().addHistory("Playlist", title, firstTrack.getInfo().uri);
            }
        }
    }

    @Override
    public void noMatches() { engine.clearPending(); }

    @Override
    public void loadFailed(FriendlyException exception) { engine.clearPending(); }
}