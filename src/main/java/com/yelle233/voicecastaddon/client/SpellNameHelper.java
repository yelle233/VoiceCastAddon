package com.yelle233.voicecastaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SpellNameHelper {

    public static Component getSpellDisplayName(ResourceLocation spellId) {
        if (spellId == null) {
            return Component.literal("Unknown Spell");
        }

        String[] keyPatterns = {
            "spell." + spellId.getNamespace() + "." + spellId.getPath(),
            spellId.getNamespace() + ".spell." + spellId.getPath(),
            "spell." + spellId.getPath()
        };

        Minecraft mc = Minecraft.getInstance();
        if (mc.getLanguageManager() != null) {
            for (String key : keyPatterns) {
                Component translated = Component.translatable(key);

                String translatedString = translated.getString();
                if (!translatedString.equals(key)) {
                    return translated;
                }
            }
        }

        String path = spellId.getPath();
        String readable = path.replace("_", " ");
        readable = capitalizeWords(readable);
        return Component.literal(readable);
    }

    private static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    private SpellNameHelper() {
    }
}
