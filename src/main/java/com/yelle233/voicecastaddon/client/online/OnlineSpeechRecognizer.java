package com.yelle233.voicecastaddon.client.online;

import java.io.IOException;

public interface OnlineSpeechRecognizer {
    /**
     * Recognize speech from audio data
     * @param audioData PCM audio data
     * @param sampleRate Sample rate in Hz
     * @return Recognized text
     * @throws IOException If recognition fails
     */
    String recognize(byte[] audioData, int sampleRate) throws IOException;

    /**
     * Get the name of the recognition provider
     * @return Provider name (e.g., "Baidu", "Aliyun")
     */
    String getProviderName();
}
