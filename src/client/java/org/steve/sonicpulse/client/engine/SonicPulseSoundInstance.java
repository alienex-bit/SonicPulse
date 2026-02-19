package org.steve.sonicpulse.client.engine;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
public class SonicPulseSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private final AudioPlayer player;
    private boolean done = false;
    public SonicPulseSoundInstance(AudioPlayer player) {
        super(Identifier.of("minecraft", "entity.player.levelup"), SoundCategory.RECORDS, net.minecraft.util.math.random.Random.create());
        this.player = player;
        this.repeat = false;
        this.volume = 0.0f;
        this.relative = true;
    }
    @Override
    public void tick() {
        if (player.getPlayingTrack() == null) {
            this.done = true;
        }
    }
    @Override
    public boolean isDone() { return done; }
}
