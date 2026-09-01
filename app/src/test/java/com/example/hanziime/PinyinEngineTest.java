package com.example.hanziime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
        assertEquals("nihao", PinyinEngine.normalize("nǐ hǎo"));
    }

    @Test
    public void fuzzyCanonicalHandlesCommonSouthernPairs() {
        assertEquals(PinyinEngine.fuzzyCanonical("zhang"),
                PinyinEngine.fuzzyCanonical("zan"));
        assertEquals(PinyinEngine.fuzzyCanonical("ling"),
                PinyinEngine.fuzzyCanonical("nin"));
    }

    @Test
    public void bundledDictionaryProvidesRealWorldPhrases() throws IOException {
        PinyinEngine engine = new PinyinEngine(new FileReader(asset("pinyin_rime.tsv")));
        assertTrue(engine.search("zhongguo", false).stream()
                .anyMatch(candidate -> candidate.text().equals("中国")));
        assertTrue(engine.search("beijing", false).stream()
                .anyMatch(candidate -> candidate.text().equals("北京")));
        assertTrue(engine.search("shijie", false).stream()
                .anyMatch(candidate -> candidate.text().equals("世界")));
        List<Candidate> morning = engine.search("zaoshang", false);
        assertTrue(morning.stream().anyMatch(candidate -> candidate.text().equals("早上")));
        assertTrue(morning.stream().anyMatch(candidate -> candidate.text().equals("早上好")));
    }

    private static File asset(String name) {
        File direct = new File("src/main/assets", name);
        return direct.isFile() ? direct : new File("app/src/main/assets", name);
    }
}
