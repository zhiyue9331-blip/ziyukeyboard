package com.example.hanziime;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HanziPronunciationTest {
    @Test
    public void reportsMultipleReadingsForPolyphone() {
        assertEquals("xíng / háng", HanziPronunciation.of("行"));
    }

    @Test
    public void annotatesHandwrittenPhrase() {
        assertEquals("nǐ hǎo", HanziPronunciation.of("你好"));
    }
}
