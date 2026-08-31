package com.example.hanziime;

import android.inputmethodservice.InputMethodService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HanziInputMethodService extends InputMethodService
        implements SimpleKeyboardView.Listener {
    private final StringBuilder composition = new StringBuilder();
    private volatile PinyinEngine pinyinEngine;
    private volatile AssemblyEngine assemblyEngine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ExecutorService dictionaryLoader;
    private boolean destroyed;
    private SimpleKeyboardView keyboard;
    private java.util.List<Candidate> candidates = java.util.List.of();
    private SimpleKeyboardView.InputMode mode = SimpleKeyboardView.InputMode.PINYIN;
    private DigitalInkRecognitionModel handwritingModel;
    private DigitalInkRecognizer handwritingRecognizer;
    private boolean handwritingModelReady;
    private SharedPreferences preferences;
    private UserLexiconStore userLexicon;
    private boolean privateInput;
    private String previousCommitted = "";

    @Override
    public void onCreate() {
        super.onCreate();
        // Keep service startup fast. Android may abandon an IME which blocks while being bound.
        pinyinEngine = new PinyinEngine(this, false);
        assemblyEngine = new AssemblyEngine(this, false);
        preferences = ImePreferences.get(this);
        userLexicon = new UserLexiconStore(this);
        initializeHandwritingRecognizer();
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
                    if (composition.length() > 0) refreshCandidates();
                });
            } catch (RuntimeException ignored) {
                // The built-in core dictionaries remain usable if an expanded asset is damaged.
            }
        });
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (dictionaryLoader != null) dictionaryLoader.shutdownNow();
        if (handwritingRecognizer != null) handwritingRecognizer.close();
        super.onDestroy();
    }

    @Override
    public View onCreateInputView() {
        keyboard = new SimpleKeyboardView(this);
        keyboard.setListener(this);
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
            if (keyboard != null) keyboard.showHandwritingStatus("中文手写模型正在准备，请稍后");
            ensureHandwritingModel();
            return;
        }
        if (keyboard != null) keyboard.showHandwritingStatus("正在识别…");
        handwritingRecognizer.recognize(ink)
                .addOnSuccessListener(result -> {
                    ListBuilder builder = new ListBuilder();
                    int count = Math.min(12, result.getCandidates().size());
                    for (int i = 0; i < count; i++) {
                        var item = result.getCandidates().get(i);
                        String text = item.getText();
                        builder.add(new Candidate(text, HanziPronunciation.of(text), 0,
                                Candidate.Source.HANDWRITING));
                    }
                    candidates = builder.values;
                    if (keyboard != null) {
                        keyboard.showCandidates("手写候选", candidates);
                    }
                })
                .addOnFailureListener(error -> {
                    if (keyboard != null) keyboard.showHandwritingStatus("识别失败，请重试");
                });
    }

    @Override
    public void onCandidate(Candidate candidate) {
        commitCandidate(candidate);
    }

    @Override
    public void onSpace() {
        if (!candidates.isEmpty()) {
            commitCandidate(candidates.get(0));
        } else {
            commitRaw(" ");
        }
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
            connection.deleteSurroundingText(1, 0);
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
        int action = info == null
                ? EditorInfo.IME_ACTION_NONE
                : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            connection.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    @Override
    public void onStartInput(android.view.inputmethod.EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        composition.setLength(0);
        candidates = java.util.List.of();
        previousCommitted = "";
        privateInput = isPrivateInput(attribute == null ? 0 : attribute.inputType);
    }

    private void refreshCandidates() {
        if (mode == SimpleKeyboardView.InputMode.ASSEMBLY) {
            candidates = assemblyEngine.search(composition.toString());
        } else {
            boolean fuzzy = ImePreferences.enabled(preferences, ImePreferences.FUZZY);
            java.util.List<Candidate> merged = new ArrayList<>(
                    pinyinEngine.search(composition.toString(), fuzzy));
            if (!privateInput) merged.addAll(0, userLexicon.customFor(composition.toString()));
            candidates = merged;
        }
        if (learningAllowed()) candidates = userLexicon.rerank(candidates);
        if (keyboard != null) keyboard.showCandidates(composition.toString(), candidates);
    }

    private void commitCandidate(Candidate candidate) {
        if (learningAllowed()) userLexicon.record(candidate, previousCommitted);
        commitDirect(candidate.text());
        previousCommitted = candidate.text();
        composition.setLength(0);
        candidates = learningAllowed() ? userLexicon.nextAfter(previousCommitted) : java.util.List.of();
        if (keyboard != null) keyboard.showCandidates("", candidates);
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

    private static boolean isPrivateInput(int inputType) {
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private void commitDirect(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
    }

    private void initializeHandwritingRecognizer() {
        try {
            DigitalInkRecognitionModelIdentifier identifier =
                    DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hans-CN");
            if (identifier == null) return;
            handwritingModel = DigitalInkRecognitionModel.builder(identifier).build();
            handwritingRecognizer = DigitalInkRecognition.getClient(
                    DigitalInkRecognizerOptions.builder(handwritingModel).build());
        } catch (MlKitException ignored) {
            handwritingModel = null;
            handwritingRecognizer = null;
        }
    }

    private void ensureHandwritingModel() {
        if (handwritingModel == null || handwritingModelReady) return;
        if (keyboard != null) keyboard.showHandwritingStatus("正在检查中文手写模型…");
        RemoteModelManager manager = RemoteModelManager.getInstance();
        manager.isModelDownloaded(handwritingModel)
                .addOnSuccessListener(downloaded -> {
                    if (downloaded) {
                        handwritingModelReady = true;
                        if (keyboard != null) keyboard.showHandwritingStatus("请在下方书写汉字");
                    } else {
                        if (keyboard != null) keyboard.showHandwritingStatus("首次使用：正在下载中文手写模型…");
                        manager.download(handwritingModel, new DownloadConditions.Builder().build())
                                .addOnSuccessListener(unused -> {
                                    handwritingModelReady = true;
                                    if (keyboard != null) keyboard.showHandwritingStatus("模型就绪，请书写汉字");
                                })
                                .addOnFailureListener(error -> {
                                    if (keyboard != null) keyboard.showHandwritingStatus("模型下载失败，请检查网络");
                                });
                    }
                })
                .addOnFailureListener(error -> {
                    if (keyboard != null) keyboard.showHandwritingStatus("无法检查手写模型");
                });
    }

    private static final class ListBuilder {
        private final java.util.List<Candidate> values = new ArrayList<>();
        private void add(Candidate value) { values.add(value); }
    }
}
