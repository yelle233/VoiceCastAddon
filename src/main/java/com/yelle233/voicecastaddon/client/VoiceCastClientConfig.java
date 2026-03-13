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

    private VoiceCastClientConfig() {
    }

    public static void ensureClientFiles() {
        try {
            Files.createDirectories(getConfigDir());
            Files.createDirectories(getCustomModelsDir());
            if (Files.notExists(getAliasFile())) {
                writeDefaultAliasFile();
            }
            if (Files.notExists(getSettingsFile())) {
                writeDefaultSettingsFile();
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
        Files.writeString(getAliasFile(), GSON.toJson(defaultAliases()), StandardCharsets.UTF_8);
    }

    private static void writeDefaultSettingsFile() throws IOException {
        Files.writeString(getSettingsFile(), GSON.toJson(defaultSettings()), StandardCharsets.UTF_8);
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
        defaults.put("irons_spellbooks:fireball", List.of("fireball", "fire ball", "\u706b\u7403", "\u706b\u7403\u672f"));
        defaults.put("irons_spellbooks:heal", List.of("heal", "healing", "\u6cbb\u7597", "\u6cbb\u7597\u672f"));
        defaults.put("irons_spellbooks:lightning_lance", List.of("lightning", "lightning lance", "\u95ea\u7535", "\u95ea\u7535\u67aa"));
        return defaults;
    }

    private static JsonObject defaultSettings() {
        JsonObject root = new JsonObject();
        root.addProperty(INPUT_DEVICE_KEY, "");
        return root;
    }
}
