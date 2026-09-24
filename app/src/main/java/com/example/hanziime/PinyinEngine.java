package com.example.hanziime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PinyinEngine {
    private static final int MAX_CANDIDATES = 256;
    private final Map<String, List<Entry>> normalFullIndex = new HashMap<>();
    private final Map<String, List<Entry>> fuzzyFullIndex = new HashMap<>();
    private final Map<String, List<Entry>> normalInitialIndex = new HashMap<>();
    private final Map<String, List<Entry>> fuzzyInitialIndex = new HashMap<>();

    public PinyinEngine(Context context) {
        this(context, true);
    }

    PinyinEngine(Context context, boolean includeFullDictionary) {
        load(openAsset(context, "pinyin_dictionary.tsv"), 1_000_000);
        if (includeFullDictionary) load(openAsset(context, "pinyin_rime.tsv"), 0);
    }

    PinyinEngine(Reader source) {
        load(source, 0);
    }

    private void load(Reader source, int priorityBonus) {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t");
                if (fields.length >= 4) {
                    addEntry(new Entry(normalize(fields[0]), fields[1], fields[2],
                            Integer.parseInt(fields[3]) + priorityBonus));
                }
            }
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("无法加载拼音词库", error);
        }
    }

    private void addEntry(Entry entry) {
        index(normalFullIndex, entry.key, entry);
        index(fuzzyFullIndex, fuzzyCanonical(entry.key), entry);
        index(normalInitialIndex, entry.initials, entry);
        index(fuzzyInitialIndex, fuzzyCanonical(entry.initials), entry);
    }

    private static void index(Map<String, List<Entry>> index, String value, Entry entry) {
        for (int length = 1; length <= Math.min(2, value.length()); length++) {
            String prefix = value.substring(0, length);
            List<Entry> bucket = index.get(prefix);
            if (bucket == null) {
                bucket = new ArrayList<>();
                index.put(prefix, bucket);
            }
            bucket.add(entry);
        }
    }

    private static Reader openAsset(Context context, String name) {
        try {
            return new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("无法打开拼音词库：" + name, error);
        }
    }

    public List<Candidate> search(String rawInput, boolean fuzzyEnabled) {
        String query = normalize(rawInput);
        if (query.isEmpty()) return List.of();
        String comparableQuery = fuzzyEnabled ? fuzzyCanonical(query) : query;
        Map<String, List<Entry>> fullIndex = fuzzyEnabled ? fuzzyFullIndex : normalFullIndex;
        Map<String, List<Entry>> initialIndex = fuzzyEnabled
                ? fuzzyInitialIndex : normalInitialIndex;
        String prefix = comparableQuery.substring(0, Math.min(2, comparableQuery.length()));
        Set<Entry> pool = new LinkedHashSet<>();
        List<Entry> fullEntries = fullIndex.get(prefix);
        List<Entry> initialEntries = initialIndex.get(prefix);
        if (fullEntries != null) pool.addAll(fullEntries);
        if (initialEntries != null) pool.addAll(initialEntries);
        List<Scored> matches = new ArrayList<>();

        for (Entry entry : pool) {
            String full = fuzzyEnabled ? fuzzyCanonical(entry.key) : entry.key;
            String initials = fuzzyEnabled ? fuzzyCanonical(entry.initials) : entry.initials;
            int quality = matchQuality(comparableQuery, full, initials);
            if (quality > 0) matches.add(new Scored(entry, entry.frequency + quality));
        }

        Collections.sort(matches, (left, right) -> {
            int scoreOrder = Integer.compare(right.score, left.score);
            return scoreOrder != 0 ? scoreOrder
                    : Integer.compare(left.entry.text.length(), right.entry.text.length());
        });
        List<Candidate> result = new ArrayList<>();
        Set<String> seenText = new LinkedHashSet<>();
        for (Scored item : matches) {
            if (!seenText.add(item.entry.text)) continue;
            result.add(new Candidate(item.entry.text, item.entry.displayPinyin,
                    item.score, Candidate.Source.PINYIN));
            if (result.size() == MAX_CANDIDATES) break;
        }
        return result;
    }

    private static int matchQuality(String query, String full, String initials) {
        if (full.equals(query)) return 2_000_000;
        if (full.startsWith(query)) return 7_000 - (full.length() - query.length()) * 10;
        if (query.length() >= 2 && initials.equals(query)) return 5_000;
        if (query.length() >= 2 && initials.startsWith(query)) return 3_000;
        return 0;
    }

    static String normalize(String input) {
        return input.toLowerCase(Locale.ROOT)
                .replace("ü", "v")
                .replaceAll("[āáǎà]", "a").replaceAll("[ēéěè]", "e")
                .replaceAll("[īíǐì]", "i").replaceAll("[ōóǒò]", "o")
                .replaceAll("[ūúǔù]", "u").replaceAll("[ǖǘǚǜ]", "v")
                .replaceAll("[^a-z]", "");
    }

    static String fuzzyCanonical(String value) {
        String result = value;
        result = result.replace("zh", "z").replace("ch", "c").replace("sh", "s");
        if (result.startsWith("n")) result = "l" + result.substring(1);
        result = result.replace('f', 'h');
        result = result.replace("iang", "ian").replace("uang", "uan");
        result = result.replace("ang", "an").replace("eng", "en").replace("ing", "in");
        return result;
    }

    private record Entry(String key, String initials, String text, String displayPinyin,
                         int frequency) {
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
