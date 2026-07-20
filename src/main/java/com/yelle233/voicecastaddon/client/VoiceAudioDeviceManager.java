package com.yelle233.voicecastaddon.client;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VoiceAudioDeviceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AudioFormat VOICE_FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);
    private static final AudioInputDevice DEFAULT_DEVICE =
            new AudioInputDevice("", "\u7cfb\u7edf\u9ed8\u8ba4\u8f93\u5165\u8bbe\u5907");

    private VoiceAudioDeviceManager() {
    }

    public static List<AudioInputDevice> listInputDevices() {
        List<AudioInputDevice> devices = new ArrayList<>();
        devices.add(DEFAULT_DEVICE);

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (!supportsVoiceFormat(mixer)) {
                continue;
            }

            devices.add(new AudioInputDevice(toId(info), toDisplayName(info)));
        }

        devices.sort(Comparator.comparing(AudioInputDevice::id));
        devices.removeIf(device -> device.id().isEmpty());
        devices.add(0, DEFAULT_DEVICE);
        return devices;
    }

    public static Mixer getPreferredMixer() {
        String preferredId = VoiceCastClientConfig.getPreferredInputDeviceId();
        if (preferredId == null || preferredId.isBlank()) {
            return null;
        }

        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (!preferredId.equals(toId(info))) {
                continue;
            }

            Mixer mixer = AudioSystem.getMixer(info);
            if (supportsVoiceFormat(mixer)) {
                return mixer;
            }
        }

        LOGGER.warn("[VoiceCastAddon] Preferred input device {} is not available, falling back to system default", preferredId);
        return null;
    }

    public static boolean supportsVoiceFormat(Mixer mixer) {
        try {
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, VOICE_FORMAT);
            return mixer.isLineSupported(lineInfo);
        } catch (Exception e) {
            return false;
        }
    }

    private static String toId(Mixer.Info info) {
        return info.getName() + "|" + info.getVendor() + "|" + info.getVersion() + "|" + info.getDescription();
    }

    private static String toDisplayName(Mixer.Info info) {
        return info.getName() + " - " + info.getDescription();
    }

    public record AudioInputDevice(String id, String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
