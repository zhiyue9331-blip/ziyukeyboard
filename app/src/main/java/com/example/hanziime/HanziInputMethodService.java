package com.example.hanziime;

import android.inputmethodservice.InputMethodService;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import com.google.mlkit.common.MlKit;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.recognition.Ink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HanziInputMethodService extends InputMethodService
        implements SimpleKeyboardView.Listener {
    // Added in API 26, but it is a plain EditorInfo bit and is safe to honor on older devices.
    private static final int IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000;
    private final StringBuilder composition = new StringBuilder();
    private volatile PinyinEngine pinyinEngine;
    private volatile AssemblyEngine assemblyEngine;
    private volatile RimeEngine rimeEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService dictionaryLoader;
    private volatile boolean destroyed;
    private SimpleKeyboardView keyboard;
    private java.util.List<Candidate> candidates = java.util.List.of();
    private SimpleKeyboardView.InputMode mode = SimpleKeyboardView.InputMode.PINYIN;
    private DigitalInkRecognitionModel handwritingModel;
    private DigitalInkRecognizer handwritingRecognizer;
    private boolean handwritingModelReady;
    private boolean handwritingModelLoading;
    private SharedPreferences preferences;
    private UserLexiconStore userLexicon;
    private boolean privateInput;
    private String previousCommitted = "";
    /** True once the expanded pinyin table (pinyin_rime.tsv) has replaced the core engine. */
    private volatile boolean fullPinyinReady;

    @Override
    public void onCreate() {
        super.onCreate();
        // Keep service startup fast. Android may abandon an IME which blocks while being bound.
        pinyinEngine = new PinyinEngine(this, false);
        assemblyEngine = new AssemblyEngine(this, false);
        preferences = ImePreferences.get(this);
        userLexicon = new UserLexiconStore(this);
        loadFullDictionariesInBackground();
    }

    private void loadFullDictionariesInBackground() {
        dictionaryLoader = Executors.newSingleThreadExecutor();
        dictionaryLoader.execute(() -> {
            try {
                PinyinEngine fullPinyin = new PinyinEngine(getApplicationContext(), true);
                AssemblyEngine fullAssembly = new AssemblyEngine(getApplicationContext(), true);
                mainHandler.post(() -> {
                    if (destroyed) return;
                    pinyinEngine = fullPinyin;
                    assemblyEngine = fullAssembly;
                    fullPinyinReady = true;
                    if (composition.length() > 0) refreshCandidates();
                });
            } catch (RuntimeException ignored) {
                // The built-in core dictionaries remain usable if an expanded asset is damaged.
            }
            if (destroyed) return;
            RimeEngine readyRime = RimeEngine.create(getApplicationContext());
            mainHandler.post(() -> {
                if (destroyed) {
                    if (readyRime != null) readyRime.close();
                    return;
                }
                rimeEngine = readyRime;
                if (composition.length() > 0) refreshCandidates();
            });
        });
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (dictionaryLoader != null) dictionaryLoader.shutdownNow();
        if (rimeEngine != null) rimeEngine.close();
        if (handwritingRecognizer != null) handwritingRecognizer.close();
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Landscape otherwise swaps in the system extract editor and covers the
        // app. On MuMu that window reports a zero-height inset, so the page
        // underneath cannot be tapped. This keyboard already shows candidates.
        return false;
    }

    @Override
    public View onCreateInputView() {
        keyboard = new SimpleKeyboardView(this);
        keyboard.setListener(this);
        keyboard.setMode(mode);
        return keyboard;
    }

    @Override
    public void onLetter(String text) {
        if (mode == SimpleKeyboardView.InputMode.ENGLISH
                || mode == SimpleKeyboardView.InputMode.NUMBER
                || mode == SimpleKeyboardView.InputMode.SYMBOL) {
            commitDirect(text);
            return;
        }
        composition.append(text);
        refreshCandidates();
    }

    @Override
    public void onDirectText(String text) {
        if (composition.length() > 0) {
            if (!candidates.isEmpty()) commitCandidate(candidates.get(0));
            else commitRaw(composition.toString());
        }
        commitDirect(text);
    }

    @Override
    public void onModeSelected(SimpleKeyboardView.InputMode newMode) {
        if (composition.length() > 0) {
            commitRaw(composition.toString());
        }
        mode = newMode;
        if (keyboard != null) keyboard.setMode(newMode);
        if (newMode == SimpleKeyboardView.InputMode.HANDWRITING) {
            ensureHandwritingModel();
        }
    }

    @Override
    public void onRecognizeHandwriting(Ink ink) {
        if (!handwritingModelReady || handwritingRecognizer == null) {
            ensureHandwritingModel();
            return;
        }
        if (keyboard != null) keyboard.showHandwritingStatus("正在识别…");
        handwritingRecognizer.recognize(ink)
                .addOnSuccessListener(result -> {
                    if (destroyed || mode != SimpleKeyboardView.InputMode.HANDWRITING) return;
                    List<Candidate> recognized = new ArrayList<>();
                    int count = Math.min(12, result.getCandidates().size());
                    for (int i = 0; i < count; i++) {
                        String text = result.getCandidates().get(i).getText();
                        recognized.add(new Candidate(text, HanziPronunciation.of(text), 0,
                                Candidate.Source.HANDWRITING));
                    }
                    candidates = recognized;
                    if (keyboard != null) keyboard.showCandidates("手写候选", candidates);
                })
                .addOnFailureListener(error -> {
                    if (!destroyed && keyboard != null)
                        keyboard.showHandwritingStatus("识别失败，请重试");
                });
    }

    @Override
    public void onCandidate(Candidate candidate) {
        commitCandidate(candidate);
    }

    @Override
    public void onSpace() {
        // While composing: space selects first candidate only when one exists.
        // Empty candidate row must not commit a word, raw pinyin, or a stray space.
        if (composition.length() > 0) {
            if (!candidates.isEmpty()) {
                commitCandidate(candidates.get(0));
            }
            return;
        }
        if (!candidates.isEmpty()) {
            commitCandidate(candidates.get(0));
            return;
        }
        commitRaw(" ");
    }

    @Override
    public void onDelete() {
        if (composition.length() > 0) {
            composition.deleteCharAt(composition.length() - 1);
            refreshCandidates();
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            deleteOneCodePoint(connection);
        }
    }

    @Override
    public void onEnter() {
        if (composition.length() > 0) {
            if (!candidates.isEmpty()) commitCandidate(candidates.get(0));
            else commitRaw(composition.toString());
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        EditorInfo info = getCurrentInputEditorInfo();
        int imeOptions = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions;
        if (shouldPerformEditorAction(imeOptions)) {
            connection.performEditorAction(imeOptions & EditorInfo.IME_MASK_ACTION);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    @Override
    public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        clearSessionState();
        privateInput = attribute != null && (isPrivateInput(attribute.inputType)
                || disablesPersonalizedLearning(attribute.imeOptions));
    }

    @Override
    public void onFinishInput() {
        clearSessionState();
        privateInput = false;
        super.onFinishInput();
    }

    private void clearSessionState() {
        composition.setLength(0);
        candidates = java.util.List.of();
        previousCommitted = "";
        if (keyboard != null) {
            keyboard.showCandidates("", candidates);
            keyboard.clearHandwriting();
        }
    }

    private void refreshCandidates() {
        java.util.List<Candidate> fromJava = java.util.List.of();
        if (mode == SimpleKeyboardView.InputMode.ASSEMBLY) {
            java.util.List<Candidate> decoded = rimeEngine == null
                    ? java.util.List.of()
                    : rimeEngine.searchAssembly(composition.toString());
            candidates = mergeCandidates(
                    assemblyEngine.decorateRimeCandidates(decoded),
                    assemblyEngine.search(composition.toString()));
        } else {
            boolean fuzzy = ImePreferences.enabled(preferences, ImePreferences.FUZZY);
            java.util.List<Candidate> decoded = rimeEngine == null
                    ? java.util.List.of()
                    : rimeEngine.search(composition.toString(), fuzzy);
            java.util.List<Candidate> custom = privateInput
                    ? java.util.List.of()
                    : userLexicon.customFor(composition.toString());
            fromJava = pinyinEngine.search(composition.toString(), fuzzy);
            // Exact single-character hits from the local table must lead, otherwise
            // Rime completions (de→等/第二, a→爱) occupy the first slots.
            java.util.List<Candidate> exactSingles = exactSingleCharHits(
                    composition.toString(), fromJava);
            candidates = mergeCandidates(exactSingles, custom, decoded, fromJava);
        }
        if (learningAllowed()) candidates = userLexicon.rerank(candidates);
        if (mode == SimpleKeyboardView.InputMode.ASSEMBLY) {
            // Rime weight and learned frequency would keep 品 ahead of 吕.
            candidates = preferExactAssembly(
                    assemblyEngine.exactMatches(composition.toString()), candidates);
        } else if (mode == SimpleKeyboardView.InputMode.PINYIN) {
            candidates = preferExactSingleChars(composition.toString(), candidates);
            // Rime completion order (and learned frequency) would otherwise keep
            // 中国人 ahead of 中国 for the abbreviation zg.
            candidates = preferExactAbbreviations(
                    composition.toString(), fromJava, candidates);
            candidates = preferPartialSyllableChars(
                    composition.toString(), fromJava, candidates);
            candidates = preferNearSyllableInitials(
                    composition.toString(), fromJava, candidates);
        }
        if (candidates.size() > 256) candidates = new ArrayList<>(candidates.subList(0, 256));
        if (keyboard != null) {
            if (candidates.isEmpty()
                    && composition.length() > 0
                    && !fullPinyinReady
                    && mode == SimpleKeyboardView.InputMode.PINYIN) {
                keyboard.showDictionaryLoading(composition.toString());
            } else {
                keyboard.showCandidates(composition.toString(), candidates);
            }
        }
    }


    /**
     * When the composition is exactly one syllable's pinyin, float single-character
     * candidates with that reading above multi-character phrases from Rime/prefix match.
     */

    /** Single-character PINYIN hits whose reading exactly equals the composition. */
    static List<Candidate> exactSingleCharHits(String rawComposition,
                                               List<Candidate> fromJava) {
        String query = PinyinEngine.normalize(rawComposition);
        if (query.isEmpty() || fromJava.isEmpty()) return List.of();
        List<Candidate> hits = new ArrayList<>();
        for (Candidate candidate : fromJava) {
            if (candidate.text().length() != 1) continue;
            String reading = PinyinEngine.normalize(candidate.pinyin());
            if (reading.equals(query)) hits.add(candidate);
        }
        return hits;
    }

    static List<Candidate> preferExactSingleChars(String rawComposition,
                                                  List<Candidate> candidates) {
        String query = PinyinEngine.normalize(rawComposition);
        if (query.isEmpty() || candidates.isEmpty()) return candidates;
        // sh → 是 still continues the syllable. Only sink 爱 while typing "a".
        boolean partial = PinyinEngine.isPartialSyllable(query);
        List<Candidate> exactSingles = new ArrayList<>();
        List<Candidate> longerReadingSingles = new ArrayList<>();
        List<Candidate> rest = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String reading = PinyinEngine.normalize(candidate.pinyin());
            if (candidate.text().length() == 1 && !reading.isEmpty() && reading.equals(query)) {
                exactSingles.add(candidate);
            } else if (!partial && candidate.text().length() == 1 && !reading.isEmpty()
                    && reading.startsWith(query) && !reading.equals(query)) {
                // 爱(ai) while composing "a"
                longerReadingSingles.add(candidate);
            } else {
                rest.add(candidate);
            }
        }
        if (exactSingles.isEmpty() && longerReadingSingles.isEmpty()) return candidates;
        List<Candidate> ordered = new ArrayList<>(candidates.size());
        ordered.addAll(exactSingles);
        ordered.addAll(rest);
        ordered.addAll(longerReadingSingles);
        return ordered;
    }

    /**
     * For a 简拼 query, exact-initial words lead, in the local dictionary's
     * order (zg → 中国, then 这个). Longer completions (中国人) follow.
     * Merged objects are kept, so a Rime hit stays a Rime hit.
     */
    static List<Candidate> preferExactAbbreviations(String rawComposition,
                                                    List<Candidate> javaOrder,
                                                    List<Candidate> merged) {
        String query = PinyinEngine.normalize(rawComposition);
        if (!PinyinEngine.isAbbreviationQuery(query) || merged.isEmpty()) return merged;
        List<String> exactOrder = new ArrayList<>();
        Set<String> exactTexts = new LinkedHashSet<>();
        for (Candidate candidate : javaOrder) {
            if (query.equals(PinyinEngine.initialsOf(candidate.pinyin()))
                    && exactTexts.add(candidate.text())) {
                exactOrder.add(candidate.text());
            }
        }
        Map<String, Candidate> mergedByText = new LinkedHashMap<>();
        List<Candidate> mergedExact = new ArrayList<>();
        for (Candidate candidate : merged) {
            mergedByText.putIfAbsent(candidate.text(), candidate);
            if (query.equals(PinyinEngine.initialsOf(candidate.pinyin()))) {
                mergedExact.add(candidate);
            }
        }
        List<Candidate> ordered = new ArrayList<>(merged.size());
        Set<String> placed = new LinkedHashSet<>();
        for (String text : exactOrder) {
            Candidate candidate = mergedByText.get(text);
            if (candidate != null && placed.add(text)) ordered.add(candidate);
        }
        for (Candidate candidate : mergedExact) {
            if (placed.add(candidate.text())) ordered.add(candidate);
        }
        for (Candidate candidate : merged) {
            if (placed.add(candidate.text())) ordered.add(candidate);
        }
        return ordered;
    }

    /**
     * Unfinished syllable: single characters that continue it lead
     * (sh → 是, zhon → 中), ahead of phrases Rime already offered.
     */
    static List<Candidate> preferPartialSyllableChars(String rawComposition,
                                                      List<Candidate> javaOrder,
                                                      List<Candidate> merged) {
        String query = PinyinEngine.normalize(rawComposition);
        if (!PinyinEngine.isPartialSyllable(query) || merged.isEmpty()) return merged;
        List<String> order = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Candidate candidate : javaOrder) {
            if (candidate.text().length() != 1) continue;
            String reading = PinyinEngine.normalize(candidate.pinyin());
            if (reading.startsWith(query) && seen.add(candidate.text())) {
                order.add(candidate.text());
            }
        }
        return placeFirst(order, merged);
    }

    /**
     * A two-letter syllable that is also a 简拼 (ng → 那个) keeps those
     * words next to the character. Far-below hits such as de → 第二 stay put.
     */
    static List<Candidate> preferNearSyllableInitials(String rawComposition,
                                                      List<Candidate> javaOrder,
                                                      List<Candidate> merged) {
        String query = PinyinEngine.normalize(rawComposition);
        if (!PinyinEngine.isSingleSyllable(query) || query.length() != 2 || merged.isEmpty()) {
            return merged;
        }
        List<String> order = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int index = 0;
        for (Candidate candidate : javaOrder) {
            if (index++ >= 20) break;
            if (candidate.text().length() <= 1) continue;
            if (query.equals(PinyinEngine.initialsOf(candidate.pinyin()))
                    && seen.add(candidate.text())) {
                order.add(candidate.text());
            }
        }
        if (order.isEmpty()) return merged;
        List<Candidate> exactSingles = new ArrayList<>();
        List<Candidate> rest = new ArrayList<>();
        Map<String, Candidate> byText = new LinkedHashMap<>();
        for (Candidate candidate : merged) {
            byText.putIfAbsent(candidate.text(), candidate);
            String reading = PinyinEngine.normalize(candidate.pinyin());
            if (candidate.text().length() == 1 && reading.equals(query)) {
                exactSingles.add(candidate);
            } else if (!seen.contains(candidate.text())) {
                rest.add(candidate);
            }
        }
        List<Candidate> ordered = new ArrayList<>(merged.size());
        Set<String> placed = new LinkedHashSet<>();
        for (Candidate candidate : exactSingles) {
            if (placed.add(candidate.text())) ordered.add(candidate);
        }
        for (String text : order) {
            Candidate candidate = byText.get(text);
            if (candidate != null && placed.add(text)) ordered.add(candidate);
        }
        for (Candidate candidate : rest) {
            if (placed.add(candidate.text())) ordered.add(candidate);
        }
        return ordered;
    }

    /**
     * An exact component spelling leads. A Rime object is kept when the same
     * character is already in the merged row.
     */
    static List<Candidate> preferExactAssembly(List<Candidate> exact, List<Candidate> merged) {
        if (exact.isEmpty()) return merged;
        Map<String, Candidate> byText = new LinkedHashMap<>();
        for (Candidate candidate : merged) byText.putIfAbsent(candidate.text(), candidate);
        List<Candidate> ordered = new ArrayList<>();
        Set<String> placed = new LinkedHashSet<>();
        for (Candidate candidate : exact) {
            if (!placed.add(candidate.text())) continue;
            Candidate existing = byText.get(candidate.text());
            ordered.add(existing == null ? candidate : existing);
        }
        for (Candidate candidate : merged) {
            if (placed.add(candidate.text())) ordered.add(candidate);
        }
        return ordered;
    }

    private static List<Candidate> placeFirst(List<String> headTexts, List<Candidate> merged) {
        if (headTexts.isEmpty()) return merged;
        Map<String, Candidate> byText = new LinkedHashMap<>();
        for (Candidate candidate : merged) {
            byText.putIfAbsent(candidate.text(), candidate);
        }
        List<Candidate> ordered = new ArrayList<>(merged.size());
        Set<String> placed = new LinkedHashSet<>();
        for (String text : headTexts) {
            Candidate candidate = byText.get(text);
            if (candidate != null && placed.add(text)) ordered.add(candidate);
        }
        for (Candidate candidate : merged) {
            if (placed.add(candidate.text())) ordered.add(candidate);
        }
        return ordered;
    }

    @SafeVarargs
    static List<Candidate> mergeCandidates(List<Candidate>... groups) {
        Map<String, Candidate> unique = new LinkedHashMap<>();
        for (List<Candidate> group : groups) {
            for (Candidate candidate : group) {
                Candidate existing = unique.get(candidate.text());
                if (existing == null) {
                    unique.put(candidate.text(), candidate);
                } else if (existing.source() == Candidate.Source.RIME
                        && candidate.source() == Candidate.Source.PINYIN) {
                    unique.put(candidate.text(), new Candidate(existing.text(),
                            candidate.pinyin(), existing.score(), existing.source(),
                            existing.annotation(), existing.consumedInputLength()));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    static String remainingPinyin(String rawComposition, Candidate candidate) {
        if (candidate.source() != Candidate.Source.PINYIN
                && candidate.source() != Candidate.Source.RIME
                && candidate.source() != Candidate.Source.USER) {
            return "";
        }
        String input = PinyinEngine.normalize(rawComposition);
        int nativeConsumed = candidate.consumedInputLength();
        if (nativeConsumed > 0) {
            return nativeConsumed < input.length() ? input.substring(nativeConsumed) : "";
        }
        String consumed = PinyinEngine.normalize(candidate.pinyin());
        if (!consumed.isEmpty() && consumed.length() < input.length()
                && input.startsWith(consumed)) {
            return input.substring(consumed.length());
        }
        return "";
    }

    private void commitCandidate(Candidate candidate) {
        String remaining = mode == SimpleKeyboardView.InputMode.PINYIN
                ? remainingPinyin(composition.toString(), candidate)
                : "";
        if ((candidate.source() == Candidate.Source.RIME
                || candidate.source() == Candidate.Source.RIME_ASSEMBLY)
                && rimeEngine != null) {
            rimeEngine.recordSelection(candidate);
        }
        if (learningAllowed()) userLexicon.record(candidate, previousCommitted);
        commitDirect(candidate.text());
        previousCommitted = candidate.text();
        composition.setLength(0);
        composition.append(remaining);
        if (!remaining.isEmpty()) {
            refreshCandidates();
        } else {
            candidates = learningAllowed()
                    ? userLexicon.nextAfter(previousCommitted) : java.util.List.of();
            if (keyboard != null) keyboard.showCandidates("", candidates);
        }
        if (candidate.source() == Candidate.Source.HANDWRITING && keyboard != null) {
            keyboard.clearHandwriting();
        }
    }

    private void commitRaw(String text) {
        commitDirect(text);
        if (!text.isBlank()) previousCommitted = text;
        composition.setLength(0);
        candidates = java.util.List.of();
        if (keyboard != null) keyboard.showCandidates("", candidates);
    }

    private boolean learningAllowed() {
        return !privateInput && ImePreferences.enabled(preferences, ImePreferences.LEARNING);
    }

    static boolean isPrivateInput(int inputType) {
        int inputClass = inputType & InputType.TYPE_MASK_CLASS;
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return inputClass == InputType.TYPE_CLASS_NUMBER
                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    static boolean shouldPerformEditorAction(int imeOptions) {
        int action = imeOptions & EditorInfo.IME_MASK_ACTION;
        return (imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                && action != EditorInfo.IME_ACTION_NONE
                && action != EditorInfo.IME_ACTION_UNSPECIFIED;
    }

    static boolean disablesPersonalizedLearning(int imeOptions) {
        return (imeOptions & IME_FLAG_NO_PERSONALIZED_LEARNING) != 0;
    }

    private static void deleteOneCodePoint(InputConnection connection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (connection.deleteSurroundingTextInCodePoints(1, 0)) return;
        }
        CharSequence beforeCursor = connection.getTextBeforeCursor(2, 0);
        int codeUnits = beforeCursor != null
                && beforeCursor.length() >= 2
                && Character.isSurrogatePair(
                        beforeCursor.charAt(beforeCursor.length() - 2),
                        beforeCursor.charAt(beforeCursor.length() - 1))
                ? 2 : 1;
        connection.deleteSurroundingText(codeUnits, 0);
    }

    private void commitDirect(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
    }

    private void ensureHandwritingModel() {
        if (handwritingModelReady) {
            if (keyboard != null) keyboard.showHandwritingStatus("请在下方书写汉字");
            return;
        }
        if (handwritingModelLoading) return;
        try {
            if (handwritingModel == null) {
                // The manifest disables eager initialization so devices without Google
                // services can still start the keyboard and use all other modes.
                MlKit.initialize(getApplicationContext());
                DigitalInkRecognitionModelIdentifier identifier =
                        DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hans-CN");
                if (identifier == null) {
                    if (keyboard != null) keyboard.showHandwritingStatus("不支持中文手写模型");
                    return;
                }
                handwritingModel = DigitalInkRecognitionModel.builder(identifier).build();
                handwritingRecognizer = DigitalInkRecognition.getClient(
                        DigitalInkRecognizerOptions.builder(handwritingModel).build());
            }
            handwritingModelLoading = true;
            if (keyboard != null) keyboard.showHandwritingStatus("正在检查中文手写模型…");
            RemoteModelManager manager = RemoteModelManager.getInstance();
            manager.isModelDownloaded(handwritingModel)
                    .addOnSuccessListener(downloaded -> {
                        if (destroyed) return;
                        if (downloaded) {
                            handwritingModelLoading = false;
                            handwritingModelReady = true;
                            if (keyboard != null) keyboard.showHandwritingStatus("请在下方书写汉字");
                        } else {
                            if (keyboard != null)
                                keyboard.showHandwritingStatus("首次使用：正在下载中文手写模型…");
                            manager.download(handwritingModel, new DownloadConditions.Builder().build())
                                    .addOnSuccessListener(unused -> {
                                        if (destroyed) return;
                                        handwritingModelLoading = false;
                                        handwritingModelReady = true;
                                        if (keyboard != null)
                                            keyboard.showHandwritingStatus("模型就绪，请书写汉字");
                                    })
                                    .addOnFailureListener(error -> showHandwritingModelError());
                        }
                    })
                    .addOnFailureListener(error -> showHandwritingModelError());
        } catch (MlKitException | RuntimeException | LinkageError error) {
            showHandwritingModelError();
        }
    }

    private void showHandwritingModelError() {
        handwritingModelLoading = false;
        if (!destroyed && keyboard != null)
            keyboard.showHandwritingStatus("手写模型暂不可用，请检查网络后重试");
    }

}
