package com.example.hanziime;

import java.util.Objects;

public final class Candidate {
    public enum Source { PINYIN, RIME, RIME_ASSEMBLY, ASSEMBLY, HANDWRITING, USER }

    private final String text;
    private final String pinyin;
    private final int score;
    private final Source source;
    private final String annotation;
    private final int consumedInputLength;

    public Candidate(String text, String pinyin, int score, Source source) {
        this(text, pinyin, score, source, "", -1);
    }

    public Candidate(String text, String pinyin, int score, Source source, String annotation) {
        this(text, pinyin, score, source, annotation, -1);
    }

    public Candidate(String text, String pinyin, int score, Source source, String annotation,
                     int consumedInputLength) {
        this.text = Objects.requireNonNull(text);
        this.pinyin = Objects.requireNonNull(pinyin);
        this.score = score;
        this.source = Objects.requireNonNull(source);
        this.annotation = Objects.requireNonNull(annotation);
        this.consumedInputLength = consumedInputLength;
    }

    public String text() { return text; }
    public String pinyin() { return pinyin; }
    public int score() { return score; }
    public Source source() { return source; }
    public String annotation() { return annotation; }
    public int consumedInputLength() { return consumedInputLength; }
}
