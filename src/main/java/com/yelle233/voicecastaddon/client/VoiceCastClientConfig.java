package com.yelle233.voicecastaddon.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoiceCastClientConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String SETTINGS_FILE_NAME = "client_settings.json";
    private static final String INPUT_DEVICE_KEY = "preferredInputDeviceId";
    private static final String MATCH_THRESHOLD_KEY = "matchThreshold";

    private static final String SETTINGS_VERSION_KEY = "_settings_version";
    private static final int CURRENT_SETTINGS_VERSION = 2;

    private VoiceCastClientConfig() {
    }

    public static void ensureClientFiles() {
        try {
            Files.createDirectories(getConfigDir());

            if (Files.notExists(getSettingsFile())) {
                writeDefaultSettingsFile();
            } else {
                mergeSettingsFileIfNeeded();
            }
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to initialize client config files", e);
        }
    }

    public static Path getConfigDirPath() {
        return getConfigDir();
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
            LOGGER.error("[VoiceCastAddon] Failed to load client settings", e);
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

    public static double getMatchThreshold() {
        ensureClientFiles();

        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(MATCH_THRESHOLD_KEY)) {
                return root.get(MATCH_THRESHOLD_KEY).getAsDouble();
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load match threshold", e);
        }
        return 50.0;
    }

    private static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve("voicecastaddon");
    }

    private static Path getSettingsFile() {
        return getConfigDir().resolve(SETTINGS_FILE_NAME);
    }

    private static void writeDefaultSettingsFile() throws IOException {
        Files.writeString(getSettingsFile(), GSON.toJson(defaultSettings()), StandardCharsets.UTF_8);
    }

    private static void mergeSettingsFileIfNeeded() {
        try {
            String json = Files.readString(getSettingsFile(), StandardCharsets.UTF_8);
            JsonObject current = JsonParser.parseString(json).getAsJsonObject();

            int fileVersion = 0;
            if (current.has(SETTINGS_VERSION_KEY) && current.get(SETTINGS_VERSION_KEY).isJsonPrimitive()) {
                fileVersion = current.get(SETTINGS_VERSION_KEY).getAsInt();
            }

            if (fileVersion >= CURRENT_SETTINGS_VERSION) {
                return;
            }

            LOGGER.info("[VoiceCastAddon] Migrating settings from version {} to {}", fileVersion, CURRENT_SETTINGS_VERSION);

            JsonObject defaults = defaultSettings();
            JsonObject merged = new JsonObject();

            merged.addProperty(SETTINGS_VERSION_KEY, CURRENT_SETTINGS_VERSION);

            for (String key : defaults.keySet()) {
                if (key.equals(SETTINGS_VERSION_KEY)) {
                    continue;
                }

                if (current.has(key)) {
                    merged.add(key, current.get(key));
                } else {
                    merged.add(key, defaults.get(key));
                    LOGGER.info("[VoiceCastAddon] Added new setting: {} = {}", key, defaults.get(key));
                }
            }

            Path backupFile = getSettingsFile().getParent().resolve(SETTINGS_FILE_NAME + ".backup");
            Files.copy(getSettingsFile(), backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[VoiceCastAddon] Created settings backup at: {}", backupFile);

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
            LOGGER.error("[VoiceCastAddon] Failed to read client settings", e);
            return defaultSettings();
        }
    }

    private static JsonObject defaultSettings() {
        JsonObject root = new JsonObject();
        root.addProperty(SETTINGS_VERSION_KEY, CURRENT_SETTINGS_VERSION);
        root.addProperty(INPUT_DEVICE_KEY, "");
        root.addProperty(MATCH_THRESHOLD_KEY, 50.0);
        return root;
    }
}
