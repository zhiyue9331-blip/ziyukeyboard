package com.example.hanziime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class PinyinEngine {
    private static final int MAX_CANDIDATES = 24;
    private final List<Entry> entries = new ArrayList<>();

    public PinyinEngine(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("pinyin_dictionary.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t");
                if (fields.length >= 4) {
                    entries.add(new Entry(fields[0], fields[1], fields[2],
                            Integer.parseInt(fields[3])));
                }
            }
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("无法加载基础拼音词库", error);
        }
    }

    public List<Candidate> search(String rawInput, boolean fuzzyEnabled) {
        String query = normalize(rawInput);
        if (query.isEmpty()) return List.of();
        String comparableQuery = fuzzyEnabled ? fuzzyCanonical(query) : query;
        List<Scored> matches = new ArrayList<>();

        for (Entry entry : entries) {
            String full = fuzzyEnabled ? fuzzyCanonical(entry.key) : entry.key;
            String initials = fuzzyEnabled ? fuzzyCanonical(entry.initials) : entry.initials;
            int quality = matchQuality(comparableQuery, full, initials);
            if (quality > 0) {
                matches.add(new Scored(entry, entry.frequency + quality));
            }
        }

        matches.sort(Comparator.comparingInt(Scored::score).reversed()
                .thenComparingInt(item -> item.entry.text.length()));
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_CANDIDATES, matches.size()); i++) {
            Scored item = matches.get(i);
            result.add(new Candidate(item.entry.text, item.entry.displayPinyin,
                    item.score, Candidate.Source.PINYIN));
        }
        return result;
    }

    private static int matchQuality(String query, String full, String initials) {
        if (full.equals(query)) return 10_000;
        if (full.startsWith(query)) return 7_000 - (full.length() - query.length()) * 10;
        if (query.length() >= 2 && initials.equals(query)) return 5_000;
        if (query.length() >= 2 && initials.startsWith(query)) return 3_000;
        return 0;
    }

    static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT)
                .replace("ü", "v")
                .replaceAll("[^a-z]", "");
    }

    static String fuzzyCanonical(String value) {
        String result = value;
        result = result.replace("zh", "z").replace("ch", "c").replace("sh", "s");
        result = result.replace('n', 'l');
        result = result.replace('f', 'h');
        result = result.replace("iang", "ian").replace("uang", "uan");
        result = result.replace("ang", "an").replace("eng", "en").replace("ing", "in");
        return result;
    }

    private record Entry(String key, String initials, String text, String displayPinyin, int frequency) {
        Entry(String key, String text, String displayPinyin, int frequency) {
            this(key, initialsOf(displayPinyin), text, displayPinyin, frequency);
        }

        private static String initialsOf(String displayPinyin) {
            StringBuilder initials = new StringBuilder();
            for (String syllable : displayPinyin.split(" ")) {
                String plain = removeTone(syllable);
                if (!plain.isEmpty()) initials.append(plain.charAt(0));
            }
            return initials.toString();
        }

        private static String removeTone(String text) {
            return text.toLowerCase(Locale.ROOT)
                    .replaceAll("[āáǎà]", "a").replaceAll("[ēéěè]", "e")
                    .replaceAll("[īíǐì]", "i").replaceAll("[ōóǒò]", "o")
                    .replaceAll("[ūúǔù]", "u").replaceAll("[ǖǘǚǜü]", "v");
        }
    }

    private record Scored(Entry entry, int score) {}
}
