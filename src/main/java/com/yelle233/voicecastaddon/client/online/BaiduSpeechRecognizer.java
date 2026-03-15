package com.yelle233.voicecastaddon.client.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Baidu AI speech recognition implementation
 * API Doc: https://ai.baidu.com/ai-doc/SPEECH/Vk38lxily
 */
public class BaiduSpeechRecognizer implements OnlineSpeechRecognizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String ASR_URL = "https://vop.baidu.com/server_api";

    private final String apiKey;
    private final String secretKey;
    private final HttpClient httpClient;
    private String accessToken;
    private long tokenExpireTime;

    public BaiduSpeechRecognizer(String apiKey, String secretKey) {
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String recognize(byte[] audioData, int sampleRate) throws IOException {
        try {
            ensureAccessToken();
            return performRecognition(audioData, sampleRate);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Recognition interrupted", e);
        }
    }

    private void ensureAccessToken() throws IOException, InterruptedException {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return;
        }

        String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Failed to get access token: HTTP " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.has("access_token")) {
            throw new IOException("No access_token in response: " + response.body());
        }

        accessToken = json.get("access_token").getAsString();
        int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 2592000;
        tokenExpireTime = System.currentTimeMillis() + (expiresIn - 60) * 1000L;
        LOGGER.info("[VoiceCastAddon] Baidu access token obtained");
    }

    private String performRecognition(byte[] audioData, int sampleRate) throws IOException, InterruptedException {
        // Convert PCM to WAV
        byte[] wavData = AudioUtils.pcmToWav(audioData, sampleRate, 1, 16);
        String base64Audio = Base64.getEncoder().encodeToString(wavData);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("format", "wav");
        requestBody.addProperty("rate", sampleRate);
        requestBody.addProperty("channel", 1);
        requestBody.addProperty("cuid", "voicecastaddon");
        requestBody.addProperty("token", accessToken);
        requestBody.addProperty("speech", base64Audio);
        requestBody.addProperty("len", wavData.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ASR_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Recognition failed: HTTP " + response.statusCode() + ", body: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

        if (!json.has("err_no")) {
            throw new IOException("Invalid response format: " + response.body());
        }

        int errNo = json.get("err_no").getAsInt();
        if (errNo != 0) {
            String errMsg = json.has("err_msg") ? json.get("err_msg").getAsString() : "Unknown error";
            throw new IOException("Baidu ASR error " + errNo + ": " + errMsg);
        }

        if (json.has("result") && json.get("result").isJsonArray()) {
            var resultArray = json.getAsJsonArray("result");
            if (resultArray.size() > 0) {
                return resultArray.get(0).getAsString();
            }
        }

        return "";
    }

    @Override
    public String getProviderName() {
        return "Baidu";
    }
}
