package com.example.hanziime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.StringReader;

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
    public void acceptsVAliasForWangYu() {
        Candidate candidate = new AssemblyEngine(new StringReader(DICTIONARY))
                .search("wangyv").get(0);
        assertEquals("珏", candidate.text());
        assertEquals("jué", candidate.pinyin());
    }
}
