package com.yelle233.voicecastaddon.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VoiceSpellAliases {
    private static final Map<String, ResourceLocation> ALIASES = new HashMap<>();

    static {
        add("fireball", "irons_spellbooks:fireball");
        add("fire ball", "irons_spellbooks:fireball");
        add("\u706b\u7403", "irons_spellbooks:fireball");
        add("\u706b\u7403\u672f", "irons_spellbooks:fireball");

        add("heal", "irons_spellbooks:heal");
        add("healing", "irons_spellbooks:heal");
        add("\u6cbb\u7597", "irons_spellbooks:heal");
        add("\u6cbb\u7597\u672f", "irons_spellbooks:heal");

        add("lightning", "irons_spellbooks:lightning_lance");
        add("lightning lance", "irons_spellbooks:lightning_lance");
        add("\u95ea\u7535", "irons_spellbooks:lightning_lance");
        add("\u96f7\u67aa", "irons_spellbooks:lightning_lance");
    }

    private static void add(String alias, String spellId) {
        ALIASES.put(normalize(alias), ResourceLocation.parse(spellId));
    }

    public static ResourceLocation match(String rawText) {
        if (rawText == null) {
            return null;
        }
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

    private VoiceSpellAliases() {
    }
}
