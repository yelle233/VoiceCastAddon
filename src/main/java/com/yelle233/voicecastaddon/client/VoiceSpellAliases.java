package com.yelle233.voicecastaddon.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VoiceSpellAliases {
    private static final Map<String, ResourceLocation> ALIASES = new HashMap<>();

    static {
        // fireball
        add("fireball", "irons_spellbooks:fireball");
        add("fire ball", "irons_spellbooks:fireball");
        add("火球", "irons_spellbooks:fireball");
        add("火球术", "irons_spellbooks:fireball");

        // heal
        add("heal", "irons_spellbooks:heal");
        add("healing", "irons_spellbooks:heal");
        add("治疗", "irons_spellbooks:heal");
        add("治疗术", "irons_spellbooks:heal");

        // lightning lance
        add("lightning", "irons_spellbooks:lightning_lance");
        add("lightning lance", "irons_spellbooks:lightning_lance");
        add("闪电", "irons_spellbooks:lightning_lance");
        add("雷枪", "irons_spellbooks:lightning_lance");
    }

    private static void add(String alias, String spellId) {
        ALIASES.put(normalize(alias), ResourceLocation.parse(spellId));
    }

    public static ResourceLocation match(String rawText) {
        if (rawText == null) return null;
        return ALIASES.get(normalize(rawText));
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace(".", " ")
                .replace("！", " ")
                .replace("!", " ")
                .replace("？", " ")
                .replace("?", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private VoiceSpellAliases() {
    }
}
