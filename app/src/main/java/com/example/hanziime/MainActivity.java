package com.example.hanziime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int SIDE_PADDING_DP = 24;
    private static final int REQUEST_IMPORT_SKIN = 701;
    private static final int REQUEST_EXPORT_SKIN = 702;
    private static final int MAX_SKIN_FILE_CHARS = 64 * 1024;

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

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText panelColor = new EditText(this);
        panelColor.setHint("面板色 #DCE2E6");
        panelColor.setSingleLine(true);
        colorRow.addView(panelColor, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        EditText keyColor = new EditText(this);
        keyColor.setHint("按键色 #FFFFFF");
        keyColor.setSingleLine(true);
        colorRow.addView(keyColor, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(colorRow, matchWrap(dp(4)));

        Button applyCustomSkin = actionButton(0);
        applyCustomSkin.setText("应用自定义颜色皮肤");
        applyCustomSkin.setOnClickListener(view -> {
            String panel = normalizeColor(panelColor.getText().toString(), "#DCE2E6");
            String key = normalizeColor(keyColor.getText().toString(), "#FFFFFF");
            if (panel == null || key == null) {
                Toast.makeText(this, "颜色格式应为 #RRGGBB", Toast.LENGTH_SHORT).show();
                return;
            }
            ImePreferences.get(this).edit()
                    .putString(ImePreferences.THEME, "custom")
                    .putString(ImePreferences.CUSTOM_SKIN_NAME, "自定义颜色")
                    .putString(ImePreferences.CUSTOM_PANEL_COLOR, panel)
                    .putString(ImePreferences.CUSTOM_KEY_COLOR, key)
                    .putString(ImePreferences.CUSTOM_TEXT_COLOR, "#17212B")
                    .putString(ImePreferences.CUSTOM_ACCENT_COLOR, "#0F766E")
                    .putInt(ImePreferences.CUSTOM_CORNER_RADIUS, 7)
                    .apply();
            updateThemeLabel(theme);
            Toast.makeText(this, "自定义皮肤已保存，重新打开键盘后生效", Toast.LENGTH_SHORT).show();
        });
        root.addView(applyCustomSkin, matchWrap(dp(14)));

        LinearLayout skinFileRow = new LinearLayout(this);
        skinFileRow.setOrientation(LinearLayout.HORIZONTAL);
        Button importSkin = actionButton(0);
        importSkin.setText("导入皮肤包");
        importSkin.setOnClickListener(view -> openSkinFile());
        skinFileRow.addView(importSkin, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button exportSkin = actionButton(0);
        exportSkin.setText("导出当前皮肤");
        exportSkin.setOnClickListener(view -> createSkinFile());
        skinFileRow.addView(exportSkin, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(skinFileRow, matchWrap(dp(16)));

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
        clearLearning.setOnClickListener(view -> new AlertDialog.Builder(this)
                .setTitle(R.string.clear_learning_title)
                .setMessage(R.string.clear_learning_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear_learning_confirm, (dialog, which) -> {
                    new UserLexiconStore(this).clearLearning();
                    Toast.makeText(this, R.string.clear_learning_done, Toast.LENGTH_SHORT).show();
                })
                .show());
        root.addView(clearLearning, matchWrap(dp(24)));
    }

    private void openSkinFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_SKIN);
    }

    private void createSkinFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "字语输入法皮肤.json");
        startActivityForResult(intent, REQUEST_EXPORT_SKIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQUEST_IMPORT_SKIN) {
                importSkin(uri);
                Toast.makeText(this, "皮肤导入成功，重新打开键盘后生效", Toast.LENGTH_LONG).show();
                setContentView(createSetupView());
            } else if (requestCode == REQUEST_EXPORT_SKIN) {
                exportSkin(uri);
                Toast.makeText(this, "皮肤文件已导出", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException | JSONException | IllegalArgumentException error) {
            Toast.makeText(this, "皮肤文件无效：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void importSkin(Uri uri) throws IOException, JSONException {
        JSONObject skin;
        try (InputStream input = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     requireStream(input), StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (json.length() + line.length() > MAX_SKIN_FILE_CHARS) {
                    throw new IOException("皮肤文件过大");
                }
                json.append(line);
            }
            skin = new JSONObject(json.toString());
        }
        if (!"ziyu-ime-skin".equals(skin.optString("format"))) {
            throw new JSONException("不是字语输入法皮肤包");
        }
        if (skin.optInt("version", -1) != 1) {
            throw new JSONException("不支持的皮肤包版本");
        }
        String panel = validatedSkinColor(skin, "panelColor", "#DCE2E6");
        String key = validatedSkinColor(skin, "keyColor", "#FFFFFF");
        String text = validatedSkinColor(skin, "textColor", "#17212B");
        String accent = validatedSkinColor(skin, "accentColor", "#0F766E");
        int radius = Math.max(0, Math.min(24, skin.optInt("cornerRadius", 7)));
        String name = skin.optString("name", "导入皮肤").trim();
        if (name.isEmpty()) name = "导入皮肤";
        if (name.codePointCount(0, name.length()) > 40) {
            name = name.substring(0, name.offsetByCodePoints(0, 40));
        }
        ImePreferences.get(this).edit()
                .putString(ImePreferences.THEME, "custom")
                .putString(ImePreferences.CUSTOM_SKIN_NAME, name)
                .putString(ImePreferences.CUSTOM_PANEL_COLOR, panel)
                .putString(ImePreferences.CUSTOM_KEY_COLOR, key)
                .putString(ImePreferences.CUSTOM_TEXT_COLOR, text)
                .putString(ImePreferences.CUSTOM_ACCENT_COLOR, accent)
                .putInt(ImePreferences.CUSTOM_CORNER_RADIUS, radius)
                .apply();
    }

    private void exportSkin(Uri uri) throws IOException, JSONException {
        SharedPreferences preferences = ImePreferences.get(this);
        JSONObject skin = new JSONObject();
        skin.put("format", "ziyu-ime-skin");
        skin.put("version", 1);
        skin.put("name", preferences.getString(ImePreferences.CUSTOM_SKIN_NAME, "我的皮肤"));
        skin.put("panelColor", preferences.getString(ImePreferences.CUSTOM_PANEL_COLOR, "#DCE2E6"));
        skin.put("keyColor", preferences.getString(ImePreferences.CUSTOM_KEY_COLOR, "#FFFFFF"));
        skin.put("textColor", preferences.getString(ImePreferences.CUSTOM_TEXT_COLOR, "#17212B"));
        skin.put("accentColor", preferences.getString(ImePreferences.CUSTOM_ACCENT_COLOR, "#0F766E"));
        skin.put("cornerRadius", preferences.getInt(ImePreferences.CUSTOM_CORNER_RADIUS, 7));
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            requireStream(output).write(skin.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private String validatedSkinColor(JSONObject skin, String key, String fallback)
            throws JSONException {
        String value = skin.optString(key, fallback);
        String normalized = normalizeColor(value, fallback);
        if (normalized == null) throw new JSONException(key + " 不是有效颜色");
        return normalized;
    }

    private static <T> T requireStream(T stream) throws IOException {
        if (stream == null) throw new IOException("无法读取或写入所选文件");
        return stream;
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
            case "custom" -> ImePreferences.get(this).getString(
                    ImePreferences.CUSTOM_SKIN_NAME, "自定义");
            default -> "纸白";
        };
        button.setText(getString(R.string.theme_button_label, name));
    }

    private String normalizeColor(String raw, String fallback) {
        String value = raw.trim().isEmpty() ? fallback : raw.trim();
        if (!value.startsWith("#")) value = "#" + value;
        if (!value.matches("#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) return null;
        try {
            Color.parseColor(value);
            return value.toUpperCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
