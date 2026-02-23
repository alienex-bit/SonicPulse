package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.MinecraftClient;
import org.steve.sonicpulse.client.SonicPulseClient;
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
    
    private final float[] realBuffer = new float[512];
    private final float[] imagBuffer = new float[512];

    private long lastFrameTime = System.currentTimeMillis();
    private static final int SAMPLE_RATE = 48000;
    private static final int HARDWARE_BUFFER_SIZE = SAMPLE_RATE * 4; 

    // BIQUAD FILTER STATES
    private float b0, b1, b2, a1, a2;
    private float t_b0, t_b1, t_b2, t_a1, t_a2;
    private float m_b0, m_b1, m_b2, m_a1, m_a2; // Muffle coefficients
    private float x1L, x2L, y1L, y2L, x1R, x2R, y1R, y2R;
    private float tx1L, tx2L, ty1L, ty2L, tx1R, tx2R, ty1R, ty2R;
    private float mx1L, mx2L, my1L, my2L, mx1R, mx2R, my1R, my2R; 

    public AudioOutput(AudioPlayer player) { this.player = player; }
    public float[] getAmplitudes() { return amplitudes.clone(); }
    
    public void start() {
        if (running) return;
        try {
            ensureLineOpen();
            running = true;
            thread = new Thread(this::runAudioLoop, "SonicPulse-Audio-DSP");
            thread.setDaemon(true);
            thread.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void ensureLineOpen() throws LineUnavailableException {
        if (line == null || !line.isOpen()) {
            MinecraftClient.getInstance().getSoundManager().stopAll();
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, HARDWARE_BUFFER_SIZE);
            line.start();
        }
    }

    private void updateFilterCoefficients() {
        SonicPulseConfig cfg = SonicPulseConfig.get();
        setupLowShelf(150, cfg.eqBass * 15.0f);
        setupHighShelf(4000, cfg.eqTreble * 15.0f);
        
        // NEW Logic: Only apply if toggle is ENABLED AND player is actually UNDERWATER
        boolean submerged = MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isSubmergedInWater();
        if (cfg.underwaterMuffle && submerged) {
            setupLowPass(400, 0.707f); 
        }
    }

    private void setupLowShelf(float freq, float db) {
        float A = (float) Math.pow(10, db / 40);
        float omega = (float) (2 * Math.PI * freq / SAMPLE_RATE);
        float sn = (float) Math.sin(omega); float cs = (float) Math.cos(omega);
        float alpha = sn / 2 * (float) Math.sqrt((A + 1/A) * (1/1.0f - 1) + 2);
        b0 = A*((A+1) - (A-1)*cs + 2*(float)Math.sqrt(A)*alpha);
        b1 = 2*A*((A-1) - (A+1)*cs);
        b2 = A*((A+1) - (A-1)*cs - 2*(float)Math.sqrt(A)*alpha);
        float a0 = (A+1) + (A-1)*cs + 2*(float)Math.sqrt(A)*alpha;
        a1 = -2*((A-1) + (A+1)*cs); a2 = (A+1) + (A-1)*cs - 2*(float)Math.sqrt(A)*alpha;
        b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0;
    }

    private void setupHighShelf(float freq, float db) {
        float A = (float) Math.pow(10, db / 40);
        float omega = (float) (2 * Math.PI * freq / SAMPLE_RATE);
        float sn = (float) Math.sin(omega); float cs = (float) Math.cos(omega);
        float alpha = sn / 2 * (float) Math.sqrt((A + 1/A) * (1/1.0f - 1) + 2);
        t_b0 = A*((A+1) + (A-1)*cs + 2*(float)Math.sqrt(A)*alpha);
        t_b1 = -2*A*((A-1) + (A+1)*cs);
        t_b2 = A*((A+1) + (A-1)*cs - 2*(float)Math.sqrt(A)*alpha);
        float ta0 = (A+1) - (A-1)*cs + 2*(float)Math.sqrt(A)*alpha;
        t_a1 = 2*((A-1) - (A+1)*cs); t_a2 = (A+1) - (A-1)*cs - 2*(float)Math.sqrt(A)*alpha;
        t_b0 /= ta0; t_b1 /= ta0; t_b2 /= ta0; t_a1 /= ta0; t_a2 /= ta0;
    }

    private void setupLowPass(float freq, float q) {
        float omega = (float) (2 * Math.PI * freq / SAMPLE_RATE);
        float sn = (float) Math.sin(omega); float cs = (float) Math.cos(omega);
        float alpha = sn / (2 * q);
        m_b0 = (1 - cs) / 2; m_b1 = 1 - cs; m_b2 = (1 - cs) / 2;
        float a0 = 1 + alpha; m_a1 = -2 * cs; m_a2 = 1 - alpha;
        m_b0 /= a0; m_b1 /= a0; m_b2 /= a0; m_a1 /= a0; m_a2 /= a0;
    }
    
    private void runAudioLoop() {
        while (running) {
            try {
                SonicPulseEngine engine = SonicPulseClient.getEngine();
                AudioFrame frame = player.provide();
                byte[] dataToPlay = null;

                if (frame != null) {
                    lastFrameTime = System.currentTimeMillis();
                    if (engine != null && engine.doesTrackUseBuffer()) {
                        engine.pushFrame(frame.getData());
                        if (!engine.isBuffering()) dataToPlay = engine.popFrame();
                    } else { dataToPlay = frame.getData(); }
                } else if (engine != null && engine.doesTrackUseBuffer() && !engine.isBuffering()) {
                    dataToPlay = engine.popFrame();
                }

                if (dataToPlay != null) {
                    ensureLineOpen();
                    updateFilterCoefficients();
                    SonicPulseConfig cfg = SonicPulseConfig.get();
                    
                    // Master Condition: Toggle ON + In Water
                    boolean actuallyMuffle = cfg.underwaterMuffle && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.isSubmergedInWater();
                    
                    for (int i = 0; i < dataToPlay.length; i += 4) {
                        float left = (short)((dataToPlay[i+1] << 8) | (dataToPlay[i] & 0xFF));
                        float right = (short)((dataToPlay[i+3] << 8) | (dataToPlay[i+2] & 0xFF));

                        // 1. EQ
                        float yL = b0*left + b1*x1L + b2*x2L - a1*y1L - a2*y2L;
                        x2L = x1L; x1L = left; y2L = y1L; y1L = yL;
                        float yR = b0*right + b1*x1R + b2*x2R - a1*y1R - a2*y2R;
                        x2R = x1R; x1R = right; y2R = y1R; y1R = yR;

                        float tyL = t_b0*yL + t_b1*tx1L + t_b2*tx2L - t_a1*ty1L - t_a2*ty2L;
                        tx2L = tx1L; tx1L = yL; ty2L = ty1L; ty1L = tyL;
                        float tyR = t_b0*yR + t_b1*tx1R + t_b2*tx2R - t_a1*ty1R - t_a2*ty2R;
                        tx2R = tx1R; tx1R = yR; ty2R = ty1R; ty1R = tyR;

                        // 2. Conditional Muffle (Automatic)
                        if (actuallyMuffle) {
                            float myL = m_b0*tyL + m_b1*mx1L + m_b2*mx2L - m_a1*my1L - m_a2*my2L;
                            mx2L = mx1L; mx1L = tyL; my2L = my1L; my1L = myL; tyL = myL;
                            float myR = m_b0*tyR + m_b1*mx1R + m_b2*mx2R - m_a1*my1R - m_a2*my2R;
                            mx2R = mx1R; mx1R = tyR; my2R = my1R; my1R = myR; tyR = myR;
                        }

                        // 3. Widening
                        float mid = (tyL + tyR) * 0.5f;
                        float side = (tyR - tyL) * 0.5f;
                        side *= cfg.stereoWidth;

                        float outL_f = mid - side; float outR_f = mid + side;
                        short outL = (short)Math.max(-32768, Math.min(32767, outL_f));
                        short outR = (short)Math.max(-32768, Math.min(32767, outR_f));

                        dataToPlay[i] = (byte)(outL & 0xFF); dataToPlay[i+1] = (byte)((outL >> 8) & 0xFF);
                        dataToPlay[i+2] = (byte)(outR & 0xFF); dataToPlay[i+3] = (byte)((outR >> 8) & 0xFF);
                    }
                    line.write(dataToPlay, 0, dataToPlay.length - (dataToPlay.length % 4));
                    updateAmplitudes(dataToPlay);
                } else {
                    Arrays.fill(amplitudes, 0);
                    Thread.sleep(10);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
    
    public void stop() { running = false; if (line != null) { line.stop(); line.close(); } Arrays.fill(amplitudes, 0); }
    
    private void updateAmplitudes(byte[] data) {
        int n = 512; if (data.length < n * 4) return;
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
            float wmR = (float)Math.cos(-2 * Math.PI / m2); float wmI = (float)Math.sin(-2 * Math.PI / m2);
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
        float currentMax = 0; int lastBin = 1;
        for (int i = 0; i < 32; i++) {
            int startBin = lastBin; int endBin = (int)(Math.pow(n/2.0, (i + 1) / 32.0));
            if (endBin <= startBin) endBin = startBin + 1;
            lastBin = endBin; float sum = 0;
            for (int j = startBin; j < endBin && j < n/2; j++) sum += (float)Math.sqrt(realBuffer[j]*realBuffer[j] + imagBuffer[j]*imagBuffer[j]);
            float avg = sum / (endBin - startBin);
            if (avg > currentMax) currentMax = avg;
            maxPeak = maxPeak * 0.99f + Math.max(currentMax, 500000f) * 0.01f;
            float val = (avg * (1.0f + (i * 0.15f))) / (maxPeak * 0.8f);
            if (val > amplitudes[i]) amplitudes[i] = Math.min(1f, val); 
            else amplitudes[i] = amplitudes[i] * 0.75f + Math.min(1f, val) * 0.25f; 
        }
    }
}