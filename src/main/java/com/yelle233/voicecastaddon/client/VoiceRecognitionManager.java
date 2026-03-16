package com.yelle233.voicecastaddon.client;

import com.mojang.logging.LogUtils;
import com.yelle233.voicecastaddon.client.audio.VoiceTemplateManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Voice recognition manager using audio template matching.
 * Records audio and matches against pre-recorded spell templates.
 */
public class VoiceRecognitionManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float SAMPLE_RATE = 16000.0f;

    private static TargetDataLine line;
    private static ByteArrayOutputStream audioBuffer;
    private static Thread captureThread;
    private static volatile boolean listening = false;
    private static volatile String lastError = "";
    private static volatile boolean initialized = false;

    public static boolean startListening() {
        if (listening) {
            return true;
        }

        try {
            ensureInitialized();

            AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            Mixer preferredMixer = VoiceAudioDeviceManager.getPreferredMixer();
            line = openTargetLine(preferredMixer, info, format);
            line.start();

            audioBuffer = new ByteArrayOutputStream();
            listening = true;

            captureThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (listening) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        audioBuffer.write(buffer, 0, count);
                    }
                }
            }, "voicecastaddon-capture");

            captureThread.setDaemon(true);
            captureThread.start();
            lastError = "";
            LOGGER.info("[VoiceCastAddon] Voice capture started");
            return true;
        } catch (Throwable t) {
            listening = false;
            closeLine();
            lastError = t.getMessage();
            LOGGER.error("[VoiceCastAddon] Failed to start voice recognition", t);
            return false;
        }
    }

    public static ResourceLocation stopListeningAndMatch() {
        if (!listening) {
            return null;
        }

        listening = false;
        closeLine();

        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] audioBytes = audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
        if (audioBytes.length == 0) {
            return null;
        }

        double threshold = VoiceCastClientConfig.getMatchThreshold();
        return VoiceTemplateManager.matchAudio(audioBytes, threshold);
    }

    public static byte[] stopListeningAndGetAudio() {
        if (!listening) {
            return new byte[0];
        }

        listening = false;
        closeLine();

        if (captureThread != null) {
            try {
                captureThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
    }

    public static boolean isListening() {
        return listening;
    }

    public static String getLastError() {
        return lastError;
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        synchronized (VoiceRecognitionManager.class) {
            if (initialized) {
                return;
            }

            LOGGER.info("[VoiceCastAddon] Loading voice templates...");
            VoiceTemplateManager.loadAllTemplates();
            initialized = true;
        }
    }

    private static void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private static void closeLine() {
        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    private static TargetDataLine openTargetLine(Mixer preferredMixer, DataLine.Info info, AudioFormat format)
            throws LineUnavailableException {
        if (preferredMixer != null) {
            try {
                TargetDataLine preferredLine = (TargetDataLine) preferredMixer.getLine(info);
                preferredLine.open(format);
                return preferredLine;
            } catch (IllegalArgumentException | LineUnavailableException e) {
                LOGGER.warn("[VoiceCastAddon] Preferred input device failed, falling back to system default");
            }
        }

        TargetDataLine defaultLine = (TargetDataLine) AudioSystem.getLine(info);
        defaultLine.open(format);
        return defaultLine;
    }

    private VoiceRecognitionManager() {
    }
}
