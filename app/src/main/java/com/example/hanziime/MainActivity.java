package com.example.hanziime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
        addSettings(root);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addSettings(LinearLayout root) {
        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("输入设置");
        settingsTitle.setTextSize(21);
        settingsTitle.setTextColor(getColor(R.color.ink));
        settingsTitle.setPadding(0, dp(12), 0, dp(8));
        root.addView(settingsTitle, matchWrap(0));

        root.addView(settingSwitch("拼音模糊输入", ImePreferences.FUZZY), matchWrap(0));
        root.addView(settingSwitch("本地词频与表达学习", ImePreferences.LEARNING), matchWrap(0));
        root.addView(settingSwitch("按键振动", ImePreferences.VIBRATION), matchWrap(0));
        root.addView(settingSwitch("打字音效", ImePreferences.SOUND), matchWrap(dp(10)));

        Button theme = actionButton(0);
        updateThemeLabel(theme);
        theme.setOnClickListener(view -> {
            String current = ImePreferences.get(this).getString(ImePreferences.THEME, "paper");
            String next = switch (current) {
                case "paper" -> "jade";
                case "jade" -> "night";
                default -> "paper";
            };
            ImePreferences.get(this).edit().putString(ImePreferences.THEME, next).apply();
            updateThemeLabel(theme);
            Toast.makeText(this, "重新打开键盘后皮肤生效", Toast.LENGTH_SHORT).show();
        });
        root.addView(theme, matchWrap(dp(10)));

        TextView customTitle = new TextView(this);
        customTitle.setText("添加自定义词条");
        customTitle.setTextSize(17);
        customTitle.setTextColor(getColor(R.color.ink));
        root.addView(customTitle, matchWrap(dp(6)));

        EditText customText = new EditText(this);
        customText.setHint("词语，例如：数智平台");
        customText.setSingleLine(true);
        root.addView(customText, matchWrap(dp(4)));

        EditText customPinyin = new EditText(this);
        customPinyin.setHint("拼音，例如：shuzhipingtai");
        customPinyin.setSingleLine(true);
        root.addView(customPinyin, matchWrap(dp(6)));

        Button addWord = actionButton(0);
        addWord.setText("添加到本地词库");
        addWord.setOnClickListener(view -> {
            String word = customText.getText().toString().trim();
            String pinyin = customPinyin.getText().toString().trim();
            if (word.isEmpty() || PinyinEngine.normalize(pinyin).isEmpty()) {
                Toast.makeText(this, "请填写词语和拼音", Toast.LENGTH_SHORT).show();
                return;
            }
            new UserLexiconStore(this).addCustom(word, pinyin, pinyin);
            customText.setText("");
            customPinyin.setText("");
            Toast.makeText(this, "已加入本地词库", Toast.LENGTH_SHORT).show();
        });
        root.addView(addWord, matchWrap(dp(6)));

        Button clearLearning = actionButton(0);
        clearLearning.setText("清空本地学习数据");
        clearLearning.setOnClickListener(view -> {
            new UserLexiconStore(this).clearLearning();
            Toast.makeText(this, "本地词频和自定义词条已清空", Toast.LENGTH_SHORT).show();
        });
        root.addView(clearLearning, matchWrap(dp(24)));
    }

    private Switch settingSwitch(String label, String key) {
        Switch setting = new Switch(this);
        setting.setText(label);
        setting.setTextSize(16);
        setting.setTextColor(getColor(R.color.ink));
        setting.setPadding(0, dp(5), 0, dp(5));
        setting.setChecked(ImePreferences.enabled(ImePreferences.get(this), key));
        setting.setOnCheckedChangeListener((button, checked) ->
                ImePreferences.get(this).edit().putBoolean(key, checked).apply());
        return setting;
    }

    private void updateThemeLabel(Button button) {
        String current = ImePreferences.get(this).getString(ImePreferences.THEME, "paper");
        String name = switch (current) {
            case "jade" -> "青玉";
            case "night" -> "夜色";
            default -> "纸白";
        };
        button.setText("输入法皮肤：" + name + "（点击切换）");
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
