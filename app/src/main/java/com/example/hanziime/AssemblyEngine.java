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
import java.util.Map;
import java.util.Set;

/** Finds a Han character from the spoken names of its visible components. */
public final class AssemblyEngine {
    private final Map<String, List<Entry>> prefixIndex = new HashMap<>();
    private final Map<String, Entry> displayEntries = new HashMap<>();

    public AssemblyEngine(Context context) {
        this(context, true);
    }

    AssemblyEngine(Context context, boolean includeFullDictionary) {
        load(openAsset(context, "assembly_dictionary.tsv"), 1_000_000);
        if (includeFullDictionary) load(openAsset(context, "assembly_full.tsv"), 0);
    }

    AssemblyEngine(Reader source) {
        load(source, 0);
    }

    private void load(Reader source, int priorityBonus) {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t");
                if (fields.length >= 5) {
                    for (String alias : fields[0].split(",")) {
                        addEntry(new Entry(PinyinEngine.normalize(alias), fields[1], fields[2],
                                fields[3], Integer.parseInt(fields[4]) + priorityBonus));
                    }
                }
            }
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("无法加载拼字部件库", error);
        }
    }

    private void addEntry(Entry entry) {
        Entry current = displayEntries.get(entry.text);
        if (current == null || entry.frequency > current.frequency) {
            displayEntries.put(entry.text, entry);
        }
        for (int length = 1; length <= Math.min(2, entry.key.length()); length++) {
            String prefix = entry.key.substring(0, length);
            List<Entry> bucket = prefixIndex.get(prefix);
            if (bucket == null) {
                bucket = new ArrayList<>();
                prefixIndex.put(prefix, bucket);
            }
            bucket.add(entry);
        }
    }

    List<Candidate> decorateRimeCandidates(List<Candidate> candidates) {
        List<Candidate> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Entry entry = displayEntries.get(candidate.text());
            if (entry == null) {
                result.add(candidate);
            } else {
                result.add(new Candidate(candidate.text(), entry.pinyin, candidate.score(),
                        Candidate.Source.RIME_ASSEMBLY, entry.components,
                        candidate.consumedInputLength()));
            }
        }
        return result;
    }

    private static Reader openAsset(Context context, String name) {
        try {
            return new InputStreamReader(context.getAssets().open(name), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("无法打开拼字部件库：" + name, error);
        }
    }

    public List<Candidate> search(String rawInput) {
        String query = PinyinEngine.normalize(rawInput);
        if (query.isEmpty()) return List.of();
        String prefix = query.substring(0, Math.min(2, query.length()));
        List<Entry> matches = new ArrayList<>();
        List<Entry> indexed = prefixIndex.get(prefix);
        if (indexed != null) {
            for (Entry entry : indexed) {
                if (entry.key.startsWith(query)) matches.add(entry);
            }
        }
        Collections.sort(matches, (left, right) -> Integer.compare(
                score(right, query), score(left, query)));
        List<Candidate> result = new ArrayList<>();
        Set<String> seenText = new LinkedHashSet<>();
        for (Entry entry : matches) {
            if (!seenText.add(entry.text)) continue;
            result.add(new Candidate(entry.text, entry.pinyin, entry.frequency,
                    Candidate.Source.ASSEMBLY, entry.components));
            if (result.size() == 24) break;
        }
        return result;
    }

    private static int score(Entry entry, String query) {
        return entry.frequency + (entry.key.equals(query) ? 10_000 : 0);
    }

    private record Entry(String key, String text, String pinyin,
                         String components, int frequency) {}
}
