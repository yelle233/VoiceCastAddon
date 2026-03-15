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
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * iFlytek (Xunfei) speech recognition implementation
 * API Doc: https://www.xfyun.cn/doc/asr/ifasr/API.html
 */
public class XfyunSpeechRecognizer implements OnlineSpeechRecognizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HOST = "iat-api.xfyun.cn";
    private static final String PATH = "/v2/iat";

    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;

    public XfyunSpeechRecognizer(String appId, String apiKey, String apiSecret) {
        this.appId = appId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
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
        String base64Audio = Base64.getEncoder().encodeToString(wavData);

        // Build authorization URL
        String authUrl = buildAuthUrl();

        // Build request body
        JsonObject business = new JsonObject();
        business.addProperty("language", "zh_cn");
        business.addProperty("domain", "iat");
        business.addProperty("accent", "mandarin");
        business.addProperty("vad_eos", 2000);

        JsonObject data = new JsonObject();
        data.addProperty("status", 2);
        data.addProperty("format", "audio/L16;rate=" + sampleRate);
        data.addProperty("encoding", "raw");
        data.addProperty("audio", base64Audio);

        JsonObject requestBody = new JsonObject();
        requestBody.add("business", business);
        requestBody.add("data", data);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Recognition failed: HTTP " + response.statusCode() + ", body: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

        if (!json.has("code")) {
            throw new IOException("Invalid response format: " + response.body());
        }

        int code = json.get("code").getAsInt();
        if (code != 0) {
            String message = json.has("message") ? json.get("message").getAsString() : "Unknown error";
            throw new IOException("iFlytek ASR error " + code + ": " + message);
        }

        if (json.has("data") && json.getAsJsonObject("data").has("result")) {
            var resultArray = json.getAsJsonObject("data").getAsJsonArray("result");
            StringBuilder text = new StringBuilder();
            for (var item : resultArray) {
                if (item.isJsonObject()) {
                    var ws = item.getAsJsonObject().getAsJsonArray("ws");
                    for (var w : ws) {
                        if (w.isJsonObject()) {
                            var cw = w.getAsJsonObject().getAsJsonArray("cw");
                            for (var c : cw) {
                                if (c.isJsonObject() && c.getAsJsonObject().has("w")) {
                                    text.append(c.getAsJsonObject().get("w").getAsString());
                                }
                            }
                        }
                    }
                }
            }
            return text.toString();
        }

        return "";
    }

    private String buildAuthUrl() throws IOException {
        try {
            // Build date header
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
            String date = ZonedDateTime.now(ZoneId.of("GMT")).format(formatter);

            // Build signature origin
            String signatureOrigin = "host: " + HOST + "\n" +
                    "date: " + date + "\n" +
                    "GET " + PATH + " HTTP/1.1";

            // Calculate signature
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(spec);
            byte[] signature = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
            String signatureBase64 = Base64.getEncoder().encodeToString(signature);

            // Build authorization
            String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"",
                    apiKey, "hmac-sha256", "host date request-line", signatureBase64);
            String authorizationBase64 = Base64.getEncoder().encodeToString(authorization.getBytes(StandardCharsets.UTF_8));

            // Build URL
            return String.format("https://%s%s?authorization=%s&date=%s&host=%s",
                    HOST, PATH,
                    URLEncoder.encode(authorizationBase64, StandardCharsets.UTF_8),
                    URLEncoder.encode(date, StandardCharsets.UTF_8),
                    URLEncoder.encode(HOST, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("Failed to build auth URL", e);
        }
    }

    @Override
    public String getProviderName() {
        return "iFlytek";
    }
}
