package com.example.hanziime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int SIDE_PADDING_DP = 24;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createSetupView());
    }

    private View createSetupView() {
        int padding = dp(SIDE_PADDING_DP);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, dp(48), padding, padding);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(getColor(R.color.paper));

        TextView title = new TextView(this);
        title.setText(R.string.setup_title);
        title.setTextColor(getColor(R.color.ink));
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(dp(16)));

        TextView description = bodyText(R.string.setup_description);
        root.addView(description, matchWrap(dp(28)));

        Button enableButton = actionButton(R.string.enable_ime);
        enableButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(enableButton, matchWrap(dp(12)));

        Button selectButton = actionButton(R.string.select_ime);
        selectButton.setOnClickListener(view -> {
            Object service = getSystemService(INPUT_METHOD_SERVICE);
            if (service instanceof android.view.inputmethod.InputMethodManager manager) {
                manager.showInputMethodPicker();
            }
        });
        root.addView(selectButton, matchWrap(dp(28)));

        TextView note = bodyText(R.string.setup_note);
        note.setTextSize(14);
        root.addView(note, matchWrap(0));
        return root;
    }

    private TextView bodyText(int textResource) {
        TextView view = new TextView(this);
        view.setText(textResource);
        view.setTextColor(Color.rgb(70, 82, 92));
        view.setTextSize(16);
        view.setLineSpacing(0, 1.25f);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button actionButton(int textResource) {
        Button button = new Button(this);
        button.setText(textResource);
        button.setTextSize(16);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
