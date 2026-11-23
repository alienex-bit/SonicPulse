package org.steve.sonicpulse.client.engine;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.Minecraft;
import org.steve.sonicpulse.client.SonicPulseClient;
import org.steve.sonicpulse.client.config.SonicPulseConfig;
import javax.sound.sampled.*;
import java.util.Arrays;

public class AudioOutput {
    private static final float SQRT2 = (float) Math.sqrt(2.0);
    private static final int SAMPLE_RATE = 48000;
    private static final int HARDWARE_BUFFER_SIZE = SAMPLE_RATE * 4;
    private static final int MAX_CONSECUTIVE_ERRORS = 5;

    private final AudioPlayer player;
    private SourceDataLine line;
    private volatile boolean running = false;
    private volatile boolean playerSubmerged = false;
    private volatile float minecraftVolume = 1.0f;
    private volatile boolean resetFiltersPending = false;
    private boolean wasMuffled = false;
    private Thread thread;
    private volatile float[] amplitudes = new float[32];
    private final float[] bandAverages = new float[32];
    private float maxPeak = 1000000f;

    private final float[] realBuffer = new float[512];
    private final float[] imagBuffer = new float[512];

    // BIQUAD FILTER STATES
    private float b0, b1, b2, a1, a2;
    private float t_b0, t_b1, t_b2, t_a1, t_a2;
    private float m_b0, m_b1, m_b2, m_a1, m_a2; // Muffle coefficients
    private float x1L, x2L, y1L, y2L, x1R, x2R, y1R, y2R;
    private float tx1L, tx2L, ty1L, ty2L, tx1R, tx2R, ty1R, ty2R;
    private float mx1L, mx2L, my1L, my2L, mx1R, mx2R, my1R, my2R;

    public AudioOutput(AudioPlayer player) {
        this.player = player;
    }

    public float[] getAmplitudes() {
        return amplitudes.clone();
    }

    public void setPlayerSubmerged(boolean submerged) {
        this.playerSubmerged = submerged;
    }

    public void setMinecraftVolume(float volume) {
        this.minecraftVolume = Math.max(0.0f, Math.min(1.0f, volume));
    }

    public void resetFilters() {
        this.resetFiltersPending = true;
    }

    private void clearAllFilterState() {
        x1L = x2L = y1L = y2L = x1R = x2R = y1R = y2R = 0f;
        tx1L = tx2L = ty1L = ty2L = tx1R = tx2R = ty1R = ty2R = 0f;
        clearMuffleFilterState();
        wasMuffled = false;
    }

    private void clearMuffleFilterState() {
        mx1L = mx2L = my1L = my2L = mx1R = mx2R = my1R = my2R = 0f;
    }

    public synchronized void start() {
        if (running && thread != null && thread.isAlive()) {
            return;
        }
        running = true;
        thread = new Thread(this::runAudioLoop, "SonicPulse-Audio-DSP");
        thread.setDaemon(true);
        thread.start();
    }

