package com.yelle233.voicecastaddon.client.online;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * Factory for creating online speech recognizers
 */
public class RecognizerFactory {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static OnlineSpeechRecognizer create(RecognizerConfig config) throws IOException {
        if (config == null) {
            throw new IOException("Recognizer config is null");
        }

        switch (config.getProvider()) {
            case TENCENT:
                return createTencentRecognizer(config);
            case BAIDU:
                return createBaiduRecognizer(config);
            case ALIYUN:
                return createAliyunRecognizer(config);
            case XFYUN:
                return createXfyunRecognizer(config);
            default:
                throw new IOException("Unsupported provider: " + config.getProvider());
        }
    }

    private static OnlineSpeechRecognizer createTencentRecognizer(RecognizerConfig config) throws IOException {
        String secretId = config.getCredential("secretId");
        String secretKey = config.getCredential("secretKey");

        if (secretId.isEmpty() || secretKey.isEmpty()) {
            throw new IOException("Tencent credentials not configured");
        }

        LOGGER.info("[VoiceCastAddon] Creating Tencent recognizer");
        return new TencentSpeechRecognizer(secretId, secretKey);
    }

    private static OnlineSpeechRecognizer createBaiduRecognizer(RecognizerConfig config) throws IOException {
        String apiKey = config.getCredential("apiKey");
        String secretKey = config.getCredential("secretKey");

        if (apiKey.isEmpty() || secretKey.isEmpty()) {
            throw new IOException("Baidu credentials not configured");
        }

        LOGGER.info("[VoiceCastAddon] Creating Baidu recognizer");
        return new BaiduSpeechRecognizer(apiKey, secretKey);
    }

    private static OnlineSpeechRecognizer createAliyunRecognizer(RecognizerConfig config) throws IOException {
        String accessKeyId = config.getCredential("accessKeyId");
        String accessKeySecret = config.getCredential("accessKeySecret");
        String appKey = config.getCredential("appKey");

        if (accessKeyId.isEmpty() || accessKeySecret.isEmpty() || appKey.isEmpty()) {
            throw new IOException("Aliyun credentials not configured");
        }

        LOGGER.info("[VoiceCastAddon] Creating Aliyun recognizer");
        return new AliyunSpeechRecognizer(accessKeyId, accessKeySecret, appKey);
    }

    private static OnlineSpeechRecognizer createXfyunRecognizer(RecognizerConfig config) throws IOException {
        String appId = config.getCredential("appId");
        String apiKey = config.getCredential("apiKey");
        String apiSecret = config.getCredential("apiSecret");

        if (appId.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty()) {
            throw new IOException("iFlytek credentials not configured");
        }

        LOGGER.info("[VoiceCastAddon] Creating iFlytek recognizer");
        return new XfyunSpeechRecognizer(appId, apiKey, apiSecret);
    }

    private RecognizerFactory() {
    }
}
