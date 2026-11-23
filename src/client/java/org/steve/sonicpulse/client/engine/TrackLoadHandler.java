package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;

public class TrackLoadHandler implements AudioLoadResultHandler {
    private final AudioPlayer player;
    private final SonicPulseEngine engine;
    private final String requestedUrl;
    private final String requestedLabel;
    private final String requestedType;

    public TrackLoadHandler(AudioPlayer player, SonicPulseEngine engine, String requestedUrl, String requestedLabel,
            String requestedType) {
        this.player = player;
        this.engine = engine;
        this.requestedUrl = requestedUrl;
        this.requestedLabel = requestedLabel;
        this.requestedType = (requestedType != null && !requestedType.isEmpty()) ? requestedType : "Track";
    }

    public static String resolveDisplayTitle(AudioTrack track, String requestedUrl, String requestedLabel) {
        String title = track.getInfo().title;
        String author = track.getInfo().author;
        String uri = track.getInfo().uri;

        // Prefer a user-saved custom label from history/favorites if present
        for (SonicPulseConfig.HistoryEntry e : SonicPulseConfig.get().history) {
            if ((uri != null && uri.equals(e.url)) || (requestedUrl != null && requestedUrl.equals(e.url))) {
                if (e.favorite && e.label != null && !e.label.isEmpty()) {
                    return e.label;
                }
            }
        }

        if (title != null && !title.isEmpty() && !title.equalsIgnoreCase("stream")
                && !title.toLowerCase().contains("unknown")) {
            if (author != null && !author.equalsIgnoreCase("unknown") && !author.isEmpty()) {
                return author + " - " + title;
            }
            return title;
        }

        if (requestedLabel != null && !requestedLabel.isEmpty() && !requestedLabel.equals(requestedUrl)) {
            return requestedLabel;
        }

        if (requestedUrl != null && (requestedUrl.contains("/") || requestedUrl.contains("\\"))) {
            String tail = requestedUrl.substring(Math.max(requestedUrl.lastIndexOf('/'), requestedUrl.lastIndexOf('\\')) + 1);
            if (!tail.isEmpty()) {
                return tail;
            }
        }

        return "Unknown Track";
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        engine.clearPending();
        player.playTrack(track);

        String uri = track.getInfo().uri != null ? track.getInfo().uri : requestedUrl;
        String displayTitle = resolveDisplayTitle(track, requestedUrl, requestedLabel);

        SonicPulseConfig.get().currentTitle = displayTitle;
        SonicPulseConfig.get().addHistory(requestedType, displayTitle, uri);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        engine.clearPending();
        if (!playlist.getTracks().isEmpty()) {
            AudioTrack firstTrack = playlist.getTracks().get(0);
            engine.enqueuePlaylist(playlist.getTracks());
            player.playTrack(firstTrack);

            String uri = firstTrack.getInfo().uri != null ? firstTrack.getInfo().uri : requestedUrl;
            String displayTitle = resolveDisplayTitle(firstTrack, requestedUrl, requestedLabel);

            SonicPulseConfig.get().currentTitle = displayTitle;
            SonicPulseConfig.get().addHistory("Playlist", displayTitle, uri);
        } else {
            engine.clearBuffering();
        }
    }

    @Override
    public void noMatches() {
        engine.clearPending();
        engine.clearBuffering();
    }

    @Override
    public void loadFailed(FriendlyException exception) {
        SonicPulseClient.LOGGER.error("Failed to load audio track: {}", exception.getMessage(), exception);
        engine.clearPending();
        engine.clearBuffering();
    }
}
