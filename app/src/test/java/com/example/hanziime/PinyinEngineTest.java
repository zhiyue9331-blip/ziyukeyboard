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

    @Test
    public void rareSingleCharactersRemainReachable() throws IOException {
        PinyinEngine engine = new PinyinEngine(new FileReader(asset("pinyin_rime.tsv")));
        assertTrue(engine.search("fu", false).stream()
                .anyMatch(candidate -> candidate.text().equals("祓")));
        assertTrue(engine.search("si", false).stream()
                .anyMatch(candidate -> candidate.text().equals("巳")));
    }

    private static File asset(String name) {
        File direct = new File("src/main/assets", name);
        return direct.isFile() ? direct : new File("app/src/main/assets", name);
    }

    @Test
    public void singleSyllablePrefersCharacterOverPhrase() throws Exception {
        // Mimic production: core dict (priority) + full rime table.
        java.io.StringWriter combined = new java.io.StringWriter();
        try (java.io.BufferedReader core = new java.io.BufferedReader(new FileReader(asset("pinyin_dictionary.tsv")));
             java.io.BufferedReader full = new java.io.BufferedReader(new FileReader(asset("pinyin_rime.tsv")))) {
            String line;
            while ((line = core.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t");
                if (fields.length >= 4) {
                    int freq = Integer.parseInt(fields[3]) + 1_000_000;
                    combined.write(fields[0] + "\t" + fields[1] + "\t" + fields[2] + "\t" + freq + "\n");
                }
            }
            while ((line = full.readLine()) != null) {
                combined.write(line);
                combined.write('\n');
            }
        }
        PinyinEngine engine = new PinyinEngine(new java.io.StringReader(combined.toString()));
        assertEquals("打", engine.search("da", false).get(0).text());
        assertEquals("很", engine.search("hen", false).get(0).text());
        assertEquals("热", engine.search("re", false).get(0).text());
                assertEquals("你好", engine.search("nihao", false).get(0).text());
        assertEquals("啊", engine.search("a", false).get(0).text());
        int love = -1, particle = -1;
        List<Candidate> aCandidates = engine.search("a", false);
        for (int i = 0; i < aCandidates.size(); i++) {
            if (aCandidates.get(i).text().equals("啊")) particle = i;
            if (aCandidates.get(i).text().equals("爱")) love = i;
        }
        assertTrue(particle >= 0);
        assertTrue(love < 0 || particle < love);
        assertEquals("的", engine.search("de", false).get(0).text());
        List<Candidate> deCandidates = engine.search("de", false);
        int deParticle = -1, deng = -1, second = -1;
        for (int i = 0; i < deCandidates.size(); i++) {
            String t = deCandidates.get(i).text();
            if (t.equals("的")) deParticle = i;
            if (t.equals("等")) deng = i;
            if (t.equals("第二")) second = i;
        }
        assertTrue(deParticle == 0);
        assertTrue(deng < 0 || deParticle < deng);
        assertTrue(second < 0 || deParticle < second);

    }

    /** Production-like engine: core dict (+1M) + full rime table. */
    private static PinyinEngine productionLikeEngine() throws IOException {
        java.io.StringWriter combined = new java.io.StringWriter();
        try (java.io.BufferedReader core = new java.io.BufferedReader(new FileReader(asset("pinyin_dictionary.tsv")));
             java.io.BufferedReader full = new java.io.BufferedReader(new FileReader(asset("pinyin_rime.tsv")))) {
            String line;
            while ((line = core.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\t");
                if (fields.length >= 4) {
                    int freq = Integer.parseInt(fields[3]) + 1_000_000;
                    combined.write(fields[0] + "\t" + fields[1] + "\t" + fields[2] + "\t" + freq + "\n");
                }
            }
            while ((line = full.readLine()) != null) {
                combined.write(line);
                combined.write('\n');
            }
        }
        return new PinyinEngine(new java.io.StringReader(combined.toString()));
    }

    @Test
    public void fuzzySingleLetterKeepsLSeparateFromN() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        List<Candidate> lHits = engine.search("l", true);
        assertFalse(lHits.isEmpty());
        List<String> top3 = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(3, lHits.size()); i++) {
            top3.add(lHits.get(i).text());
        }
        assertFalse("fuzzy l must not put 那个 in top 3, got " + top3,
                top3.contains("那个"));
        assertFalse("fuzzy l must not put 嗯 in top 3, got " + top3,
                top3.contains("嗯"));
        assertFalse("fuzzy l must not put 唔 in top 3, got " + top3,
                top3.contains("唔"));
        boolean hasLWord = lHits.stream().limit(5).anyMatch(c -> {
            String t = c.text();
            return t.equals("了") || t.equals("老师") || t.startsWith("里")
                    || t.equals("来") || t.equals("老") || t.equals("两");
        });
        assertTrue("fuzzy l top should include real l-initial words, got "
                + lHits.stream().limit(8).map(Candidate::text).toList(), hasLWord);
        for (Candidate c : lHits.subList(0, Math.min(5, lHits.size()))) {
            String reading = PinyinEngine.normalize(c.pinyin());
            assertTrue("top fuzzy-l hit should be l-initial, got " + c.text()
                            + " / " + c.pinyin(),
                    reading.startsWith("l") || reading.isEmpty());
        }

        List<Candidate> nHits = engine.search("n", true);
        assertFalse(nHits.isEmpty());
        boolean hasNWord = nHits.stream().limit(5).anyMatch(c -> {
            String reading = PinyinEngine.normalize(c.pinyin());
            return reading.startsWith("n") || c.text().equals("嗯") || c.text().equals("唔")
                    || c.text().equals("你") || c.text().equals("那");
        });
        assertTrue("fuzzy n may still show n-words, got "
                + nHits.stream().limit(8).map(Candidate::text).toList(), hasNWord);

        List<Candidate> laFuzzy = engine.search("la", true);
        assertFalse(laFuzzy.isEmpty());
        // Longer fuzzy input may still mix na/la (那个 can appear).
        boolean hasLaOrNa = laFuzzy.stream().limit(12).anyMatch(c -> {
            String t = c.text();
            return t.contains("老") || t.contains("那") || t.equals("拉") || t.equals("啦");
        });
        assertTrue(hasLaOrNa);
    }


    @Test
    public void candidateFixes0410Dict() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        assertEquals("不可以", engine.search("bukeyi", false).get(0).text());
        assertEquals("模糊音", engine.search("mohuyin", false).get(0).text());
        assertEquals("一会儿", engine.search("yihuir", false).get(0).text());
        assertEquals("一会儿", engine.search("yihuier", false).get(0).text());
        assertEquals("一会儿", engine.search("yi huir", false).get(0).text());
        assertEquals("怎么了", engine.search("zenmejiale", false).get(0).text());
        assertEquals("不喜欢", engine.search("buxihuan", false).get(0).text());
        assertEquals("试试", engine.search("shishi", false).get(0).text());
        assertEquals("吧", engine.search("ba", false).get(0).text());
        assertEquals("安", engine.search("an", false).get(0).text());

        // Soft: lone f must not rank multi-char 方便 first
        List<Candidate> fHits = engine.search("f", true);
        assertFalse(fHits.isEmpty());
        assertFalse("lone f must not put 方便 first, got " + fHits.stream().limit(5).map(Candidate::text).toList(),
                fHits.get(0).text().equals("方便"));
    }

}
