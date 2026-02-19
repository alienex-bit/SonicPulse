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
    private final float[] amplitudes = new float[32];

    public AudioOutput(AudioPlayer player) { this.player = player; }
    public float[] getAmplitudes() { return amplitudes; }
    
    public void start() {
        if (running) return;
        try {
            MinecraftClient.getInstance().getSoundManager().stopAll();
            
            // FIX: Set to Little Endian (false) to match the Engine config
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
                            
                            // Safety: Trim non-integral frames
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
                    } catch (Exception e) {
                        System.err.println("Audio Error: " + e.getMessage());
                    }
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
        float[] real = new float[n];
        float[] imag = new float[n];
        
        // Parsing for Little Endian (LSB first)
        for (int i = 0; i < n; i++) {
            // Index 0 is LSB, Index 1 is MSB
            int sampleL = (data[i * 4 + 1] << 8) | (data[i * 4] & 0xFF);
            int sampleR = (data[i * 4 + 3] << 8) | (data[i * 4 + 2] & 0xFF);
            
            real[i] = (sampleL + sampleR) / 2.0f;
            real[i] *= (float)(0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1))));
        }
        
        int m = 9;
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - m);
            if (j > i) { float t = real[i]; real[i] = real[j]; real[j] = t; }
        }
        for (int s = 1; s <= m; s++) {
            int m2 = 1 << s; int m1 = m2 >> 1;
            float wmR = (float)Math.cos(-2 * Math.PI / m2);
            float wmI = (float)Math.sin(-2 * Math.PI / m2);
            for (int k = 0; k < n; k += m2) {
                float wR = 1, wI = 0;
                for (int j = 0; j < m1; j++) {
                    float tR = wR * real[k + j + m1] - wI * imag[k + j + m1];
                    float tI = wR * imag[k + j + m1] + wI * real[k + j + m1];
                    float uR = real[k + j]; float uI = imag[k + j];
                    real[k + j] = uR + tR; imag[k + j] = uI + tI;
                    real[k + j + m1] = uR - tR; imag[k + j + m1] = uI - tI;
                    float nextWR = wR * wmR - wI * wmI;
                    wI = wR * wmI + wI * wmR; wR = nextWR;
                }
            }
        }
        for (int i = 0; i < 32; i++) {
            float lowFreq = (float)Math.pow(2, i * 8.0 / 31.0); 
            int startBin = Math.max(1, (int)lowFreq);
            int endBin = Math.max(startBin + 1, (int)Math.pow(2, (i + 1) * 8.0 / 31.0));
            float sum = 0;
            for (int j = startBin; j < endBin && j < n/2; j++) {
                sum += (float)Math.sqrt(real[j]*real[j] + imag[j]*imag[j]);
            }
            float avg = sum / (endBin - startBin);
            float boost = 1.0f + ((float)i / 31.0f) * 6.0f;
            if (i < 6) { boost *= (0.2f + (i * 0.1f)); }
            float val = (avg * boost) / 2500000f; 
            if (val > 1.0f) val = 1.0f;
            amplitudes[i] = amplitudes[i] * 0.4f + val * 0.6f;
        }
    }
}
