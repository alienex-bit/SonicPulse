package org.steve.sonicpulse.mixin.client;

import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.MusicInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.steve.sonicpulse.client.SonicPulseClient;

@Mixin(MusicTracker.class)
public class MusicTrackerMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlay(MusicInstance music, CallbackInfo ci) {
        if (SonicPulseClient.getEngine() != null && SonicPulseClient.getEngine().isActiveOrPending()) {
            ci.cancel();
        }
    }
}
