package com.yelle233.voicecastaddon.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VoiceCastClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String ALIAS_FILE_NAME = "spell_aliases.json";
    private static final String SETTINGS_FILE_NAME = "client_settings.json";
    private static final String CUSTOM_MODELS_DIR_NAME = "custom-voice-models";
    private static final String INPUT_DEVICE_KEY = "preferredInputDeviceId";
    private static final String RECOGNITION_MODE_KEY = "recognitionMode";
    private static final String ONLINE_PROVIDER_KEY = "onlineProvider";
    private static final String TENCENT_SECRET_ID = "tencentSecretId";
    private static final String TENCENT_SECRET_KEY = "tencentSecretKey";
    private static final String BAIDU_API_KEY = "baiduApiKey";
    private static final String BAIDU_SECRET_KEY = "baiduSecretKey";
    private static final String ALIYUN_ACCESS_KEY_ID = "aliyunAccessKeyId";
    private static final String ALIYUN_ACCESS_KEY_SECRET = "aliyunAccessKeySecret";
    private static final String ALIYUN_APP_KEY = "aliyunAppKey";
    private static final String XFYUN_APP_ID = "xfyunAppId";
    private static final String XFYUN_API_KEY = "xfyunApiKey";
    private static final String XFYUN_API_SECRET = "xfyunApiSecret";
    private static final String LONG_SENTENCE_THRESHOLD_KEY = "longSentenceThreshold";

    private static final String CONFIG_VERSION_KEY = "_config_version";
    private static final int CURRENT_CONFIG_VERSION = 1;
    private static final String SETTINGS_VERSION_KEY = "_settings_version";
    private static final int CURRENT_SETTINGS_VERSION = 1; // Increment when settings schema changes

    private VoiceCastClientConfig() {
    }

    public static void ensureClientFiles() {
        try {
            Files.createDirectories(getConfigDir());
            Files.createDirectories(getCustomModelsDir());

            if (Files.notExists(getAliasFile())) {
                writeDefaultAliasFile();
            } else {
                mergeAliasFileIfNeeded();
            }

            if (Files.notExists(getSettingsFile())) {
                writeDefaultSettingsFile();
            } else {
                mergeSettingsFileIfNeeded();
            }
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to initialize client config files", e);
        }
    }

    public static Map<String, List<String>> loadSpellAliases() {
        ensureClientFiles();

        Map<String, List<String>> aliases = new LinkedHashMap<>();
        try {
            String json = Files.readString(getAliasFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue;
                }

                if (!entry.getValue().isJsonArray()) {
                    continue;
                }

                List<String> values = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(element -> {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        values.add(element.getAsString());
                    }
                });

                if (!values.isEmpty()) {
                    aliases.put(entry.getKey(), values);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load spell alias config, using defaults", e);
            aliases.putAll(defaultAliases());
        }

        return aliases;
    }

    public static List<Path> findCustomModelDirectories() {
        ensureClientFiles();

        List<Path> directories = new ArrayList<>();
        try (var stream = Files.list(getCustomModelsDir())) {
            stream.filter(Files::isDirectory)
                    .filter(VoiceCastClientConfig::looksLikeVoskModel)
                    .forEach(directories::add);
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to scan custom voice models directory", e);
        }
        return directories;
    }

    public static Path getCustomModelsDir() {
        return getConfigDir().resolve(CUSTOM_MODELS_DIR_NAME);
    }

    public static String getPreferredInputDeviceId() {
        ensureClientFiles();

        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has(INPUT_DEVICE_KEY)) {
                return "";
            }

            String value = root.get(INPUT_DEVICE_KEY).getAsString();
            return value == null ? "" : value;
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load client settings, using system default input device", e);
            return "";
        }
    }

    public static void setPreferredInputDeviceId(String deviceId) {
        ensureClientFiles();

        JsonObject root = readSettingsObject();
        root.addProperty(INPUT_DEVICE_KEY, deviceId == null ? "" : deviceId);

        try {
            Files.writeString(getSettingsFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to save client settings", e);
        }
    }

    public static Path getConfigDirPath() {
        return getConfigDir();
    }

    public static void saveSpellAliases(Map<String, List<String>> aliases) throws IOException {
        ensureClientFiles();
        JsonObject aliasObject = createAliasJsonObject(aliases, CURRENT_CONFIG_VERSION);
        Files.writeString(getAliasFile(), GSON.toJson(aliasObject), StandardCharsets.UTF_8);
        LOGGER.info("[VoiceCastAddon] Saved spell aliases config with {} spells", aliases.size());
    }

    private static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve("voicecastaddon");
    }

    private static Path getAliasFile() {
        return getConfigDir().resolve(ALIAS_FILE_NAME);
    }

    private static Path getSettingsFile() {
        return getConfigDir().resolve(SETTINGS_FILE_NAME);
    }

    private static boolean looksLikeVoskModel(Path dir) {
        return Files.exists(dir.resolve("am").resolve("final.mdl"))
                && Files.exists(dir.resolve("conf").resolve("model.conf"));
    }

    private static void writeDefaultAliasFile() throws IOException {
        JsonObject aliasObject = createAliasJsonObject(defaultAliases(), CURRENT_CONFIG_VERSION);
        Files.writeString(getAliasFile(), GSON.toJson(aliasObject), StandardCharsets.UTF_8);
    }

    private static void mergeAliasFileIfNeeded() {
        try {
            String json = Files.readString(getAliasFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            int fileVersion = 0;
            if (root.has(CONFIG_VERSION_KEY) && root.get(CONFIG_VERSION_KEY).isJsonPrimitive()) {
                fileVersion = root.get(CONFIG_VERSION_KEY).getAsInt();
            }

            if (fileVersion >= CURRENT_CONFIG_VERSION) {
                return;
            }

            LOGGER.info("[VoiceCastAddon] Detected outdated config version {} (current: {}), merging with defaults...",
                    fileVersion, CURRENT_CONFIG_VERSION);

            Map<String, List<String>> userAliases = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue;
                }

                if (!entry.getValue().isJsonArray()) {
                    continue;
                }

                List<String> values = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(element -> {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        values.add(element.getAsString());
                    }
                });

                if (!values.isEmpty()) {
                    userAliases.put(entry.getKey(), values);
                }
            }

            Map<String, List<String>> defaultAliases = defaultAliases();
            Map<String, List<String>> mergedAliases = new LinkedHashMap<>();

            for (Map.Entry<String, List<String>> entry : defaultAliases.entrySet()) {
                if (userAliases.containsKey(entry.getKey())) {
                    mergedAliases.put(entry.getKey(), userAliases.get(entry.getKey()));
                    LOGGER.debug("[VoiceCastAddon] Keeping user config for spell: {}", entry.getKey());
                } else {
                    mergedAliases.put(entry.getKey(), entry.getValue());
                    LOGGER.info("[VoiceCastAddon] Adding new spell to config: {}", entry.getKey());
                }
            }

            for (Map.Entry<String, List<String>> entry : userAliases.entrySet()) {
                if (!mergedAliases.containsKey(entry.getKey())) {
                    mergedAliases.put(entry.getKey(), entry.getValue());
                    LOGGER.debug("[VoiceCastAddon] Keeping user custom spell: {}", entry.getKey());
                }
            }

            Path backupFile = getAliasFile().getParent().resolve(ALIAS_FILE_NAME + ".backup");
            Files.copy(getAliasFile(), backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[VoiceCastAddon] Created backup at: {}", backupFile);

            JsonObject mergedObject = createAliasJsonObject(mergedAliases, CURRENT_CONFIG_VERSION);
            Files.writeString(getAliasFile(), GSON.toJson(mergedObject), StandardCharsets.UTF_8);

            LOGGER.info("[VoiceCastAddon] Config merge completed. Updated to version {}", CURRENT_CONFIG_VERSION);

        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to merge alias config", e);
        }
    }

    private static JsonObject createAliasJsonObject(Map<String, List<String>> aliases, int version) {
        JsonObject root = new JsonObject();
        root.addProperty(CONFIG_VERSION_KEY, version);

        for (Map.Entry<String, List<String>> entry : aliases.entrySet()) {
            root.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
        }

        return root;
    }

    private static void writeDefaultSettingsFile() throws IOException {
        Files.writeString(getSettingsFile(), GSON.toJson(defaultSettings()), StandardCharsets.UTF_8);
    }

    private static void mergeSettingsFileIfNeeded() {
        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            JsonObject current = JsonParser.parseString(json).getAsJsonObject();

            // Check version
            int fileVersion = 0;
            if (current.has(SETTINGS_VERSION_KEY) && current.get(SETTINGS_VERSION_KEY).isJsonPrimitive()) {
                fileVersion = current.get(SETTINGS_VERSION_KEY).getAsInt();
            }

            if (fileVersion >= CURRENT_SETTINGS_VERSION) {
                return; // Already up to date
            }

            LOGGER.info("[VoiceCastAddon] Migrating settings from version {} to {}", fileVersion, CURRENT_SETTINGS_VERSION);

            JsonObject defaults = defaultSettings();
            JsonObject merged = new JsonObject();

            // Copy version
            merged.addProperty(SETTINGS_VERSION_KEY, CURRENT_SETTINGS_VERSION);

            // Migrate known fields from old to new
            for (String key : defaults.keySet()) {
                if (key.equals(SETTINGS_VERSION_KEY)) {
                    continue; // Already added
                }

                if (current.has(key)) {
                    // Keep user's value
                    merged.add(key, current.get(key));
                    LOGGER.debug("[VoiceCastAddon] Preserved setting: {} = {}", key, current.get(key));
                } else {
                    // Use default value
                    merged.add(key, defaults.get(key));
                    LOGGER.info("[VoiceCastAddon] Added new setting: {} = {}", key, defaults.get(key));
                }
            }

            // Log removed fields
            for (String key : current.keySet()) {
                if (!merged.has(key) && !key.startsWith("_")) {
                    LOGGER.info("[VoiceCastAddon] Removed obsolete setting: {}", key);
                }
            }

            // Create backup
            Path backupFile = getSettingsFile().getParent().resolve(SETTINGS_FILE_NAME + ".backup");
            Files.copy(getSettingsFile(), backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[VoiceCastAddon] Created settings backup at: {}", backupFile);

            // Write merged settings
            Files.writeString(getSettingsFile(), GSON.toJson(merged), StandardCharsets.UTF_8);
            LOGGER.info("[VoiceCastAddon] Settings migrated successfully to version {}", CURRENT_SETTINGS_VERSION);

        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to merge settings file", e);
        }
    }

    private static JsonObject readSettingsObject() {
        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to read client settings, recreating defaults", e);
            return defaultSettings();
        }
    }

    private static Map<String, List<String>> defaultAliases() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();

        // Example spells - 示例法术
        // Players should use "/voicecast generate" command to auto-generate all spell configurations
        // 玩家应使用 "/voicecast generate" 命令自动生成所有法术配置

        return defaults;
    }

    private static JsonObject defaultSettings() {
        JsonObject root = new JsonObject();
        root.addProperty(SETTINGS_VERSION_KEY, CURRENT_SETTINGS_VERSION);
        root.addProperty(INPUT_DEVICE_KEY, "");
        root.addProperty(RECOGNITION_MODE_KEY, "offline"); // offline, online, hybrid
        root.addProperty(ONLINE_PROVIDER_KEY, "tencent"); // tencent, baidu, aliyun, xfyun

        // Tencent Cloud credentials
        root.addProperty(TENCENT_SECRET_ID, "");
        root.addProperty(TENCENT_SECRET_KEY, "");

        // Baidu AI credentials
        root.addProperty(BAIDU_API_KEY, "");
        root.addProperty(BAIDU_SECRET_KEY, "");

        // Aliyun credentials
        root.addProperty(ALIYUN_ACCESS_KEY_ID, "");
        root.addProperty(ALIYUN_ACCESS_KEY_SECRET, "");
        root.addProperty(ALIYUN_APP_KEY, "");

        // iFlytek credentials
        root.addProperty(XFYUN_APP_ID, "");
        root.addProperty(XFYUN_API_KEY, "");
        root.addProperty(XFYUN_API_SECRET, "");

        root.addProperty(LONG_SENTENCE_THRESHOLD_KEY, 10);
        return root;
    }

    public static String getRecognitionMode() {
        return getSettingString(RECOGNITION_MODE_KEY, "offline");
    }

    public static String getOnlineProvider() {
        return getSettingString(ONLINE_PROVIDER_KEY, "tencent");
    }

    public static String getTencentSecretId() {
        return getSettingString(TENCENT_SECRET_ID, "");
    }

    public static String getTencentSecretKey() {
        return getSettingString(TENCENT_SECRET_KEY, "");
    }

    public static String getBaiduApiKey() {
        return getSettingString(BAIDU_API_KEY, "");
    }

    public static String getBaiduSecretKey() {
        return getSettingString(BAIDU_SECRET_KEY, "");
    }

    public static String getAliyunAccessKeyId() {
        return getSettingString(ALIYUN_ACCESS_KEY_ID, "");
    }

    public static String getAliyunAccessKeySecret() {
        return getSettingString(ALIYUN_ACCESS_KEY_SECRET, "");
    }

    public static String getAliyunAppKey() {
        return getSettingString(ALIYUN_APP_KEY, "");
    }

    public static String getXfyunAppId() {
        return getSettingString(XFYUN_APP_ID, "");
    }

    public static String getXfyunApiKey() {
        return getSettingString(XFYUN_API_KEY, "");
    }

    public static String getXfyunApiSecret() {
        return getSettingString(XFYUN_API_SECRET, "");
    }

    public static int getLongSentenceThreshold() {
        ensureClientFiles();
        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(LONG_SENTENCE_THRESHOLD_KEY)) {
                return root.get(LONG_SENTENCE_THRESHOLD_KEY).getAsInt();
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load long sentence threshold", e);
        }
        return 10;
    }

    private static String getSettingString(String key, String defaultValue) {
        ensureClientFiles();
        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(key)) {
                return root.get(key).getAsString();
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load setting {}", key, e);
        }
        return defaultValue;
    }
}
