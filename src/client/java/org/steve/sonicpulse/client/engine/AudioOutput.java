package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.MinecraftClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import javax.sound.sampled.*;
import java.util.Arrays;

public class AudioOutput {
    private final AudioPlayer player;
    private SourceDataLine line;
    private boolean running = false;
    private Thread thread;
    private volatile float[] amplitudes = new float[32];
    private float maxPeak = 1000000f; 
    
    // OPTIMIZATION: Pre-allocate buffers to prevent heavy GC stutter
    private final float[] realBuffer = new float[512];
    private final float[] imagBuffer = new float[512];

    public AudioOutput(AudioPlayer player) { this.player = player; }
    public float[] getAmplitudes() { return amplitudes.clone(); }
    
    public void start() {
        if (running) return;
        try {
            MinecraftClient.getInstance().getSoundManager().stopAll();
            AudioFormat format = new AudioFormat(48000, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 48000);
            line.start();
            
            player.setVolume(SonicPulseConfig.get().volume);
            running = true;
            thread = new Thread(() -> {
                while (running) {
                    try {
                        AudioFrame frame = player.provide();
                        if (frame != null) {
                            byte[] data = frame.getData();
                            int validLen = data.length - (data.length % 4);
                            if (validLen > 0) {
                                line.write(data, 0, validLen);
                                if (player.getVolume() != SonicPulseConfig.get().volume) {
                                    player.setVolume(SonicPulseConfig.get().volume);
                                }
                                updateAmplitudes(data);
                            }
                        } else {
                            Arrays.fill(amplitudes, 0);
                            Thread.sleep(10);
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }, "SonicPulse-Audio-Decoupled");
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    public void stop() {
        running = false;
        if (line != null) { line.stop(); line.close(); }
        Arrays.fill(amplitudes, 0);
    }
    
    private void updateAmplitudes(byte[] data) {
        int n = 512;
        if (data.length < n * 4) return;
        
        // OPTIMIZATION: Reuse existing arrays instead of allocating new ones
        Arrays.fill(imagBuffer, 0f);
        
        for (int i = 0; i < n; i++) {
            int sampleL = (data[i * 4 + 1] << 8) | (data[i * 4] & 0xFF);
            int sampleR = (data[i * 4 + 3] << 8) | (data[i * 4 + 2] & 0xFF);
            realBuffer[i] = (sampleL + sampleR) / 2.0f;
            realBuffer[i] *= (float)(0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1))));
        }
        
        int m = 9;
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - m);
            if (j > i) { float t = realBuffer[i]; realBuffer[i] = realBuffer[j]; realBuffer[j] = t; }
        }
        for (int s = 1; s <= m; s++) {
            int m2 = 1 << s; int m1 = m2 >> 1;
            float wmR = (float)Math.cos(-2 * Math.PI / m2);
            float wmI = (float)Math.sin(-2 * Math.PI / m2);
            for (int k = 0; k < n; k += m2) {
                float wR = 1, wI = 0;
                for (int j = 0; j < m1; j++) {
                    float tR = wR * realBuffer[k + j + m1] - wI * imagBuffer[k + j + m1];
                    float tI = wR * imagBuffer[k + j + m1] + wI * realBuffer[k + j + m1];
                    float uR = realBuffer[k + j]; float uI = imagBuffer[k + j];
                    realBuffer[k + j] = uR + tR; imagBuffer[k + j] = uI + tI;
                    realBuffer[k + j + m1] = uR - tR; imagBuffer[k + j + m1] = uI - tI;
                    float nWR = wR * wmR - wI * wmI; wI = wR * wmI + wI * wmR; wR = nWR;
                }
            }
        }

        float currentMax = 0;
        int lastBin = 1;
        for (int i = 0; i < 32; i++) {
            int startBin = lastBin;
            int endBin = (int)(Math.pow(n/2.0, (i + 1) / 32.0));
            if (endBin <= startBin) endBin = startBin + 1;
            lastBin = endBin;

            float sum = 0;
            for (int j = startBin; j < endBin && j < n/2; j++) {
                sum += (float)Math.sqrt(realBuffer[j]*realBuffer[j] + imagBuffer[j]*imagBuffer[j]);
            }
            float avg = sum / (endBin - startBin);
            if (avg > currentMax) currentMax = avg;

            maxPeak = maxPeak * 0.99f + Math.max(currentMax, 500000f) * 0.01f;
            
            float freqBoost = 1.0f + (i * 0.15f);
            float val = (avg * freqBoost) / (maxPeak * 0.8f);
            
            if (val > 1.0f) val = 1.0f;

            if (val > amplitudes[i]) {
                amplitudes[i] = val; 
            } else {
                amplitudes[i] = amplitudes[i] * 0.75f + val * 0.25f; 
            }
        }
    }
}