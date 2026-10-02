package org.steve.sonicpulse.mixin.client;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.steve.sonicpulse.client.SonicPulseClient;

@Mixin(MusicManager.class)
public class MusicTrackerMixin {
    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void onStartPlaying(Music music, CallbackInfo ci) {
        if (SonicPulseClient.getEngine() != null && SonicPulseClient.getEngine().isActiveOrPending()) {
            ci.cancel();
        }
    }
}
