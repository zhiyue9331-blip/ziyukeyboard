package com.example.hanziime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class CandidateMergeTest {
    @Test
    public void combinesRimeAndCompletionCandidatesWithoutDuplicates() {
        Candidate rime = new Candidate("早上", "zao shang", 100,
                Candidate.Source.RIME);
        Candidate duplicate = new Candidate("早上", "zǎo shang", 90,
                Candidate.Source.PINYIN);
        Candidate completion = new Candidate("早上好", "zǎo shang hǎo", 80,
                Candidate.Source.PINYIN);

        List<Candidate> result = HanziInputMethodService.mergeCandidates(
                List.of(rime), List.of(duplicate, completion));

        assertEquals(List.of("早上", "早上好"),
                result.stream().map(Candidate::text).toList());
        assertEquals(Candidate.Source.RIME, result.get(0).source());
    }
}
