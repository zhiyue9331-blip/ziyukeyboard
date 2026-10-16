package com.example.hanziime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AssemblyEngineTest {
    private static final String DICTIONARY = """
            jiuri\t旮\tgā\t九+日\t900
            wangyu,wangyv\t珏\tjué\t王+玉\t980
            """;

    @Test
    public void combinesNineAndSunIntoGa() {
        Candidate candidate = new AssemblyEngine(new StringReader(DICTIONARY))
                .search("jiuri").get(0);
        assertEquals("旮", candidate.text());
        assertEquals("gā", candidate.pinyin());
        assertEquals("九+日", candidate.annotation());
    }

    @Test
    public void exactSpellingBeatsAHeavierLongerCode() {
        AssemblyEngine engine = new AssemblyEngine(new StringReader("""
                koukou\t吕\tlǚ\t口+口\t650
                koukoukou\t品\tpǐn\t口+口+口\t500000
                """));
        assertEquals("吕", engine.search("koukou").get(0).text());
        assertEquals(List.of("吕"), engine.exactMatches("koukou").stream()
                .map(Candidate::text).toList());
        assertEquals("品", engine.search("koukoukou").get(0).text());
    }

    @Test
    public void acceptsVAliasForWangYu() {
        Candidate candidate = new AssemblyEngine(new StringReader(DICTIONARY))
                .search("wangyv").get(0);
        assertEquals("珏", candidate.text());
        assertEquals("jué", candidate.pinyin());
    }

    @Test
    public void decoratesNativeAssemblyCandidateWithPronunciationAndComponents() {
        AssemblyEngine engine = new AssemblyEngine(new StringReader(DICTIONARY));
        Candidate nativeCandidate = new Candidate("旮", "jiuri", 100,
                Candidate.Source.RIME_ASSEMBLY);

        Candidate decorated = engine.decorateRimeCandidates(List.of(nativeCandidate)).get(0);

        assertEquals("gā", decorated.pinyin());
        assertEquals("九+日", decorated.annotation());
        assertEquals(Candidate.Source.RIME_ASSEMBLY, decorated.source());
    }

    @Test
    public void bundledDictionaryContainsThousandsOfGeneratedCombinations() throws IOException {
        AssemblyEngine engine = new AssemblyEngine(new FileReader(asset("assembly_full.tsv")));
        assertTrue(engine.search("jiuri").stream()
                .anyMatch(candidate -> candidate.text().equals("旮")));
        assertTrue(engine.search("wangyu").stream()
                .anyMatch(candidate -> candidate.text().equals("珏")));
        assertFalseOrMoreThanExamples(engine);
    }

    @Test
    public void curatedListDoesNotHideAMuchCommonerExactMatch() throws IOException {
        AssemblyEngine engine = new AssemblyEngine(
                new InputStreamReader(new FileInputStream(asset("assembly_dictionary.tsv")),
                        StandardCharsets.UTF_8),
                new InputStreamReader(new FileInputStream(asset("assembly_full.tsv")),
                        StandardCharsets.UTF_8));
        assertEquals("昌", engine.search("riri").get(0).text());
        assertEquals("晚", engine.search("rimian").get(0).text());
        assertEquals("吧", engine.search("kouba").get(0).text());
        assertEquals("只", engine.search("kueba").get(0).text());
        assertEquals("旮", engine.search("jiuri").get(0).text());
        assertEquals("林", engine.search("mumu").get(0).text());
        assertEquals("吕", engine.search("koukou").get(0).text());
    }

    private static void assertFalseOrMoreThanExamples(AssemblyEngine engine) {
        assertTrue(engine.search("mumu").size() > 2);
    }

    private static File asset(String name) {
        File direct = new File("src/main/assets", name);
        return direct.isFile() ? direct : new File("app/src/main/assets", name);
    }
}
