package com.yelle233.voicecastaddon.client.audio;

/**
 * Dynamic Time Warping (DTW) algorithm for comparing audio feature sequences.
 * Returns distance between two MFCC feature matrices - lower is more similar.
 */
public class DtwMatcher {

    /**
     * Calculate DTW distance between two MFCC feature matrices.
     * @param features1 First feature matrix [frames][coeffs]
     * @param features2 Second feature matrix [frames][coeffs]
     * @return DTW distance (lower = more similar)
     */
    public static double calculateDistance(double[][] features1, double[][] features2) {
        if (features1.length == 0 || features2.length == 0) {
            return Double.MAX_VALUE;
        }

        int n = features1.length;
        int m = features2.length;

        double[][] dtw = new double[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                dtw[i][j] = Double.MAX_VALUE;
            }
        }
        dtw[0][0] = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double cost = euclideanDistance(features1[i - 1], features2[j - 1]);
                dtw[i][j] = cost + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
            }
        }

        return dtw[n][m] / (n + m);
    }

    private static double euclideanDistance(double[] vec1, double[] vec2) {
        double sum = 0;
        int len = Math.min(vec1.length, vec2.length);
        for (int i = 0; i < len; i++) {
            double diff = vec1[i] - vec2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
