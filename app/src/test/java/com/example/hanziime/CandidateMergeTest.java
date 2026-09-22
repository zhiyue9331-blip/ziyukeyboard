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
}
