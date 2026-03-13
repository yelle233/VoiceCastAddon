package com.yelle233.voicecastaddon.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VoiceSpellAliases {
    private static final Map<String, ResourceLocation> ALIASES = new HashMap<>();
    private static volatile boolean loaded = false;

    public static ResourceLocation match(String rawText) {
        if (rawText == null) {
            return null;
        }

        ensureLoaded();
        return ALIASES.get(normalize(rawText));
    }

    public static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("\uFF0C", " ")
                .replace(",", " ")
                .replace("\u3002", " ")
                .replace(".", " ")
                .replace("\uFF01", " ")
                .replace("!", " ")
                .replace("\uFF1F", " ")
                .replace("?", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static synchronized void reload() {
        ALIASES.clear();
        VoiceCastClientConfig.loadSpellAliases().forEach((spellId, aliases) -> {
            for (String alias : aliases) {
                ALIASES.put(normalize(alias), ResourceLocation.parse(spellId));
            }
        });
        loaded = true;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    private VoiceSpellAliases() {
    }
}
