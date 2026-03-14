package com.yelle233.voicecastaddon.client;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;

import java.util.Locale;

public class PinyinMatcher {
    private static final HanyuPinyinOutputFormat FORMAT = new HanyuPinyinOutputFormat();

    static {
        FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    public static String toPinyin(String chinese) {
        if (chinese == null || chinese.isEmpty()) {
            return "";
        }

        StringBuilder pinyin = new StringBuilder();
        for (char c : chinese.toCharArray()) {
            if (isChinese(c)) {
                String[] pinyinArray = getPinyinArray(c);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    pinyin.append(pinyinArray[0]);
                } else {
                    pinyin.append(c);
                }
            } else if (Character.isWhitespace(c)) {
                pinyin.append(" ");
            } else if (Character.isLetterOrDigit(c)) {
                pinyin.append(Character.toLowerCase(c));
            }
        }

        return pinyin.toString().trim().replaceAll("\\s+", " ");
    }

    public static String toPinyinInitials(String chinese) {
        if (chinese == null || chinese.isEmpty()) {
            return "";
        }

        StringBuilder initials = new StringBuilder();
        for (char c : chinese.toCharArray()) {
            if (isChinese(c)) {
                String[] pinyinArray = getPinyinArray(c);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    initials.append(pinyinArray[0].charAt(0));
                } else {
                    initials.append(c);
                }
            } else if (Character.isWhitespace(c)) {
                initials.append(" ");
            } else if (Character.isLetterOrDigit(c)) {
                initials.append(Character.toLowerCase(c));
            }
        }

        return initials.toString().trim().replaceAll("\\s+", " ");
    }

    private static boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    public static boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                return true;
            }
        }
        return false;
    }

    public static int calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        if (s1.equals(s2)) {
            return 0;
        }
        return levenshteinDistance(s1, s2);
    }

    private static int levenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        if (len1 == 0) return len2;
        if (len2 == 0) return len1;

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[len1][len2];
    }

    private static String[] getPinyinArray(char c) {
        try {
            return PinyinHelper.toHanyuPinyinStringArray(c, FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private PinyinMatcher() {
    }
}
