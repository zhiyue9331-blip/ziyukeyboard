package com.example.hanziime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import org.junit.Test;

import java.util.List;

public class CandidateMergeTest {
    @Test
    public void combinesRimeAndCompletionCandidatesWithoutDuplicates() {
        Candidate rime = new Candidate("早上", "zao shang", 100,
                Candidate.Source.RIME, "", 8);
        Candidate duplicate = new Candidate("早上", "zǎo shang", 90,
                Candidate.Source.PINYIN);
        Candidate completion = new Candidate("早上好", "zǎo shang hǎo", 80,
                Candidate.Source.PINYIN);

        List<Candidate> result = HanziInputMethodService.mergeCandidates(
                List.of(rime), List.of(duplicate, completion));

        assertEquals(List.of("早上", "早上好"),
                result.stream().map(Candidate::text).toList());
        assertEquals(Candidate.Source.RIME, result.get(0).source());
        assertEquals("zǎo shang", result.get(0).pinyin());
        assertEquals(8, result.get(0).consumedInputLength());
    }

    @Test
    public void selectingFirstSyllableKeepsTheUnconsumedPinyin() {
        Candidate firstCharacter = new Candidate("你", "nihao", 100,
                Candidate.Source.RIME, "", 2);
        Candidate wholePhrase = new Candidate("你好", "nǐ hǎo", 100,
                Candidate.Source.PINYIN);

        assertEquals("hao", HanziInputMethodService.remainingPinyin(
                "nihao", firstCharacter));
        assertEquals("", HanziInputMethodService.remainingPinyin(
                "nihao", wholePhrase));
    }

