package com.example.hanziime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.StringReader;
import java.util.List;

public class PinyinEngineTest {
    private static final String DICTIONARY = """
            ni\t你\tnǐ\t900
            nihao\t你好\tnǐ hǎo\t1000
            zhongguo\t中国\tzhōng guó\t1100
            """;

    @Test
    public void exactPinyinRanksExactWord() {
        PinyinEngine engine = new PinyinEngine(new StringReader(DICTIONARY));
        List<Candidate> result = engine.search("nihao", false);
        assertFalse(result.isEmpty());
        assertEquals("你好", result.get(0).text());
        assertEquals("nǐ hǎo", result.get(0).pinyin());
    }

    @Test
    public void initialsFindPhrase() {
        PinyinEngine engine = new PinyinEngine(new StringReader(DICTIONARY));
        assertEquals("中国", engine.search("zg", false).get(0).text());
    }

    @Test
    public void normalizesUmlautAndSeparators() {
        assertEquals("nvpengyou", PinyinEngine.normalize("NÜ-peng'you"));
    }

    @Test
    public void fuzzyCanonicalHandlesCommonSouthernPairs() {
        assertEquals(PinyinEngine.fuzzyCanonical("zhang"),
                PinyinEngine.fuzzyCanonical("zan"));
        assertEquals(PinyinEngine.fuzzyCanonical("ling"),
                PinyinEngine.fuzzyCanonical("nin"));
    }
}
