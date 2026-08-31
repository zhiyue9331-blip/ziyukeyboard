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
        void onDirectText(String text);
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
        setPadding(dp(3), dp(3), dp(3), dp(4));
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
        addModeButton(toolbar, "拆字", InputMode.ASSEMBLY);
        addModeButton(toolbar, "手写", InputMode.HANDWRITING);
        addModeButton(toolbar, "中/英", InputMode.ENGLISH);
        addModeButton(toolbar, "123", InputMode.NUMBER);
        addModeButton(toolbar, "符号", InputMode.SYMBOL);

        compositionView.setText("中文 · 26键");
        compositionView.setTextColor(accentColor());
        compositionView.setTextSize(14);
        compositionView.setGravity(Gravity.CENTER_VERTICAL);
        compositionView.setPadding(dp(8), 0, dp(8), 0);

        candidateRow.setOrientation(HORIZONTAL);
        HorizontalScrollView scroller = new HorizontalScrollView(getContext());
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(candidateRow, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        LinearLayout candidateBar = createRow();
        candidateBar.setGravity(Gravity.CENTER_VERTICAL);
        candidateBar.addView(compositionView, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        candidateBar.addView(scroller, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        addView(candidateBar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(46)));
        addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(34)));

        keyboardArea.setOrientation(VERTICAL);
        handwritingArea.setOrientation(VERTICAL);

        rebuildKeyArea();
        addView(keyboardArea, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        handwritingArea.addView(handwritingPad,
                new LayoutParams(LayoutParams.MATCH_PARENT, dp(138)));
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
                new LayoutParams(LayoutParams.MATCH_PARENT, dp(44)));
        handwritingArea.setVisibility(View.GONE);
        addView(handwritingArea,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void rebuildKeyArea() {
        keyboardArea.removeAllViews();
        String[] rows = switch (mode) {
            case NUMBER -> new String[]{"123", "456", "789", "+-0.="};
            case SYMBOL -> new String[]{"！？。，", "；：、…", "（）【】", "《》“”", "@#%&"};
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
            keyboardArea.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(43)));
        }

        LinearLayout actions = createRow();
        if (mode != InputMode.NUMBER && mode != InputMode.SYMBOL) {
            String leftMark = mode == InputMode.ENGLISH ? "," : "，";
            Button comma = createKey(leftMark);
            comma.setOnClickListener(view -> {
                if (listener != null) listener.onDirectText(leftMark);
            });
            actions.addView(comma, weightedKey(0.72f));
        }
        Button delete = createKey("删除");
        delete.setOnClickListener(view -> {
            if (listener != null) listener.onDelete();
        });
        actions.addView(delete, weightedKey(1.08f));

        Button space = createKey("空格");
        space.setOnClickListener(view -> {
            if (listener != null) listener.onSpace();
        });
        actions.addView(space, weightedKey(3f));

        if (mode != InputMode.NUMBER && mode != InputMode.SYMBOL) {
            String rightMark = mode == InputMode.ENGLISH ? "." : "。";
            Button period = createKey(rightMark);
            period.setOnClickListener(view -> {
                if (listener != null) listener.onDirectText(rightMark);
            });
            actions.addView(period, weightedKey(0.72f));
        }

        Button enter = createKey("回车");
        enter.setOnClickListener(view -> {
            if (listener != null) listener.onEnter();
        });
        actions.addView(enter, weightedKey(1.08f));
        keyboardArea.addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, dp(45)));
    }

    public void showCandidates(String composition, java.util.List<Candidate> candidates) {
        compositionView.setText(composition.isEmpty() ? modeTitle() : composition);
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
                    LayoutParams.WRAP_CONTENT, dp(40));
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            button.setMinWidth(dp(68));
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

    private String modeTitle() {
        return switch (mode) {
            case ASSEMBLY -> "拆字输入";
            case HANDWRITING -> "手写输入";
            case ENGLISH -> "英文 · 26键";
            case NUMBER -> "数字键盘";
            case SYMBOL -> "常用符号";
            default -> "中文 · 26键";
        };
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
        key.setTextColor(keyTextColor());
        key.setAllCaps(false);
        key.setPadding(0, 0, 0, 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(keyColor());
        background.setCornerRadius(dp(cornerRadius()));
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

    private int accentColor() {
        if ("custom".equals(preferences.getString(ImePreferences.THEME, "paper"))) {
            return safeColor(ImePreferences.CUSTOM_ACCENT_COLOR, "#0F766E");
        }
        return Color.rgb(15, 118, 110);
    }

    private int keyTextColor() {
        if ("custom".equals(preferences.getString(ImePreferences.THEME, "paper"))) {
            return safeColor(ImePreferences.CUSTOM_TEXT_COLOR, "#17212B");
        }
        return Color.rgb(23, 33, 43);
    }

    private int cornerRadius() {
        if ("custom".equals(preferences.getString(ImePreferences.THEME, "paper"))) {
            return Math.max(0, Math.min(24,
                    preferences.getInt(ImePreferences.CUSTOM_CORNER_RADIUS, 7)));
        }
        return 7;
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
        LayoutParams params = new LayoutParams(0, dp(39), weight);
        params.setMargins(dp(1), dp(1), dp(1), dp(1));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
