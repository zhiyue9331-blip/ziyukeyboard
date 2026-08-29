package com.example.hanziime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds a Han character from the spoken names of its visible components. */
public final class AssemblyEngine {
    private final List<Entry> entries = new ArrayList<>();

    public AssemblyEngine(Context context) {
        this(openAsset(context));
    }

    AssemblyEngine(Reader source) {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t");
                if (fields.length >= 5) {
                    for (String alias : fields[0].split(",")) {
                        entries.add(new Entry(PinyinEngine.normalize(alias), fields[1], fields[2],
                                fields[3], Integer.parseInt(fields[4])));
                    }
                }
            }
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("无法加载拼字部件库", error);
        }
    }

    private static Reader openAsset(Context context) {
        try {
            return new InputStreamReader(context.getAssets().open("assembly_dictionary.tsv"),
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("无法打开拼字部件库", error);
        }
    }

    public List<Candidate> search(String rawInput) {
        String query = PinyinEngine.normalize(rawInput);
        if (query.isEmpty()) return List.of();
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.key.startsWith(query)) matches.add(entry);
        }
        Collections.sort(matches, (left, right) -> Integer.compare(
                score(right, query), score(left, query)));
        List<Candidate> result = new ArrayList<>();
        for (int i = 0; i < Math.min(24, matches.size()); i++) {
            Entry entry = matches.get(i);
            Candidate candidate = new Candidate(entry.text, entry.pinyin, entry.frequency,
                    Candidate.Source.ASSEMBLY, entry.components);
            boolean duplicate = false;
            for (Candidate existing : result) {
                if (existing.text().equals(candidate.text())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) result.add(candidate);
        }
        return result;
    }

    private static int score(Entry entry, String query) {
        return entry.frequency + (entry.key.equals(query) ? 10_000 : 0);
    }

    private record Entry(String key, String text, String pinyin,
                         String components, int frequency) {}
}
