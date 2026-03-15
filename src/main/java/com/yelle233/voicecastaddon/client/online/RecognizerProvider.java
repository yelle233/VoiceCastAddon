package com.yelle233.voicecastaddon.client.online;

/**
 * Enum of supported online speech recognition providers
 */
public enum RecognizerProvider {
    TENCENT("tencent", "Tencent Cloud"),
    BAIDU("baidu", "Baidu AI"),
    ALIYUN("aliyun", "Alibaba Cloud"),
    XFYUN("xfyun", "iFlytek");

    private final String id;
    private final String displayName;

    RecognizerProvider(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static RecognizerProvider fromId(String id) {
        if (id == null || id.isEmpty()) {
            return TENCENT; // Default
        }
        for (RecognizerProvider provider : values()) {
            if (provider.id.equalsIgnoreCase(id)) {
                return provider;
            }
        }
        return TENCENT;
    }
}
