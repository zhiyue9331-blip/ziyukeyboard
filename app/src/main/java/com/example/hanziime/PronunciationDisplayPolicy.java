package com.example.hanziime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Shows pronunciation only when a candidate contains a character outside the common set. */
final class PronunciationDisplayPolicy {
    private final Set<Integer> commonCharacters = new HashSet<>();

    PronunciationDisplayPolicy(Context context) {
        try {
            load(new InputStreamReader(context.getAssets().open("common_hanzi.txt"),
                    StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("无法加载常用字表", error);
        }
    }

    PronunciationDisplayPolicy(Reader source) {
        load(source);
    }

    private void load(Reader source) {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (int offset = 0; offset < line.length();) {
                    int codePoint = Character.codePointAt(line, offset);
                    if (isHan(codePoint)) commonCharacters.add(codePoint);
                    offset += Character.charCount(codePoint);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("无法读取常用字表", error);
        }
    }

    boolean shouldShow(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = Character.codePointAt(text, offset);
            if (isHan(codePoint) && !commonCharacters.contains(codePoint)) return true;
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    String labelFor(Candidate candidate) {
        String pronunciation = shouldShow(candidate.text()) && !candidate.pinyin().isBlank()
                ? "  " + candidate.pinyin() : "";
        String detail = candidate.annotation().isEmpty()
                ? "" : " 〔" + candidate.annotation() + "〕";
        return candidate.text() + pronunciation + detail;
    }

    private static boolean isHan(int codePoint) {
        return codePoint >= 0x3400 && codePoint <= 0x4DBF
                || codePoint >= 0x4E00 && codePoint <= 0x9FFF
                || codePoint >= 0xF900 && codePoint <= 0xFAFF
                || codePoint >= 0x20000 && codePoint <= 0x2FA1F;
    }
}
