package com.example.hanziime;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class SimpleKeyboardView extends LinearLayout {
    public interface Listener {
        void onLetter(String text);
        void onCandidate(Candidate candidate);
        void onSpace();
        void onDelete();
        void onEnter();
    }

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private Listener listener;
    private final TextView compositionView;
    private final LinearLayout candidateRow;

    public SimpleKeyboardView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(6), dp(4), dp(8));
        setBackgroundColor(Color.rgb(220, 226, 230));
        compositionView = new TextView(context);
        candidateRow = new LinearLayout(context);
        buildKeyboard();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void buildKeyboard() {
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

        for (String keys : ROWS) {
            LinearLayout row = createRow();
            for (int i = 0; i < keys.length(); i++) {
                String letter = String.valueOf(keys.charAt(i));
                Button key = createKey(letter);
                key.setOnClickListener(view -> {
                    if (listener != null) listener.onLetter(letter);
                });
                row.addView(key, weightedKey());
            }
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, dp(48)));
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
        addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, dp(50)));
    }

    public void showCandidates(String composition, java.util.List<Candidate> candidates) {
        compositionView.setText(composition.isEmpty() ? "中文 · 26键" : composition);
        candidateRow.removeAllViews();
        for (Candidate candidate : candidates) {
            Button button = createKey(candidate.text() + "  " + candidate.pinyin());
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
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(7));
        key.setBackground(background);
        return key;
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
