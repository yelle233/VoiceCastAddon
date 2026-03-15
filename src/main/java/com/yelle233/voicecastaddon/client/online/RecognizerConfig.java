package com.yelle233.voicecastaddon.client.online;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for an online speech recognizer
 */
public class RecognizerConfig {
    private final RecognizerProvider provider;
    private final Map<String, String> credentials;
    private final Map<String, String> options;

    private RecognizerConfig(RecognizerProvider provider, Map<String, String> credentials, Map<String, String> options) {
        this.provider = provider;
        this.credentials = new HashMap<>(credentials);
        this.options = new HashMap<>(options);
    }

    public RecognizerProvider getProvider() {
        return provider;
    }

    public String getCredential(String key) {
        return credentials.getOrDefault(key, "");
    }

    public String getOption(String key, String defaultValue) {
        return options.getOrDefault(key, defaultValue);
    }

    public static Builder builder(RecognizerProvider provider) {
        return new Builder(provider);
    }

    public static class Builder {
        private final RecognizerProvider provider;
        private final Map<String, String> credentials = new HashMap<>();
        private final Map<String, String> options = new HashMap<>();

        private Builder(RecognizerProvider provider) {
            this.provider = provider;
        }

        public Builder credential(String key, String value) {
            credentials.put(key, value);
            return this;
        }

        public Builder option(String key, String value) {
            options.put(key, value);
            return this;
        }

        public RecognizerConfig build() {
            return new RecognizerConfig(provider, credentials, options);
        }
    }
}