    @Test
    public void privateInputDetectionRespectsTheInputClass() {
        assertTrue(HanziInputMethodService.isPrivateInput(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertTrue(HanziInputMethodService.isPrivateInput(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        assertTrue(HanziInputMethodService.isPrivateInput(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
        assertTrue(HanziInputMethodService.isPrivateInput(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));

        // URI and numeric password share the same variation bits. The class must disambiguate
        // them so normal address fields can still benefit from learning and custom entries.
        assertFalse(HanziInputMethodService.isPrivateInput(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
        assertFalse(HanziInputMethodService.isPrivateInput(InputType.TYPE_CLASS_TEXT));
        assertFalse(HanziInputMethodService.isPrivateInput(InputType.TYPE_CLASS_PHONE));
    }

    @Test
    public void editorActionsRespectNoEnterActionFlag() {
        assertTrue(HanziInputMethodService.shouldPerformEditorAction(
                EditorInfo.IME_ACTION_SEARCH));
        assertTrue(HanziInputMethodService.shouldPerformEditorAction(
                EditorInfo.IME_ACTION_DONE));
        assertFalse(HanziInputMethodService.shouldPerformEditorAction(
                EditorInfo.IME_ACTION_NONE));
        assertFalse(HanziInputMethodService.shouldPerformEditorAction(
                EditorInfo.IME_ACTION_UNSPECIFIED));
        assertFalse(HanziInputMethodService.shouldPerformEditorAction(
                EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_ENTER_ACTION));
    }

    @Test
    public void editorsCanDisablePersonalizedLearning() {
        assertTrue(HanziInputMethodService.disablesPersonalizedLearning(0x01000000));
        assertTrue(HanziInputMethodService.disablesPersonalizedLearning(
                0x01000000 | EditorInfo.IME_ACTION_DONE));
        assertFalse(HanziInputMethodService.disablesPersonalizedLearning(
                EditorInfo.IME_ACTION_DONE));
    }

    @Test
    public void explicitCustomEntriesReceiveTopRankingPriority() {
        Candidate custom = new Candidate("数智平台", "shuzhipingtai", 20_000,
                Candidate.Source.USER, "自定义");
        Candidate prediction = new Candidate("数智平台", "shuzhipingtai", 20_000,
                Candidate.Source.USER, "常用表达");
        Candidate builtIn = new Candidate("数智平台", "shù zhì píng tái", 1_100_000,
                Candidate.Source.PINYIN);

        assertTrue(custom.score() + UserLexiconStore.rankingBonus(custom)
                > builtIn.score() + UserLexiconStore.rankingBonus(builtIn));
        assertEquals(0, UserLexiconStore.rankingBonus(prediction));
    }

    @Test
    public void readingBonusMovesAFewRanksAndStops() {
        assertEquals(0, UserLexiconStore.readingBonus(0));
        assertEquals(2, UserLexiconStore.readingBonus(1));
        assertEquals(6, UserLexiconStore.readingBonus(3));
        assertEquals(6, UserLexiconStore.readingBonus(40));
        int exact = 6_000_000;
        int fuzzy = 500_000;
        int rimeNeighbor = 100_000;
        assertTrue(exact > fuzzy + UserLexiconStore.readingBonus(40));
        assertTrue(rimeNeighbor + UserLexiconStore.readingBonus(1) - rimeNeighbor <= 6);
    }

    @Test
    public void abbreviationPrefersExactInitialsOverRimeCompletions() {
        List<Candidate> javaOrder = List.of(
                new Candidate("中国", "zhōng guó", 2_500_000, Candidate.Source.PINYIN),
                new Candidate("这个", "zhè ge", 2_490_000, Candidate.Source.PINYIN),
                new Candidate("中国人", "zhōng guó rén", 10, Candidate.Source.PINYIN));
        Candidate china = new Candidate("中国", "zhōng guó", 99_994,
                Candidate.Source.RIME, "", 2);
        List<Candidate> merged = List.of(
                new Candidate("中国人", "zhōng guó rén", 100_000, Candidate.Source.RIME),
                new Candidate("这个人", "zhè ge rén", 99_999, Candidate.Source.RIME),
                new Candidate("这个时候", "zhè ge shí hou", 99_998, Candidate.Source.RIME),
                new Candidate("这个", "zhè ge", 99_997, Candidate.Source.RIME),
                china);

        List<Candidate> result = HanziInputMethodService.preferExactAbbreviations(
                "zg", javaOrder, merged);

        assertEquals(List.of("中国", "这个", "中国人", "这个人", "这个时候"),
                result.stream().map(Candidate::text).toList());
        assertEquals(Candidate.Source.RIME, result.get(0).source());
        assertEquals(2, result.get(0).consumedInputLength());
    }

    @Test
    public void fullPinyinAndSyllablesDoNotUseAbbreviationOrder() {
        List<Candidate> merged = List.of(
                new Candidate("中国人", "zhōng guó rén", 10, Candidate.Source.RIME),
                new Candidate("中国", "zhōng guó", 9, Candidate.Source.RIME));
        assertEquals(List.of("中国人", "中国"),
                HanziInputMethodService.preferExactAbbreviations(
                        "zhongguo", List.of(), merged).stream().map(Candidate::text).toList());

        List<Candidate> de = List.of(
                new Candidate("第二", "dì èr", 90, Candidate.Source.RIME),
                new Candidate("的", "de", 80, Candidate.Source.RIME));
        assertEquals(List.of("第二", "的"),
                HanziInputMethodService.preferExactAbbreviations(
                        "de", List.of(new Candidate("的", "de", 2_000_000, Candidate.Source.PINYIN)),
                        de).stream().map(Candidate::text).toList());
    }

    @Test
    public void partialSyllableFloatsCharactersAboveRimePhrases() {
        List<Candidate> javaOrder = List.of(
                new Candidate("是", "shì", 2_600_000, Candidate.Source.PINYIN),
                new Candidate("说", "shuō", 2_500_000, Candidate.Source.PINYIN),
                new Candidate("试试", "shì shi", 10, Candidate.Source.PINYIN));
        Candidate shi = new Candidate("是", "", 99_000, Candidate.Source.RIME, "", 2);
        List<Candidate> merged = List.of(
                new Candidate("试试", "shì shi", 100_000, Candidate.Source.RIME),
                new Candidate("什么", "shén me", 99_900, Candidate.Source.RIME),
                shi,
                new Candidate("说", "shuō", 2_500_000, Candidate.Source.PINYIN));

        List<Candidate> result = HanziInputMethodService.preferPartialSyllableChars(
                "sh", javaOrder, merged);

        assertEquals(List.of("是", "说", "试试", "什么"),
                result.stream().map(Candidate::text).toList());
        assertEquals(Candidate.Source.RIME, result.get(0).source());
    }

    @Test
    public void bareNFloatsNiAheadOfRimeEnInterjection() {
        List<Candidate> javaOrder = List.of(
                new Candidate("你", "nǐ", 2_600_000, Candidate.Source.PINYIN),
                new Candidate("那", "nà", 2_400_000, Candidate.Source.PINYIN),
                new Candidate("嗯", "ng", 1_700_000, Candidate.Source.PINYIN));
        List<Candidate> merged = List.of(
                new Candidate("嗯", "", 100_000, Candidate.Source.RIME),
                new Candidate("你", "nǐ", 99_000, Candidate.Source.RIME),
                new Candidate("那", "nà", 98_500, Candidate.Source.RIME),
                new Candidate("那个", "nà ge", 98_000, Candidate.Source.RIME));

        List<Candidate> result = HanziInputMethodService.preferPartialSyllableChars(
                "n", javaOrder, merged);

        assertEquals(List.of("你", "那", "嗯", "那个"),
                result.stream().map(Candidate::text).toList());
    }

    @Test
    public void ngKeepsShortAbbreviationAheadOfRimeCompletion() {
        List<Candidate> javaOrder = List.of(
                new Candidate("嗯", "ng", 2_000_000, Candidate.Source.PINYIN),
                new Candidate("那个", "nà ge", -600_000, Candidate.Source.PINYIN),
                new Candidate("哪个", "nǎ ge", -610_000, Candidate.Source.PINYIN));
        List<Candidate> merged = List.of(
                new Candidate("那个人", "nà gè rén", 100_000, Candidate.Source.RIME),
                new Candidate("嗯", "ng", 99_000, Candidate.Source.RIME),
                new Candidate("那个", "nà ge", 98_000, Candidate.Source.RIME));

        List<Candidate> result = HanziInputMethodService.preferNearSyllableInitials(
                "ng", javaOrder, merged);

        assertEquals(List.of("嗯", "那个", "那个人"),
                result.stream().map(Candidate::text).toList());
    }

    @Test
    public void assemblyExactCodeLeadsTheLongerRimeCompletion() {
        List<Candidate> exact = List.of(
                new Candidate("吕", "lǚ", 650, Candidate.Source.ASSEMBLY, "口+口"));
        Candidate lv = new Candidate("吕", "", 99_000, Candidate.Source.RIME_ASSEMBLY, "", 6);
        List<Candidate> merged = List.of(
                new Candidate("品", "pǐn", 100_000, Candidate.Source.RIME_ASSEMBLY, "口+口+口"),
                new Candidate("哭", "kū", 99_500, Candidate.Source.RIME_ASSEMBLY),
                lv);

        List<Candidate> result = HanziInputMethodService.preferExactAssembly(exact, merged);

        assertEquals(List.of("吕", "品", "哭"),
                result.stream().map(Candidate::text).toList());
        assertEquals(Candidate.Source.RIME_ASSEMBLY, result.get(0).source());
        assertEquals(6, result.get(0).consumedInputLength());
    }

    @Test
    public void buriedSyllableAbbreviationIsNotPromoted() {
        List<Candidate> javaOrder = new java.util.ArrayList<>();
        javaOrder.add(new Candidate("的", "de", 3_000_000, Candidate.Source.PINYIN));
        for (int i = 0; i < 20; i++) {
            javaOrder.add(new Candidate("词" + i, "de hua", 1000 - i, Candidate.Source.PINYIN));
        }
        javaOrder.add(new Candidate("第二", "dì èr", -1_700_000, Candidate.Source.PINYIN));
        List<Candidate> merged = List.of(
                new Candidate("的", "de", 3_000_000, Candidate.Source.RIME),
                new Candidate("得了", "dé le", 50_000, Candidate.Source.RIME),
                new Candidate("第二", "dì èr", 40_000, Candidate.Source.RIME));

        assertEquals(List.of("的", "得了", "第二"),
                HanziInputMethodService.preferNearSyllableInitials(
                        "de", javaOrder, merged).stream().map(Candidate::text).toList());
    }
}
