package com.coolxer.utils;

/**
 * 字符串转换工具类
 */
public class StringConvertUtils {

    /**
     * 将下划线格式的字符串转换成驼峰格式的字符串
     * @param str 下划线格式的字符串
     * @param firstLower 第一个字母是否小写
     * @return 驼峰格式的字符串
     */
    public static String underscoreToCamel(String str,boolean firstLower) {
        if (str == null || str.trim().length() == 0) {
            return str;
        }

        StringBuilder sb = new StringBuilder();

        // 首先将原字符串按照下划线分割成多个单词
        String[] words = str.toLowerCase().split("_");

        // 将每个单词的首字母大写，并拼接起来
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i == 0 && firstLower) {
                sb.append(word);
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
            }
        }

        return sb.toString();
    }

    /**
     * 将驼峰格式的字符串转换成下划线格式的字符串
     * @param str 驼峰格式的字符串
     * @return 下划线格式的字符串
     */
    public static String camelToUnderscore(String str) {
        if (str == null || str.trim().length() == 0) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        // 按照驼峰规则把字符串拆成多个单词
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append("_");
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }


}
