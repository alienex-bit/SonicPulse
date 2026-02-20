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
        String uri = track.getInfo().uri;
        
        String displayTitle = "YouTube Track";
        if (title != null && !title.equalsIgnoreCase("stream") && !title.toLowerCase().contains("unknown")) {
            if (author != null && !author.equalsIgnoreCase("unknown") && !author.isEmpty()) {
                displayTitle = author + " - " + title;
            } else {
                displayTitle = title;
            }
        }
        
        // BUG FIX: Check if the user has a custom name saved in history for this track!
        for (SonicPulseConfig.HistoryEntry e : SonicPulseConfig.get().history) {
            if (e.url.equals(uri)) {
                displayTitle = e.label;
                break;
            }
        }
        
        SonicPulseConfig.get().currentTitle = displayTitle;
        SonicPulseConfig.get().addHistory("Track", displayTitle, uri);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        engine.clearPending();
        if (!playlist.getTracks().isEmpty()) {
            AudioTrack firstTrack = playlist.getTracks().get(0);
            player.playTrack(firstTrack);
            
            String title = firstTrack.getInfo().title;
            String uri = firstTrack.getInfo().uri;
            
            for (SonicPulseConfig.HistoryEntry e : SonicPulseConfig.get().history) {
                if (e.url.equals(uri)) {
                    title = e.label;
                    break;
                }
            }
            
            if (title != null && !title.equalsIgnoreCase("stream")) {
                SonicPulseConfig.get().currentTitle = title;
                SonicPulseConfig.get().addHistory("Playlist", title, uri);
            }
        }
    }

    @Override
    public void noMatches() { engine.clearPending(); }

    @Override
    public void loadFailed(FriendlyException exception) { engine.clearPending(); }
}