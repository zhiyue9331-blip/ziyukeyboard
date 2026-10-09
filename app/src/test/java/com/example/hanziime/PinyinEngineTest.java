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

    @Test
    public void abbreviationOutranksLongerCompletion() {
        PinyinEngine engine = new PinyinEngine(new StringReader("""
                zhongguo\t中国\tzhōng guó\t1240
                zhege\t这个\tzhè ge\t1160
                zhongguoren\t中国人\tzhōng guó rén\t9244
                zhegeren\t这个人\tzhè ge rén\t2801
                zhongguorenmin\t中国人民\tzhōng guó rén mín\t1364
                beijing\t北京\tbei jing\t53176
                beijingren\t北京人\tběi jīng rén\t669
                nihao\t你好\tnǐ hǎo\t1200
                nihaoma\t你好吗\tnǐ hǎo ma\t182
                """));
        List<Candidate> zg = engine.search("zg", false);
        assertEquals(List.of("中国", "这个"),
                zg.stream().limit(2).map(Candidate::text).toList());
        assertBefore(zg, "中国", "中国人");
        assertBefore(zg, "这个", "中国人");

        List<Candidate> zgr = engine.search("zgr", false);
        assertEquals("中国人", zgr.get(0).text());
        assertBefore(zgr, "中国人", "中国人民");

        assertBefore(engine.search("bj", false), "北京", "北京人");
        List<Candidate> nh = engine.search("nh", false);
        assertEquals("你好", nh.get(0).text());
        assertBefore(nh, "你好", "你好吗");
    }

    @Test
    public void productionAbbreviationPutsChinaFirst() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        List<Candidate> zg = engine.search("zg", false);
        assertEquals("中国", zg.get(0).text());
        assertBefore(zg, "中国", "这个");
        assertBefore(zg, "这个", "中国人");
        assertBefore(engine.search("bj", false), "北京", "北京人");
        List<Candidate> nh = engine.search("nh", false);
        assertEquals("你好", nh.get(0).text());
        assertBefore(nh, "你好", "你好吗");
        List<Candidate> zgr = engine.search("zgr", false);
        assertEquals("中国人", zgr.get(0).text());
        assertBefore(zgr, "中国人", "中国人民");
    }

    @Test
    public void abbreviationQueryIgnoresFullPinyinAndUnfinishedSyllables() {
        assertTrue(PinyinEngine.isAbbreviationQuery("zg"));
        assertTrue(PinyinEngine.isAbbreviationQuery("bj"));
        assertTrue(PinyinEngine.isAbbreviationQuery("nh"));
        assertTrue(PinyinEngine.isAbbreviationQuery("zgr"));
        assertFalse(PinyinEngine.isAbbreviationQuery("de"));
        assertFalse(PinyinEngine.isAbbreviationQuery("zh"));
        assertFalse(PinyinEngine.isAbbreviationQuery("zhon"));
        assertFalse(PinyinEngine.isAbbreviationQuery("nihao"));
        assertFalse(PinyinEngine.isAbbreviationQuery("zhongguo"));
        assertFalse(PinyinEngine.isAbbreviationQuery("shishi"));
        assertFalse(PinyinEngine.isAbbreviationQuery("yihuir"));
        assertFalse(PinyinEngine.isAbbreviationQuery("a"));
        assertEquals("zg", PinyinEngine.initialsOf("zhōng guó"));
        assertEquals("zgr", PinyinEngine.initialsOf("zhōng guó rén"));
    }

    @Test
    public void partialSyllablePrefersCharactersOverPhrases() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        List<Candidate> sh = engine.search("sh", false);
        assertEquals("是", sh.get(0).text());
        assertBefore(sh, "是", "试试");
        assertBefore(sh, "说", "什么");
        List<Candidate> zh = engine.search("zh", false);
        assertEquals("这", zh.get(0).text());
        assertBefore(zh, "这", "这个");
        assertBefore(zh, "中", "中国");
        List<Candidate> zhon = engine.search("zhon", false);
        assertEquals("中", zhon.get(0).text());
        assertBefore(zhon, "中", "中国");
    }

    @Test
    public void ngRanksExactAbbreviationAheadOfLongerWord() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        List<Candidate> ng = engine.search("ng", false);
        assertEquals("嗯", ng.get(0).text());
        assertBefore(ng, "那个", "那个人");
        assertBefore(ng, "哪个", "那个人");
        int neige = -1;
        int second = -1;
        List<Candidate> de = engine.search("de", false);
        for (int i = 0; i < ng.size(); i++) {
            if (ng.get(i).text().equals("那个")) neige = i;
        }
        for (int i = 0; i < de.size(); i++) {
            if (de.get(i).text().equals("第二")) second = i;
        }
        assertTrue("那个 should sit with 嗯, at " + neige, neige >= 0 && neige < 8);
        assertEquals("的", de.get(0).text());
        assertTrue("第二 stays well below 的, at " + second, second < 0 || second > 20);
    }

    @Test
    public void bareNLeadsWithNiNotTheEnInterjection() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        List<Candidate> n = engine.search("n", true);
        assertEquals("你", n.get(0).text());
        assertFalse(n.stream().limit(3).anyMatch(c -> c.text().equals("嗯") || c.text().equals("唔")));
        List<Candidate> en = engine.search("en", false);
        assertEquals("恩", en.get(0).text());
        assertTrue(en.stream().limit(6).anyMatch(c -> c.text().equals("嗯")));
    }

    @Test
    public void yecanLeadsWithPicnicNotFuzzyChang() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        assertEquals("野餐", engine.search("yecan", false).get(0).text());
        List<Candidate> fuzzy = engine.search("yecan", true);
        assertEquals("野餐", fuzzy.get(0).text());
        int chang = -1;
        for (int i = 0; i < fuzzy.size(); i++) {
            if (fuzzy.get(i).text().equals("也常")) chang = i;
        }
        assertTrue(chang < 0 || chang > 0);
    }

    @Test
    public void commonAbbreviationsLeadWithTheExactWord() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        assertEquals("中国", engine.search("zg", false).get(0).text());
        assertEquals("你好", engine.search("nh", false).get(0).text());
        assertEquals("我们", engine.search("wm", false).get(0).text());
        assertEquals("什么", engine.search("sm", false).get(0).text());
        assertEquals("怎么", engine.search("zm", false).get(0).text());
        assertEquals("今天", engine.search("jt", false).get(0).text());
        assertEquals("为什么", engine.search("wsm", false).get(0).text());
        assertEquals("中文", engine.search("zw", false).get(0).text());
        assertEquals("可以", engine.search("ky", false).get(0).text());
        assertEquals("没有", engine.search("my", false).get(0).text());
        assertEquals("现在", engine.search("xz", false).get(0).text());
        assertEquals("知道", engine.search("zd", false).get(0).text());
        assertEquals("喜欢", engine.search("xh", false).get(0).text());
        assertEquals("时间", engine.search("sj", false).get(0).text());
        assertBefore(engine.search("sj", false), "时间", "手机");
        assertBefore(engine.search("wm", false), "我们", "我们一起");
        assertBefore(engine.search("jt", false), "今天", "今天晚上");
        assertBefore(engine.search("bky", false), "不可以", "不可以吗");
    }

    @Test
    public void noTwoLetterAbbreviationRanksALongerCompletionFirst() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        StringBuilder failures = new StringBuilder();
        String letters = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < letters.length(); i++) {
            for (int j = 0; j < letters.length(); j++) {
                surveyAbbreviation(engine, "" + letters.charAt(i) + letters.charAt(j), failures);
            }
        }
        for (String query : new String[] {
                "zgr", "nhm", "wsm", "bky", "wmy", "jtz", "zgg", "rmb", "bjg"}) {
            surveyAbbreviation(engine, query, failures);
        }
        assertEquals(failures.toString(), "", failures.toString());
    }

    private static void surveyAbbreviation(PinyinEngine engine, String query,
                                           StringBuilder failures) {
        if (!PinyinEngine.isAbbreviationQuery(query)) return;
        List<Candidate> hits = engine.search(query, false);
        int exactAt = -1;
        int longerAt = -1;
        for (int i = 0; i < hits.size(); i++) {
            String initials = PinyinEngine.initialsOf(hits.get(i).pinyin());
            if (exactAt < 0 && initials.equals(query)) exactAt = i;
            if (longerAt < 0 && initials.startsWith(query) && initials.length() > query.length()) {
                longerAt = i;
            }
        }
        if (exactAt > 0) {
            String reading = PinyinEngine.normalize(hits.get(0).pinyin());
            if (!reading.equals(query)) {
                failures.append(query).append(" first=").append(hits.get(0).text())
                        .append(" exactAt=").append(exactAt)
                        .append(" sample=").append(preview(hits)).append('\n');
            }
        }
        if (exactAt >= 0 && longerAt >= 0 && longerAt < exactAt) {
            failures.append(query).append(" longer before exact ").append(preview(hits)).append('\n');
        }
    }

    private static String preview(List<Candidate> hits) {
        StringBuilder preview = new StringBuilder();
        int limit = Math.min(6, hits.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) preview.append(' ');
            preview.append(hits.get(i).text());
        }
        return preview.toString();
    }

    @Test
    public void exactReadingLeadsWheneverTheDictionaryHasOne() throws IOException {
        PinyinEngine engine = productionLikeEngine();
        StringBuilder failures = new StringBuilder();
        for (char letter = 'a'; letter <= 'z'; letter++) {
            String query = String.valueOf(letter);
            for (boolean fuzzy : new boolean[] {false, true}) {
                List<Candidate> hits = engine.search(query, fuzzy);
                if (!hits.isEmpty() && hits.get(0).text().length() != 1) {
                    failures.append(query).append(fuzzy ? " fuzzy" : "")
                            .append(" phrase-first ").append(preview(hits)).append('\n');
                }
            }
        }
        for (String query : PinyinEngine.incompleteSyllables()) {
            List<Candidate> hits = engine.search(query, false);
            if (hits.isEmpty() || hits.get(0).text().length() == 1) continue;
            boolean characterExists = false;
            for (Candidate candidate : hits) {
                if (candidate.text().length() == 1
                        && PinyinEngine.normalize(candidate.pinyin()).startsWith(query)) {
                    characterExists = true;
                    break;
                }
            }
            if (characterExists) {
                failures.append(query).append(" partial phrase-first ")
                        .append(preview(hits)).append('\n');
            }
        }
        for (String query : PinyinEngine.syllables()) {
            List<Candidate> hits = engine.search(query, false);
            if (hits.isEmpty() || hits.get(0).text().length() == 1) continue;
            boolean exactChar = false;
            for (Candidate candidate : hits) {
                if (candidate.text().length() == 1
                        && PinyinEngine.normalize(candidate.pinyin()).equals(query)) {
                    exactChar = true;
                    break;
                }
            }
            if (exactChar) {
                failures.append(query).append(" syllable phrase-first ")
                        .append(preview(hits)).append('\n');
            }
        }
        assertEquals("就", engine.search("jiu", false).get(0).text());
        assertEquals("其", engine.search("qi", false).get(0).text());
        assertEquals(failures.toString(), "", failures.toString());
    }

    private static void assertBefore(List<Candidate> hits, String earlier, String later) {
        int earlierAt = -1;
        int laterAt = -1;
        for (int i = 0; i < hits.size(); i++) {
            String text = hits.get(i).text();
            if (text.equals(earlier)) earlierAt = i;
            if (text.equals(later)) laterAt = i;
        }
        assertTrue(earlier + " missing from " + hits.stream().limit(8).map(Candidate::text).toList(),
                earlierAt >= 0);
        assertTrue(later + " should follow " + earlier + ", got "
                        + hits.stream().limit(8).map(Candidate::text).toList(),
                laterAt < 0 || earlierAt < laterAt);
    }

}
