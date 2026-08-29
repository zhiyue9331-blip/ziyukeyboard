package com.example.hanziime;

import java.util.HashMap;
import java.util.Map;

/** Offline fallback used to annotate common handwriting results without a network call. */
public final class HanziPronunciation {
    private static final Map<Character, String> PINYIN = new HashMap<>();

    static {
        String[] entries = {
                "你:nǐ", "好:hǎo", "我:wǒ", "们:men", "中:zhōng", "国:guó", "人:rén",
                "大:dà", "小:xiǎo", "日:rì", "月:yuè", "明:míng", "王:wáng", "玉:yù",
                "珏:jué", "九:jiǔ", "旮:gā", "行:xíng / háng", "重:zhòng / chóng",
                "长:cháng / zhǎng", "乐:lè / yuè", "和:hé / hè / huó", "的:de", "是:shì",
                "天:tiān", "气:qì", "学:xué", "习:xí", "字:zì", "语:yǔ", "手:shǒu",
                "写:xiě", "输:shū", "入:rù", "法:fǎ", "林:lín", "森:sēn", "水:shuǐ",
                "火:huǒ", "木:mù", "金:jīn", "土:tǔ", "女:nǚ", "子:zǐ", "心:xīn",
                "有:yǒu", "在:zài", "来:lái", "去:qù", "看:kàn", "说:shuō", "听:tīng",
                "读:dú", "爱:ài", "家:jiā", "朋:péng", "友:yǒu", "今:jīn", "晚:wǎn",
                "早:zǎo", "上:shàng", "下:xià", "左:zuǒ", "右:yòu", "前:qián", "后:hòu"
        };
        for (String entry : entries) {
            int separator = entry.indexOf(':');
            PINYIN.put(entry.charAt(0), entry.substring(separator + 1));
        }
    }

    private HanziPronunciation() {}

    public static String of(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            String pinyin = PINYIN.get(text.charAt(i));
            if (pinyin != null) {
                if (!result.isEmpty()) result.append(' ');
                result.append(pinyin);
            }
        }
        return result.isEmpty() ? "读音待补充" : result.toString();
    }
}
