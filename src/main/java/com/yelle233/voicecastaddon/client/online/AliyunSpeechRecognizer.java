package com.yelle233.voicecastaddon.client.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Alibaba Cloud speech recognition implementation
 * API Doc: https://help.aliyun.com/document_detail/92131.html
 */
public class AliyunSpeechRecognizer implements OnlineSpeechRecognizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENDPOINT = "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/asr";

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String appKey;
    private final HttpClient httpClient;

    public AliyunSpeechRecognizer(String accessKeyId, String accessKeySecret, String appKey) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.appKey = appKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String recognize(byte[] audioData, int sampleRate) throws IOException {
        try {
            return performRecognition(audioData, sampleRate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Recognition interrupted", e);
        }
    }

    private String performRecognition(byte[] audioData, int sampleRate) throws IOException, InterruptedException {
        // Convert PCM to WAV
        byte[] wavData = AudioUtils.pcmToWav(audioData, sampleRate, 1, 16);

        // Build request parameters
        Map<String, String> params = new TreeMap<>();
        params.put("appkey", appKey);
        params.put("format", "wav");
        params.put("sample_rate", String.valueOf(sampleRate));
        params.put("enable_intermediate_result", "false");
        params.put("enable_punctuation_prediction", "false");
        params.put("enable_inverse_text_normalization", "false");

        // Build authorization header
        String authorization = buildAuthorization(params);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "?" + buildQueryString(params)))
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(wavData))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Recognition failed: HTTP " + response.statusCode() + ", body: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

        if (!json.has("status")) {
            throw new IOException("Invalid response format: " + response.body());
        }

        int status = json.get("status").getAsInt();
        if (status != 20000000) {
            String message = json.has("message") ? json.get("message").getAsString() : "Unknown error";
            throw new IOException("Aliyun ASR error " + status + ": " + message);
        }

        if (json.has("result")) {
            return json.get("result").getAsString();
        }

        return "";
    }

    private String buildAuthorization(Map<String, String> params) throws IOException {
        try {
            // Build string to sign
            String method = "POST";
            String accept = "application/json";
            String contentType = "application/octet-stream";
            String date = DateTimeFormatter.RFC_1123_DATE_TIME
                    .withZone(ZoneId.of("GMT"))
                    .format(ZonedDateTime.now());

            String stringToSign = method + "\n" +
                    accept + "\n" +
                    "" + "\n" +  // Content-MD5 (empty)
                    contentType + "\n" +
                    date + "\n" +
                    "/stream/v1/asr?" + buildQueryString(params);

            // Calculate signature
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec spec = new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(spec);
            byte[] signature = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String signatureBase64 = Base64.getEncoder().encodeToString(signature);

            return "Dataplus " + accessKeyId + ":" + signatureBase64;
        } catch (Exception e) {
            throw new IOException("Failed to build authorization", e);
        }
    }

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    @Override
    public String getProviderName() {
        return "Aliyun";
    }
}