    private synchronized void ensureLineOpen() throws LineUnavailableException {
        if (!running) {
            return;
        }
        if (line == null || !line.isOpen()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> client.getSoundManager().stop());
            }
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, HARDWARE_BUFFER_SIZE);
            line.start();
        }
    }

    private synchronized void closeLine() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    private void updateFilterCoefficients(boolean submerged) {
        SonicPulseConfig cfg = SonicPulseConfig.get();
        setupLowShelf(150, cfg.eqBass * 15.0f);
        setupHighShelf(4000, cfg.eqTreble * 15.0f);

        if (cfg.underwaterMuffle && submerged) {
            setupLowPass(400, 0.707f);
        }
    }

    private void setupLowShelf(float freq, float db) {
        float A = (float) Math.pow(10, db / 40);
        float omega = (float) (2 * Math.PI * freq / SAMPLE_RATE);
        float sn = (float) Math.sin(omega);
        float cs = (float) Math.cos(omega);
        // Shelf slope S = 1 simplification: alpha = sin(omega) / 2 * sqrt(2)
        float alpha = (sn * 0.5f) * SQRT2;
        float sqrtA2Alpha = 2 * (float) Math.sqrt(A) * alpha;
        b0 = A * ((A + 1) - (A - 1) * cs + sqrtA2Alpha);
        b1 = 2 * A * ((A - 1) - (A + 1) * cs);
        b2 = A * ((A + 1) - (A - 1) * cs - sqrtA2Alpha);
        float a0 = (A + 1) + (A - 1) * cs + sqrtA2Alpha;
        a1 = -2 * ((A - 1) + (A + 1) * cs);
        a2 = (A + 1) + (A - 1) * cs - sqrtA2Alpha;
        b0 /= a0;
        b1 /= a0;
        b2 /= a0;
        a1 /= a0;
        a2 /= a0;
    }

    private void setupHighShelf(float freq, float db) {
        float A = (float) Math.pow(10, db / 40);
        float omega = (float) (2 * Math.PI * freq / SAMPLE_RATE);
        float sn = (float) Math.sin(omega);
        float cs = (float) Math.cos(omega);
        // Shelf slope S = 1 simplification: alpha = sin(omega) / 2 * sqrt(2)
        float alpha = (sn * 0.5f) * SQRT2;
        float sqrtA2Alpha = 2 * (float) Math.sqrt(A) * alpha;
        t_b0 = A * ((A + 1) + (A - 1) * cs + sqrtA2Alpha);
        t_b1 = -2 * A * ((A - 1) + (A + 1) * cs);
        t_b2 = A * ((A + 1) + (A - 1) * cs - sqrtA2Alpha);
        float ta0 = (A + 1) - (A - 1) * cs + sqrtA2Alpha;
        t_a1 = 2 * ((A - 1) - (A + 1) * cs);
        t_a2 = (A + 1) - (A - 1) * cs - sqrtA2Alpha;
        t_b0 /= ta0;
        t_b1 /= ta0;
        t_b2 /= ta0;
        t_a1 /= ta0;
        t_a2 /= ta0;
    }

    private void setupLowPass(float freq, float q) {
        float omega = (float) (2 * Math.PI * freq / SAMPLE_RATE);
        float sn = (float) Math.sin(omega);
        float cs = (float) Math.cos(omega);
        float alpha = sn / (2 * q);
        m_b0 = (1 - cs) / 2;
        m_b1 = 1 - cs;
        m_b2 = (1 - cs) / 2;
        float a0 = 1 + alpha;
        m_a1 = -2 * cs;
        m_a2 = 1 - alpha;
        m_b0 /= a0;
        m_b1 /= a0;
        m_b2 /= a0;
        m_a1 /= a0;
        m_a2 /= a0;
    }

    private void runAudioLoop() {
        int consecutiveErrors = 0;
        try {
            while (running) {
                try {
                    SonicPulseEngine engine = SonicPulseClient.getEngine();
                    AudioFrame frame = player.provide();
                    byte[] dataToPlay = null;

                    if (frame != null) {
                        if (engine != null && engine.doesTrackUseBuffer()) {
                            engine.pushFrame(frame.getData());
                            if (!engine.isBuffering())
                                dataToPlay = engine.popFrame();
                        } else {
                            dataToPlay = frame.getData();
                        }
                    } else if (engine != null && engine.doesTrackUseBuffer() && !engine.isBuffering()) {
                        dataToPlay = engine.popFrame();
                    }

                    if (dataToPlay != null) {
                        ensureLineOpen();
                        SourceDataLine activeLine = this.line;
                        if (!running || activeLine == null) {
                            break;
                        }

                        if (resetFiltersPending) {
                            clearAllFilterState();
                            resetFiltersPending = false;
                        }

                        SonicPulseConfig cfg = SonicPulseConfig.get();
                        boolean submerged = this.playerSubmerged;
                        updateFilterCoefficients(submerged);

                        boolean actuallyMuffle = cfg.underwaterMuffle && submerged;
                        if (actuallyMuffle != wasMuffled) {
                            clearMuffleFilterState();
                            wasMuffled = actuallyMuffle;
                        }

                        // Combine Minecraft's Master * Music volume with SonicPulse's own volume setting (0-100%)
                        float gain = this.minecraftVolume * Math.max(0.0f, Math.min(1.0f, cfg.volume / 100.0f));

                        for (int i = 0; i < dataToPlay.length; i += 4) {
                            float left = (short) ((dataToPlay[i + 1] << 8) | (dataToPlay[i] & 0xFF));
                            float right = (short) ((dataToPlay[i + 3] << 8) | (dataToPlay[i + 2] & 0xFF));

                            // 1. EQ
                            float yL = b0 * left + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L;
                            x2L = x1L;
                            x1L = left;
                            y2L = y1L;
                            y1L = yL;
                            float yR = b0 * right + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R;
                            x2R = x1R;
                            x1R = right;
                            y2R = y1R;
                            y1R = yR;

                            float tyL = t_b0 * yL + t_b1 * tx1L + t_b2 * tx2L - t_a1 * ty1L - t_a2 * ty2L;
                            tx2L = tx1L;
                            tx1L = yL;
                            ty2L = ty1L;
                            ty1L = tyL;
                            float tyR = t_b0 * yR + t_b1 * tx1R + t_b2 * tx2R - t_a1 * ty1R - t_a2 * ty2R;
                            tx2R = tx1R;
                            tx1R = yR;
                            ty2R = ty1R;
                            ty1R = tyR;

                            // 2. Conditional Muffle (Automatic)
                            if (actuallyMuffle) {
                                float myL = m_b0 * tyL + m_b1 * mx1L + m_b2 * mx2L - m_a1 * my1L - m_a2 * my2L;
                                mx2L = mx1L;
                                mx1L = tyL;
                                my2L = my1L;
                                my1L = myL;
                                tyL = myL;
                                float myR = m_b0 * tyR + m_b1 * mx1R + m_b2 * mx2R - m_a1 * my1R - m_a2 * my2R;
                                mx2R = mx1R;
                                mx1R = tyR;
                                my2R = my1R;
                                my1R = myR;
                                tyR = myR;
                            }

                            // 3. Widening & Volume Gain
                            float mid = (tyL + tyR) * 0.5f;
                            float side = (tyR - tyL) * 0.5f;
                            side *= cfg.stereoWidth;

                            float outL_f = (mid - side) * gain;
                            float outR_f = (mid + side) * gain;
                            short outL = (short) Math.max(-32768, Math.min(32767, outL_f));
                            short outR = (short) Math.max(-32768, Math.min(32767, outR_f));

                            dataToPlay[i] = (byte) (outL & 0xFF);
                            dataToPlay[i + 1] = (byte) ((outL >> 8) & 0xFF);
                            dataToPlay[i + 2] = (byte) (outR & 0xFF);
                            dataToPlay[i + 3] = (byte) ((outR >> 8) & 0xFF);
                        }
                        activeLine.write(dataToPlay, 0, dataToPlay.length - (dataToPlay.length % 4));
                        consecutiveErrors = 0;

                        if (cfg.hudVisible && (cfg.showBars || cfg.bgEffect != SonicPulseConfig.BgEffect.OFF))
                            updateAmplitudes(dataToPlay, gain);
                        else
                            Arrays.fill(amplitudes, 0);
                    } else {
                        Arrays.fill(amplitudes, 0);
                        Thread.sleep(10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    consecutiveErrors++;
                    SonicPulseClient.LOGGER.error("Audio output error ({}/{}): {}", consecutiveErrors,
                            MAX_CONSECUTIVE_ERRORS, e.getMessage());
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        SonicPulseClient.LOGGER.error("Stopping audio output loop after {} consecutive failures.",
                                MAX_CONSECUTIVE_ERRORS);
                        running = false;
                        break;
                    }
                    try {
                        Thread.sleep(250L * consecutiveErrors);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            closeLine();
            Arrays.fill(amplitudes, 0);
        }
    }

    public synchronized void stop() {
        running = false;
        Thread t = this.thread;
        this.thread = null;
        if (t != null) {
            t.interrupt();
            if (t != Thread.currentThread()) {
                try {
                    t.join(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        closeLine();
        Arrays.fill(amplitudes, 0);
    }

    private void updateAmplitudes(byte[] data, float gain) {
        if (gain <= 0.001f) {
            Arrays.fill(amplitudes, 0);
            return;
        }
        int n = 512;
        if (data.length < n * 4)
            return;
        Arrays.fill(imagBuffer, 0f);
        for (int i = 0; i < n; i++) {
            int sampleL = (data[i * 4 + 1] << 8) | (data[i * 4] & 0xFF);
            int sampleR = (data[i * 4 + 3] << 8) | (data[i * 4 + 2] & 0xFF);
            realBuffer[i] = (sampleL + sampleR) / 2.0f;
            realBuffer[i] *= (float) (0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1))));
        }
        int m = 9;
        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (32 - m);
            if (j > i) {
                float t = realBuffer[i];
                realBuffer[i] = realBuffer[j];
                realBuffer[j] = t;
            }
        }
        for (int s = 1; s <= m; s++) {
            int m2 = 1 << s;
            int m1 = m2 >> 1;
            float wmR = (float) Math.cos(-2 * Math.PI / m2);
            float wmI = (float) Math.sin(-2 * Math.PI / m2);
            for (int k = 0; k < n; k += m2) {
                float wR = 1, wI = 0;
                for (int j = 0; j < m1; j++) {
                    float tR = wR * realBuffer[k + j + m1] - wI * imagBuffer[k + j + m1];
                    float tI = wR * imagBuffer[k + j + m1] + wI * realBuffer[k + j + m1];
                    float uR = realBuffer[k + j];
                    float uI = imagBuffer[k + j];
                    realBuffer[k + j] = uR + tR;
                    imagBuffer[k + j] = uI + tI;
                    realBuffer[k + j + m1] = uR - tR;
                    imagBuffer[k + j + m1] = uI - tI;
                    float nWR = wR * wmR - wI * wmI;
                    wI = wR * wmI + wI * wmR;
                    wR = nWR;
                }
            }
        }
        float currentMax = 0;
        int lastBin = 1;
        for (int i = 0; i < 32; i++) {
            int startBin = lastBin;
            int endBin = (int) (Math.pow(n / 2.0, (i + 1) / 32.0));
            if (endBin <= startBin)
                endBin = startBin + 1;
            lastBin = endBin;
            float sum = 0;
            for (int j = startBin; j < endBin && j < n / 2; j++)
                sum += (float) Math.sqrt(realBuffer[j] * realBuffer[j] + imagBuffer[j] * imagBuffer[j]);
            float avg = sum / (endBin - startBin);
            bandAverages[i] = avg;
            if (avg > currentMax)
                currentMax = avg;
        }
        // Update peak envelope once per frame rather than once per band (scaled with gain so quiet playback stays responsive)
        maxPeak = maxPeak * 0.99f + Math.max(currentMax, 500000f * gain) * 0.01f;
        for (int i = 0; i < 32; i++) {
            float val = (bandAverages[i] * (1.0f + (i * 0.15f))) / (maxPeak * 0.8f);
            if (val > amplitudes[i])
                amplitudes[i] = Math.min(1f, val);
            else
                amplitudes[i] = amplitudes[i] * 0.75f + Math.min(1f, val) * 0.25f;
        }
    }
}
