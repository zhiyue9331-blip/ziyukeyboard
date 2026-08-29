package com.example.hanziime;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public final class HanziInputMethodService extends InputMethodService
        implements SimpleKeyboardView.Listener {
    private final StringBuilder composition = new StringBuilder();
    private PinyinEngine pinyinEngine;
    private AssemblyEngine assemblyEngine;
    private SimpleKeyboardView keyboard;
    private java.util.List<Candidate> candidates = java.util.List.of();
    private SimpleKeyboardView.InputMode mode = SimpleKeyboardView.InputMode.PINYIN;

    @Override
    public void onCreate() {
        super.onCreate();
        pinyinEngine = new PinyinEngine(this);
        assemblyEngine = new AssemblyEngine(this);
    }

    @Override
    public View onCreateInputView() {
        keyboard = new SimpleKeyboardView(this);
        keyboard.setListener(this);
        return keyboard;
    }

    @Override
    public void onLetter(String text) {
        if (mode == SimpleKeyboardView.InputMode.ENGLISH) {
            commitDirect(text);
            return;
        }
        composition.append(text);
        refreshCandidates();
    }

    @Override
    public void onModeSelected(SimpleKeyboardView.InputMode newMode) {
        if (!composition.isEmpty()) {
            commitRaw(composition.toString());
        }
        mode = newMode;
        if (keyboard != null) keyboard.setMode(newMode);
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
        if (!composition.isEmpty()) {
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
        if (!composition.isEmpty()) {
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
    }

    private void refreshCandidates() {
        if (mode == SimpleKeyboardView.InputMode.ASSEMBLY) {
            candidates = assemblyEngine.search(composition.toString());
        } else {
            candidates = pinyinEngine.search(composition.toString(), true);
        }
        if (keyboard != null) keyboard.showCandidates(composition.toString(), candidates);
    }

    private void commitCandidate(Candidate candidate) {
        commitRaw(candidate.text());
    }

    private void commitRaw(String text) {
        commitDirect(text);
        composition.setLength(0);
        candidates = java.util.List.of();
        if (keyboard != null) keyboard.showCandidates("", candidates);
    }

    private void commitDirect(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
    }
}
