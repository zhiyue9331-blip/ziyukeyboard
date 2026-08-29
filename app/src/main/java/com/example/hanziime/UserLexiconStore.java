package com.example.hanziime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Private, on-device candidate frequency and expression model. */
public final class UserLexiconStore {
    private static final String FREQUENCY = "frequency.";
    private static final String PRONUNCIATION = "pronunciation.";
    private static final String BIGRAM = "bigram.";
    private static final String CUSTOM = "custom.";
    private final SharedPreferences data;

    public UserLexiconStore(Context context) {
        data = context.getSharedPreferences("user_lexicon", Context.MODE_PRIVATE);
    }

    public List<Candidate> rerank(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.<Candidate>comparingInt(candidate ->
                        candidate.score() + data.getInt(FREQUENCY + candidate.text(), 0) * 250)
                .reversed());
        return sorted;
    }

    public void record(Candidate candidate, String previousText) {
        String text = candidate.text();
        SharedPreferences.Editor editor = data.edit();
        editor.putInt(FREQUENCY + text, data.getInt(FREQUENCY + text, 0) + 1);
        editor.putString(PRONUNCIATION + text, candidate.pinyin());
        if (previousText != null && !previousText.isEmpty()) {
            String key = BIGRAM + previousText + "." + text;
            editor.putInt(key, data.getInt(key, 0) + 1);
        }
        editor.apply();
    }

    public List<Candidate> nextAfter(String previousText) {
        if (previousText == null || previousText.isEmpty()) return List.of();
        String prefix = BIGRAM + previousText + ".";
        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : data.getAll().entrySet()) {
            if (!entry.getKey().startsWith(prefix) || !(entry.getValue() instanceof Integer count)) {
                continue;
            }
            String text = entry.getKey().substring(prefix.length());
            String pinyin = data.getString(PRONUNCIATION + text, HanziPronunciation.of(text));
            result.add(new Candidate(text, pinyin, count * 100, Candidate.Source.USER, "常用表达"));
        }
        result.sort(Comparator.comparingInt(Candidate::score).reversed());
        return result.stream().limit(8).toList();
    }

    public List<Candidate> customFor(String rawPinyin) {
        String query = PinyinEngine.normalize(rawPinyin);
        if (query.isEmpty()) return List.of();
        String prefix = CUSTOM + query + ".";
        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : data.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof String pinyin) {
                String text = entry.getKey().substring(prefix.length());
                result.add(new Candidate(text, pinyin, 20_000, Candidate.Source.USER, "自定义"));
            }
        }
        return result;
    }

    public void addCustom(String text, String rawPinyin, String displayPinyin) {
        String query = PinyinEngine.normalize(rawPinyin);
        if (text.isBlank() || query.isEmpty()) return;
        String shown = displayPinyin.isBlank() ? rawPinyin.trim() : displayPinyin.trim();
        data.edit().putString(CUSTOM + query + "." + text.trim(), shown).apply();
    }

    public void clearLearning() {
        data.edit().clear().apply();
    }
}
