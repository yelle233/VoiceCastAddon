package com.yelle233.voicecastaddon.server;

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

public final class VoiceCastServerConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final String SERVER_SETTINGS_FILE_NAME = "server_settings.json";
    private static final String SKIP_CAST_TIME_KEY = "skipCastTime";
    private static final String IGNORE_COOLDOWN_KEY = "ignoreCooldown";
    private static final String IGNORE_MANA_KEY = "ignoreManaRequirement";

    private VoiceCastServerConfig() {
    }

    public static void ensureServerFiles() {
        try {
            Files.createDirectories(getConfigDir());

            if (Files.notExists(getServerSettingsFile())) {
                writeDefaultServerSettingsFile();
            }
        } catch (IOException e) {
            LOGGER.error("[VoiceCastAddon] Failed to initialize server config files", e);
        }
    }

    public static boolean getSkipCastTime() {
        ensureServerFiles();
        try {
            String json = Files.readString(getServerSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(SKIP_CAST_TIME_KEY)) {
                return root.get(SKIP_CAST_TIME_KEY).getAsBoolean();
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load skipCastTime setting", e);
        }
        return false;
    }

    public static boolean getIgnoreCooldown() {
        ensureServerFiles();
        try {
            String json = Files.readString(getServerSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(IGNORE_COOLDOWN_KEY)) {
                return root.get(IGNORE_COOLDOWN_KEY).getAsBoolean();
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load ignoreCooldown setting", e);
        }
        return false;
    }

    public static boolean getIgnoreManaRequirement() {
        ensureServerFiles();
        try {
            String json = Files.readString(getServerSettingsFile(), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has(IGNORE_MANA_KEY)) {
                return root.get(IGNORE_MANA_KEY).getAsBoolean();
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to load ignoreManaRequirement setting", e);
        }
        return false;
    }

    private static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get().resolve("voicecastaddon");
    }

    private static Path getServerSettingsFile() {
        return getConfigDir().resolve(SERVER_SETTINGS_FILE_NAME);
    }

    private static void writeDefaultServerSettingsFile() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Server-side settings for VoiceCastAddon. These settings affect game balance and should be configured by server administrators.");
        root.addProperty(SKIP_CAST_TIME_KEY, false);
        root.addProperty(IGNORE_COOLDOWN_KEY, false);
        root.addProperty(IGNORE_MANA_KEY, false);

        Files.writeString(getServerSettingsFile(), GSON.toJson(root), StandardCharsets.UTF_8);
        LOGGER.info("[VoiceCastAddon] Created default server settings file");
    }
}
