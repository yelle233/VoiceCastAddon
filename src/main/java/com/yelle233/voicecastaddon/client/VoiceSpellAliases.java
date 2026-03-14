package com.yelle233.voicecastaddon.client;

import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.Locale;

public class VoiceSpellAliases {
    private static final Map<String, ResourceLocation> ALIASES = new HashMap<>();
    private static final Map<String, ResourceLocation> PINYIN_ALIASES = new HashMap<>();
    private static final Map<String, String> ALIAS_TO_PINYIN = new HashMap<>();
    private static final Map<String, List<String>> ALIAS_KEYWORDS = new HashMap<>();
    private static volatile boolean loaded = false;

    private static final int MAX_FUZZY_DISTANCE = 3;
    private static final double MIN_KEYWORD_COVERAGE = 0.5;

    public static ResourceLocation match(String rawText) {
        if (rawText == null) {
            return null;
        }

        ensureLoaded();
        String normalized = normalize(rawText);

        // Level 1: Exact match
        ResourceLocation result = ALIASES.get(normalized);
        if (result != null) {
            return result;
        }

        String pinyin = PinyinMatcher.toPinyin(normalized);

        // Level 2: Exact pinyin match
        result = PINYIN_ALIASES.get(pinyin);
        if (result != null) {
            return result;
        }

        // Level 3: Fuzzy pinyin match (edit distance)
        result = fuzzyMatchByPinyin(pinyin);
        if (result != null) {
            return result;
        }

        // Level 4: Keyword coverage match
        return keywordCoverageMatch(normalized, pinyin);
    }

    private static ResourceLocation fuzzyMatchByPinyin(String inputPinyin) {
        if (inputPinyin == null || inputPinyin.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Map.Entry<String, String> entry : ALIAS_TO_PINYIN.entrySet()) {
            String aliasPinyin = entry.getValue();
            int distance = PinyinMatcher.calculateSimilarity(inputPinyin, aliasPinyin);

            if (distance <= MAX_FUZZY_DISTANCE && distance < bestDistance) {
                bestDistance = distance;
                bestMatch = entry.getKey();
            }
        }

        return bestMatch != null ? ALIASES.get(bestMatch) : null;
    }

    private static ResourceLocation keywordCoverageMatch(String normalized, String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) {
            return null;
        }

        List<String> inputKeywords = extractKeywords(normalized, pinyin);
        if (inputKeywords.isEmpty()) {
            return null;
        }

        String bestMatch = null;
        double bestCoverage = 0.0;

        for (Map.Entry<String, List<String>> entry : ALIAS_KEYWORDS.entrySet()) {
            List<String> aliasKeywords = entry.getValue();
            if (aliasKeywords.isEmpty()) {
                continue;
            }

            int matchedCount = 0;
            for (String inputKeyword : inputKeywords) {
                for (String aliasKeyword : aliasKeywords) {
                    int distance = PinyinMatcher.calculateSimilarity(inputKeyword, aliasKeyword);
                    if (distance <= 1) {
                        matchedCount++;
                        break;
                    }
                }
            }

            double coverage = (double) matchedCount / aliasKeywords.size();
            if (coverage >= MIN_KEYWORD_COVERAGE && coverage > bestCoverage) {
                bestCoverage = coverage;
                bestMatch = entry.getKey();
            }
        }

        return bestMatch != null ? ALIASES.get(bestMatch) : null;
    }

    private static List<String> extractKeywords(String normalized, String pinyin) {
        List<String> keywords = new ArrayList<>();

        if (PinyinMatcher.containsChinese(normalized)) {
            String[] pinyinWords = pinyin.split("\\s+");
            for (String word : pinyinWords) {
                if (!word.isEmpty()) {
                    keywords.add(word);
                }
            }
        } else {
            String[] words = normalized.split("\\s+");
            for (String word : words) {
                if (word.length() >= 2) {
                    keywords.add(word);
                }
            }
        }

        return keywords;
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
        PINYIN_ALIASES.clear();
        ALIAS_TO_PINYIN.clear();
        ALIAS_KEYWORDS.clear();

        VoiceCastClientConfig.loadSpellAliases().forEach((spellId, aliases) -> {
            ResourceLocation location = ResourceLocation.parse(spellId);
            for (String alias : aliases) {
                String normalized = normalize(alias);
                ALIASES.put(normalized, location);

                String pinyin = PinyinMatcher.toPinyin(normalized);
                if (!pinyin.isEmpty() && !pinyin.equals(normalized)) {
                    PINYIN_ALIASES.put(pinyin, location);
                    ALIAS_TO_PINYIN.put(normalized, pinyin);

                    List<String> keywords = extractKeywords(normalized, pinyin);
                    if (!keywords.isEmpty()) {
                        ALIAS_KEYWORDS.put(normalized, keywords);
                    }
                }
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
