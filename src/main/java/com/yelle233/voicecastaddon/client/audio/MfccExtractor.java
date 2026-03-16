package com.yelle233.voicecastaddon.client.audio;

/**
 * MFCC (Mel-Frequency Cepstral Coefficients) feature extractor.
 * Pure Java implementation without external dependencies.
 */
public class MfccExtractor {
    private static final int DEFAULT_SAMPLE_RATE = 16000;
    private static final int DEFAULT_FRAME_SIZE = 512;
    private static final int DEFAULT_FRAME_SHIFT = 160;
    private static final int DEFAULT_NUM_FILTERS = 26;
    private static final int DEFAULT_NUM_COEFFS = 13;
    private static final int DEFAULT_FFT_SIZE = 512;

    private final int sampleRate;
    private final int frameSize;
    private final int frameShift;
    private final int numFilters;
    private final int numCoeffs;
    private final int fftSize;
    private final double[][] melFilterBank;

    public MfccExtractor() {
        this(DEFAULT_SAMPLE_RATE, DEFAULT_FRAME_SIZE, DEFAULT_FRAME_SHIFT,
             DEFAULT_NUM_FILTERS, DEFAULT_NUM_COEFFS, DEFAULT_FFT_SIZE);
    }

    public MfccExtractor(int sampleRate, int frameSize, int frameShift,
                         int numFilters, int numCoeffs, int fftSize) {
        this.sampleRate = sampleRate;
        this.frameSize = frameSize;
        this.frameShift = frameShift;
        this.numFilters = numFilters;
        this.numCoeffs = numCoeffs;
        this.fftSize = fftSize;
        this.melFilterBank = createMelFilterBank();
    }

    /**
     * Extract MFCC features from PCM audio bytes.
     * @param audioBytes PCM audio data (16-bit signed, mono)
     * @return MFCC feature matrix [numFrames][numCoeffs]
     */
    public double[][] extractFeatures(byte[] audioBytes) {
        double[] samples = bytesToSamples(audioBytes);
        return extractFeatures(samples);
    }

    /**
     * Extract MFCC features from audio samples.
     * @param samples Audio samples normalized to [-1, 1]
     * @return MFCC feature matrix [numFrames][numCoeffs]
     */
    public double[][] extractFeatures(double[] samples) {
        int numFrames = (samples.length - frameSize) / frameShift + 1;
        if (numFrames <= 0) {
            return new double[0][numCoeffs];
        }

        double[][] mfccs = new double[numFrames][numCoeffs];
        double[] frame = new double[frameSize];
        double[] hammingWindow = createHammingWindow(frameSize);

        for (int i = 0; i < numFrames; i++) {
            int offset = i * frameShift;

            // Extract frame and apply window
            for (int j = 0; j < frameSize; j++) {
                frame[j] = samples[offset + j] * hammingWindow[j];
            }

            // Compute power spectrum
            double[] powerSpectrum = computePowerSpectrum(frame);

            // Apply mel filter bank
            double[] melSpectrum = applyMelFilterBank(powerSpectrum);

            // Compute DCT to get MFCCs
            mfccs[i] = computeDCT(melSpectrum);
        }

        return mfccs;
    }

    private double[] bytesToSamples(byte[] audioBytes) {
        int numSamples = audioBytes.length / 2;
        double[] samples = new double[numSamples];

        for (int i = 0; i < numSamples; i++) {
            int low = audioBytes[i * 2] & 0xFF;
            int high = audioBytes[i * 2 + 1];
            int sample = (high << 8) | low;
            samples[i] = sample / 32768.0;
        }

        return samples;
    }

