package com.yelle233.voicecastaddon.client.online;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.TreeMap;

public class TencentSpeechRecognizer implements OnlineSpeechRecognizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENDPOINT = "asr.tencentcloudapi.com";
    private static final String SERVICE = "asr";
    private static final String VERSION = "2019-06-14";
    private static final String ACTION = "SentenceRecognition";
    private static final String REGION = "ap-guangzhou";

    private final String secretId;
    private final String secretKey;
    private final HttpClient httpClient;

    public TencentSpeechRecognizer(String secretId, String secretKey) {
        this.secretId = secretId;
        this.secretKey = secretKey;
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
        // Convert PCM to WAV format
        byte[] wavData = AudioUtils.pcmToWav(audioData, sampleRate, 1, 16);
        String base64Audio = Base64.getEncoder().encodeToString(wavData);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("EngSerViceType", "16k_zh");
        requestBody.addProperty("SourceType", 1);
        requestBody.addProperty("VoiceFormat", "wav");
        requestBody.addProperty("Data", base64Audio);
        requestBody.addProperty("DataLen", wavData.length);

        String payload = requestBody.toString();
        long timestamp = Instant.now().getEpochSecond();
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.of("UTC"))
                .format(Instant.ofEpochSecond(timestamp));

        try {
            String authorization = buildAuthorization(payload, timestamp, date);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + ENDPOINT))
                    .header("Content-Type", "application/json")
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("X-TC-Region", REGION)
                    .header("Authorization", authorization)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Recognition failed: HTTP " + response.statusCode() + ", body: " + response.body());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

            if (json.has("Response")) {
                JsonObject responseObj = json.getAsJsonObject("Response");

                if (responseObj.has("Error")) {
                    JsonObject error = responseObj.getAsJsonObject("Error");
                    String errorCode = error.has("Code") ? error.get("Code").getAsString() : "Unknown";
                    String errorMsg = error.has("Message") ? error.get("Message").getAsString() : "Unknown error";
                    throw new IOException("Tencent ASR error " + errorCode + ": " + errorMsg);
                }

                if (responseObj.has("Result")) {
                    return responseObj.get("Result").getAsString();
                }
            }

            return "";
        } catch (Exception e) {
            throw new IOException("Failed to perform recognition", e);
        }
    }

    private String buildAuthorization(String payload, long timestamp, String date) throws Exception {
        // Step 1: Build canonical request
        String hashedPayload = sha256Hex(payload);
        // Note: Host header is automatically added by HttpClient, but we need it in signature
        String canonicalRequest = "POST\n/\n\ncontent-type:application/json\nhost:" + ENDPOINT +
                "\n\ncontent-type;host\n" + hashedPayload;

        // Step 2: Build string to sign
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n" + credentialScope + "\n" + hashedCanonicalRequest;

        // Step 3: Calculate signature
        byte[] secretDate = hmacSHA256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSHA256(secretDate, SERVICE);
        byte[] secretSigning = hmacSHA256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSHA256(secretSigning, stringToSign));

        // Step 4: Build authorization
        return "TC3-HMAC-SHA256 Credential=" + secretId + "/" + credentialScope +
                ", SignedHeaders=content-type;host, Signature=" + signature;
    }

    private static String sha256Hex(String s) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(s.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static byte[] hmacSHA256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
        mac.init(secretKeySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    @Override
    public String getProviderName() {
        return "Tencent";
    }
}
