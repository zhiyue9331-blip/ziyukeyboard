package com.example.hanziime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds a Han character from the spoken names of its visible components. */
public final class AssemblyEngine {
    private final List<Entry> entries = new ArrayList<>();

    public AssemblyEngine(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("assembly_dictionary.tsv"), StandardCharsets.UTF_8))) {
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

    public List<Candidate> search(String rawInput) {
        String query = PinyinEngine.normalize(rawInput);
        if (query.isEmpty()) return List.of();
        List<Entry> matches = entries.stream()
                .filter(entry -> entry.key.startsWith(query))
                .sorted(Comparator.<Entry>comparingInt(entry ->
                        entry.key.equals(query) ? entry.frequency + 10_000 : entry.frequency)
                        .reversed())
                .limit(24)
                .toList();
        List<Candidate> result = new ArrayList<>();
        for (Entry entry : matches) {
            Candidate candidate = new Candidate(entry.text, entry.pinyin, entry.frequency,
                    Candidate.Source.ASSEMBLY, entry.components);
            if (result.stream().noneMatch(existing -> existing.text().equals(candidate.text()))) {
                result.add(candidate);
            }
        }
        return result;
    }

    private record Entry(String key, String text, String pinyin,
                         String components, int frequency) {}
}
