package com.example.hanziime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.StringReader;

public class PronunciationDisplayPolicyTest {
    private final PronunciationDisplayPolicy policy =
            new PronunciationDisplayPolicy(new StringReader("你\n好\n世\n界\n"));

    @Test
    public void commonCandidateDoesNotRepeatPinyin() {
        Candidate candidate = new Candidate("你好", "nǐ hǎo", 1, Candidate.Source.PINYIN);
        assertFalse(policy.shouldShow(candidate.text()));
        assertEquals("你好", policy.labelFor(candidate));
    }

    @Test
    public void rareCharacterKeepsPronunciation() {
        Candidate candidate = new Candidate("龘", "dá", 1, Candidate.Source.HANDWRITING);
        assertTrue(policy.shouldShow(candidate.text()));
        assertEquals("龘  dá", policy.labelFor(candidate));
    }

    @Test
    public void assemblyAnnotationRemainsVisible() {
        Candidate candidate = new Candidate("旮", "gā", 1,
                Candidate.Source.ASSEMBLY, "九+日");
        assertEquals("旮  gā 〔九+日〕", policy.labelFor(candidate));
    }
}
