package com.yelle233.voicecastaddon.client.online;

/**
 * Audio format conversion utilities
 */
public class AudioUtils {

    /**
     * Convert PCM audio data to WAV format
     * @param pcmData Raw PCM data
     * @param sampleRate Sample rate in Hz
     * @param channels Number of channels (1 for mono, 2 for stereo)
     * @param bitsPerSample Bits per sample (typically 16)
     * @return WAV format audio data with header
     */
    public static byte[] pcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        byte[] wavHeader = new byte[44];
        int dataSize = pcmData.length;
        int fileSize = dataSize + 36;

        // RIFF header
        wavHeader[0] = 'R'; wavHeader[1] = 'I'; wavHeader[2] = 'F'; wavHeader[3] = 'F';
        writeInt(wavHeader, 4, fileSize);
        wavHeader[8] = 'W'; wavHeader[9] = 'A'; wavHeader[10] = 'V'; wavHeader[11] = 'E';

        // fmt chunk
        wavHeader[12] = 'f'; wavHeader[13] = 'm'; wavHeader[14] = 't'; wavHeader[15] = ' ';
        writeInt(wavHeader, 16, 16); // fmt chunk size
        writeShort(wavHeader, 20, (short) 1); // audio format (PCM)
        writeShort(wavHeader, 22, (short) channels);
        writeInt(wavHeader, 24, sampleRate);
        writeInt(wavHeader, 28, byteRate);
        writeShort(wavHeader, 32, (short) blockAlign);
        writeShort(wavHeader, 34, (short) bitsPerSample);

        // data chunk
        wavHeader[36] = 'd'; wavHeader[37] = 'a'; wavHeader[38] = 't'; wavHeader[39] = 'a';
        writeInt(wavHeader, 40, dataSize);

        // Combine header and data
        byte[] wavData = new byte[44 + dataSize];
        System.arraycopy(wavHeader, 0, wavData, 0, 44);
        System.arraycopy(pcmData, 0, wavData, 44, dataSize);

        return wavData;
    }

    private static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
        buffer[offset + 2] = (byte) ((value >> 16) & 0xFF);
        buffer[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeShort(byte[] buffer, int offset, short value) {
        buffer[offset] = (byte) (value & 0xFF);
        buffer[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private AudioUtils() {
    }
}
