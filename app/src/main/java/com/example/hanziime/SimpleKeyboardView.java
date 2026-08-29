package com.example.hanziime;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.mlkit.vision.digitalink.recognition.Ink;

public final class SimpleKeyboardView extends LinearLayout {
    public enum InputMode { PINYIN, ASSEMBLY, HANDWRITING, ENGLISH, NUMBER, SYMBOL }

    public interface Listener {
        void onLetter(String text);
        void onCandidate(Candidate candidate);
        void onSpace();
        void onDelete();
        void onEnter();
        void onModeSelected(InputMode mode);
        void onRecognizeHandwriting(Ink ink);
    }

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private Listener listener;
    private final TextView compositionView;
    private final LinearLayout candidateRow;
    private final LinearLayout keyboardArea;
    private final LinearLayout handwritingArea;
    private final HandwritingPad handwritingPad;
    private InputMode mode = InputMode.PINYIN;
    private final SharedPreferences preferences;

    public SimpleKeyboardView(Context context) {
        super(context);
        preferences = ImePreferences.get(context);
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(6), dp(4), dp(8));
        setBackgroundColor(panelColor());
        compositionView = new TextView(context);
        candidateRow = new LinearLayout(context);
        keyboardArea = new LinearLayout(context);
        handwritingArea = new LinearLayout(context);
        handwritingPad = new HandwritingPad(context);
        buildKeyboard();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void buildKeyboard() {
        LinearLayout toolbar = createRow();
        addModeButton(toolbar, "拼音", InputMode.PINYIN);
        addModeButton(toolbar, "拼字", InputMode.ASSEMBLY);
        addModeButton(toolbar, "手写", InputMode.HANDWRITING);
        addModeButton(toolbar, "英文", InputMode.ENGLISH);
        addModeButton(toolbar, "123", InputMode.NUMBER);
        addModeButton(toolbar, "符号", InputMode.SYMBOL);
        addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(38)));

        compositionView.setText("中文 · 26键");
        compositionView.setTextColor(Color.rgb(45, 58, 68));
        compositionView.setTextSize(15);
        compositionView.setGravity(Gravity.CENTER_VERTICAL);
        compositionView.setPadding(dp(12), 0, 0, 0);
        addView(compositionView, new LayoutParams(LayoutParams.MATCH_PARENT, dp(36)));

        candidateRow.setOrientation(HORIZONTAL);
        HorizontalScrollView scroller = new HorizontalScrollView(getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(candidateRow, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(scroller, new LayoutParams(LayoutParams.MATCH_PARENT, dp(54)));

        keyboardArea.setOrientation(VERTICAL);
        handwritingArea.setOrientation(VERTICAL);

        rebuildKeyArea();
        addView(keyboardArea, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        handwritingArea.addView(handwritingPad,
                new LayoutParams(LayoutParams.MATCH_PARENT, dp(150)));
        LinearLayout inkActions = createRow();
        Button clear = createKey("清空");
        clear.setOnClickListener(view -> handwritingPad.clear());
        inkActions.addView(clear, weightedKey());
        Button recognize = createKey("识别");
        recognize.setOnClickListener(view -> {
            if (listener != null && !handwritingPad.isEmpty()) {
                listener.onRecognizeHandwriting(handwritingPad.getInk());
            }
        });
        inkActions.addView(recognize, weightedKey(2f));
        Button inkDelete = createKey("删除");
        inkDelete.setOnClickListener(view -> {
            if (listener != null) listener.onDelete();
        });
        inkActions.addView(inkDelete, weightedKey());
        handwritingArea.addView(inkActions,
                new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
        handwritingArea.setVisibility(View.GONE);
        addView(handwritingArea,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void rebuildKeyArea() {
        keyboardArea.removeAllViews();
        String[] rows = switch (mode) {
            case NUMBER -> new String[]{"123", "456", "789", "0"};
            case SYMBOL -> new String[]{"，。？！", "；：、…", "（）《》", "@#%&"};
            default -> ROWS;
        };
        for (String keys : rows) {
            LinearLayout row = createRow();
            for (int i = 0; i < keys.length(); i++) {
                String letter = String.valueOf(keys.charAt(i));
                Button key = createKey(letter);
                key.setOnClickListener(view -> {
                    if (listener != null) listener.onLetter(letter);
                });
                row.addView(key, weightedKey());
            }
            keyboardArea.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
        }

        LinearLayout actions = createRow();
        Button delete = createKey("删除");
        delete.setOnClickListener(view -> {
            if (listener != null) listener.onDelete();
        });
        actions.addView(delete, weightedKey(1.2f));

        Button space = createKey("空格");
        space.setOnClickListener(view -> {
            if (listener != null) listener.onSpace();
        });
        actions.addView(space, weightedKey(3f));

        Button enter = createKey("回车");
        enter.setOnClickListener(view -> {
            if (listener != null) listener.onEnter();
        });
        actions.addView(enter, weightedKey(1.2f));
        keyboardArea.addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
    }

    public void showCandidates(String composition, java.util.List<Candidate> candidates) {
        compositionView.setText(composition.isEmpty() ? "中文 · 26键" : composition);
        candidateRow.removeAllViews();
        for (Candidate candidate : candidates) {
            String detail = candidate.annotation().isEmpty() ? "" : " 〔" + candidate.annotation() + "〕";
            Button button = createKey(candidate.text() + "  " + candidate.pinyin() + detail);
            button.setTextSize(14);
            button.setSingleLine(true);
            button.setOnClickListener(view -> {
                if (listener != null) listener.onCandidate(candidate);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, dp(48));
            params.setMargins(dp(3), dp(2), dp(3), dp(2));
            button.setMinWidth(dp(76));
            candidateRow.addView(button, params);
        }
    }

    public void setMode(InputMode mode) {
        this.mode = mode;
        boolean handwriting = mode == InputMode.HANDWRITING;
        keyboardArea.setVisibility(handwriting ? View.GONE : View.VISIBLE);
        handwritingArea.setVisibility(handwriting ? View.VISIBLE : View.GONE);
        if (!handwriting) rebuildKeyArea();
        showCandidates("", java.util.List.of());
    }

    public void clearHandwriting() {
        handwritingPad.clear();
    }

    public void showHandwritingStatus(String status) {
        compositionView.setText(status);
    }

    private void addModeButton(LinearLayout toolbar, String label, InputMode targetMode) {
        Button button = createKey(label);
        button.setTextSize(13);
        button.setOnClickListener(view -> {
            mode = targetMode;
            if (listener != null) listener.onModeSelected(targetMode);
        });
        toolbar.addView(button, weightedKey());
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private Button createKey(String label) {
        Button key = new Button(getContext());
        key.setText(label);
        key.setTextSize(16);
        key.setTextColor(Color.rgb(23, 33, 43));
        key.setAllCaps(false);
        key.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(keyColor());
        background.setCornerRadius(dp(7));
        key.setBackground(background);
        key.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (ImePreferences.enabled(preferences, ImePreferences.VIBRATION)) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                if (ImePreferences.enabled(preferences, ImePreferences.SOUND)) {
                    view.playSoundEffect(SoundEffectConstants.CLICK);
                }
            }
            return false;
        });
        return key;
    }

    private int keyColor() {
        return switch (preferences.getString(ImePreferences.THEME, "paper")) {
            case "jade" -> Color.rgb(218, 241, 235);
            case "night" -> Color.rgb(184, 198, 209);
            case "custom" -> safeColor(ImePreferences.CUSTOM_KEY_COLOR, "#FFFFFF");
            default -> Color.WHITE;
        };
    }

    private int panelColor() {
        return switch (preferences.getString(ImePreferences.THEME, "paper")) {
            case "jade" -> Color.rgb(176, 211, 202);
            case "night" -> Color.rgb(50, 63, 74);
            case "custom" -> safeColor(ImePreferences.CUSTOM_PANEL_COLOR, "#DCE2E6");
            default -> Color.rgb(220, 226, 230);
        };
    }

    private int safeColor(String key, String fallback) {
        try {
            return Color.parseColor(preferences.getString(key, fallback));
        } catch (IllegalArgumentException ignored) {
            return Color.parseColor(fallback);
        }
    }

    private LayoutParams weightedKey() {
        return weightedKey(1f);
    }

    private LayoutParams weightedKey(float weight) {
        LayoutParams params = new LayoutParams(0, dp(42), weight);
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