    private double[] createHammingWindow(int size) {
        double[] window = new double[size];
        for (int i = 0; i < size; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (size - 1));
        }
        return window;
    }

    private double[] computePowerSpectrum(double[] frame) {
        double[] fftResult = fft(frame, fftSize);
        int halfSize = fftSize / 2 + 1;
        double[] powerSpectrum = new double[halfSize];

        for (int i = 0; i < halfSize; i++) {
            double real = fftResult[i * 2];
            double imag = fftResult[i * 2 + 1];
            powerSpectrum[i] = real * real + imag * imag;
        }

        return powerSpectrum;
    }

    private double[] fft(double[] input, int n) {
        double[] padded = new double[n];
        System.arraycopy(input, 0, padded, 0, Math.min(input.length, n));

        double[] result = new double[n * 2];
        for (int i = 0; i < n; i++) {
            result[i * 2] = padded[i];
            result[i * 2 + 1] = 0;
        }

        fftInPlace(result, n);
        return result;
    }

    private void fftInPlace(double[] data, int n) {
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                swap(data, i * 2, j * 2);
                swap(data, i * 2 + 1, j * 2 + 1);
            }
            int m = n / 2;
            while (m >= 1 && j >= m) {
                j -= m;
                m /= 2;
            }
            j += m;
        }

        for (int len = 2; len <= n; len *= 2) {
            double angle = -2 * Math.PI / len;
            double wlenReal = Math.cos(angle);
            double wlenImag = Math.sin(angle);

            for (int i = 0; i < n; i += len) {
                double wReal = 1;
                double wImag = 0;

                for (int k = 0; k < len / 2; k++) {
                    int evenIdx = (i + k) * 2;
                    int oddIdx = (i + k + len / 2) * 2;

                    double evenReal = data[evenIdx];
                    double evenImag = data[evenIdx + 1];
                    double oddReal = data[oddIdx];
                    double oddImag = data[oddIdx + 1];

                    double tReal = wReal * oddReal - wImag * oddImag;
                    double tImag = wReal * oddImag + wImag * oddReal;

                    data[evenIdx] = evenReal + tReal;
                    data[evenIdx + 1] = evenImag + tImag;
                    data[oddIdx] = evenReal - tReal;
                    data[oddIdx + 1] = evenImag - tImag;

                    double nextWReal = wReal * wlenReal - wImag * wlenImag;
                    double nextWImag = wReal * wlenImag + wImag * wlenReal;
                    wReal = nextWReal;
                    wImag = nextWImag;
                }
            }
        }
    }

    private void swap(double[] array, int i, int j) {
        double temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    private double[][] createMelFilterBank() {
        double lowFreqMel = hzToMel(0);
        double highFreqMel = hzToMel(sampleRate / 2.0);
        double[] melPoints = new double[numFilters + 2];

        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = lowFreqMel + (highFreqMel - lowFreqMel) * i / (numFilters + 1);
        }

        int[] bins = new int[numFilters + 2];
        for (int i = 0; i < bins.length; i++) {
            bins[i] = (int) Math.floor((fftSize + 1) * melToHz(melPoints[i]) / sampleRate);
        }

        double[][] filterBank = new double[numFilters][fftSize / 2 + 1];

        for (int i = 0; i < numFilters; i++) {
            int left = bins[i];
            int center = bins[i + 1];
            int right = bins[i + 2];

            for (int j = left; j < center; j++) {
                filterBank[i][j] = (double) (j - left) / (center - left);
            }
            for (int j = center; j < right; j++) {
                filterBank[i][j] = (double) (right - j) / (right - center);
            }
        }

        return filterBank;
    }

    private double[] applyMelFilterBank(double[] powerSpectrum) {
        double[] melSpectrum = new double[numFilters];

        for (int i = 0; i < numFilters; i++) {
            double sum = 0;
            for (int j = 0; j < powerSpectrum.length; j++) {
                sum += powerSpectrum[j] * melFilterBank[i][j];
            }
            melSpectrum[i] = Math.log(Math.max(sum, 1e-10));
        }

        return melSpectrum;
    }

    private double[] computeDCT(double[] input) {
        double[] output = new double[numCoeffs];

        for (int i = 0; i < numCoeffs; i++) {
            double sum = 0;
            for (int j = 0; j < input.length; j++) {
                sum += input[j] * Math.cos(Math.PI * i * (j + 0.5) / input.length);
            }
            output[i] = sum;
        }

        return output;
    }

    private double hzToMel(double hz) {
        return 2595 * Math.log10(1 + hz / 700.0);
    }

    private double melToHz(double mel) {
        return 700 * (Math.pow(10, mel / 2595.0) - 1);
    }
}
