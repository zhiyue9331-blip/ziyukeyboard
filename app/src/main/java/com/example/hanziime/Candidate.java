package com.example.hanziime;

import java.util.Objects;

public final class Candidate {
    public enum Source { PINYIN, ASSEMBLY, HANDWRITING, USER }

    private final String text;
    private final String pinyin;
    private final int score;
    private final Source source;

    public Candidate(String text, String pinyin, int score, Source source) {
        this.text = Objects.requireNonNull(text);
        this.pinyin = Objects.requireNonNull(pinyin);
        this.score = score;
        this.source = Objects.requireNonNull(source);
    }

    public String text() { return text; }
    public String pinyin() { return pinyin; }
    public int score() { return score; }
    public Source source() { return source; }
}
