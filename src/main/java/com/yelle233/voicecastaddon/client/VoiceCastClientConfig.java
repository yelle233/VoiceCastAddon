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

    private static final String CONFIG_VERSION_KEY = "_config_version";
    private static final int CURRENT_CONFIG_VERSION = 1;

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
        defaults.put("irons_spellbooks:fireball", List.of("fireball", "火球"));
        defaults.put("irons_spellbooks:heal", List.of("heal", "治疗术"));

        return defaults;
    }

    private static JsonObject defaultSettings() {
        JsonObject root = new JsonObject();
        root.addProperty(INPUT_DEVICE_KEY, "");
        return root;
    }
}
