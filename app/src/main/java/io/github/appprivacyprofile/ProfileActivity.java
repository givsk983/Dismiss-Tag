package io.github.appprivacyprofile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.icu.text.TimeZoneNames;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

public final class ProfileActivity extends Activity {
    private static final String REPOSITORY = "https://github.com/givsk983/Dismiss-Tag";
    private static final String RELEASE_API =
            "https://api.github.com/repos/givsk983/Dismiss-Tag/releases/latest";
    private static final Pattern RELEASE_VERSION = Pattern.compile("v?([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})");
    private static final String TIKTOK = "com.zhiliaoapp.musically";
    private static final String TIKTOK_ALTERNATE = "com.ss.android.ugc.trill";
    private static final String CLAUDE = "com.anthropic.claude";
    private static final String FRAMEWORK_HELP =
            "请在支持 Modern API 101 或更新版本的 LSPosed 管理器启用模块，勾选目标应用，再重新打开此配置页。";
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private ScrollView scroll;
    private LinearLayout picker;
    private LinearLayout editor;
    private LinearLayout footer;
    private LinearLayout carrierGroup;
    private Button tiktok;
    private Button claude;
    private Button other;
    private Button saveButton;
    private Button appInfo;
    private Button updateButton;
    private TextView targetName;
    private TextView targetPackage;
    private TextView status;
    private TextView frameworkStatus;
    private Switch enabled;
    private Switch maskSim;
    private Switch maskFingerprint;
    private Switch maskUsage;
    private ChoiceField carrier;
    private ChoiceField language;
    private ChoiceField timeZone;
    private String loadedPackage;
    private String deviceId = "";
    private boolean available;
    private boolean dirty;
    private boolean saved;
    private boolean warning;
    private boolean populating;
    private boolean editing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout page = column();
        page.setBackgroundColor(getColor(R.color.page));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        page.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1));
        LinearLayout content = column();
        content.setPadding(dp(18), dp(10), dp(18), dp(18));
        content.setFocusableInTouchMode(true);
        scroll.addView(content, new ScrollView.LayoutParams(MATCH, WRAP));
        setContentView(page);

        picker = column();
        add(content, picker, 0);
        buildHero(picker);
        buildSelector(picker);
        buildProjectLinks(picker);
        buildEditor(content);
        buildFooter(page);

        if (state != null && state.getString("target") != null) {
            loadedPackage = state.getString("target");
            available = state.getBoolean("available");
            deviceId = state.getString("device_id", "");
            populate(state.getBoolean("enabled"), state.getBoolean("sim"),
                    state.getString("country", ""), state.getString("language", ""),
                    state.getString("zone", ""), state.getBoolean("fingerprint"), state.getBoolean("usage"));
            dirty = state.getBoolean("dirty");
            saved = state.getBoolean("saved");
            showTarget();
            setEditing(state.getBoolean("editing", true));
            showStatus(state.getString("status", ""), state.getBoolean("warning"));
            scroll.post(() -> scroll.scrollTo(0, state.getInt("scroll")));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        profileApplication().setObserver(this::updateConnection);
    }

    @Override
    protected void onStop() {
        profileApplication().setObserver(null);
        super.onStop();
    }

    private void buildHero(LinearLayout content) {
        LinearLayout hero = column();
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{getColor(R.color.hero_top), getColor(R.color.hero_bottom)});
        background.setCornerRadius(dp(24));
        hero.setBackground(background);
        hero.setPadding(dp(18), dp(14), dp(18), dp(16));
        add(content, hero, 0);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView shield = new ImageView(this);
        shield.setImageResource(R.drawable.ic_privacy);
        shield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(shield, new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView brand = text("LSPosed 隐私模块", 13, R.color.hero_badge, true);
        LinearLayout.LayoutParams brandLayout = new LinearLayout.LayoutParams(0, WRAP, 1);
        brandLayout.setMarginStart(dp(8));
        row.addView(brand, brandLayout);
        Button help = button("设置帮助", () -> new AlertDialog.Builder(this)
                .setTitle("如何让配置生效")
                .setMessage("1. " + FRAMEWORK_HELP
                        + "\n\n2. 选择目标应用，启用配置并保存。"
                        + "\n\n3. 在系统应用信息中强行停止目标应用，再重新打开。")
                .setPositiveButton("知道了", null)
                .show());
        styleButton(help, R.color.hero_top, R.color.hero_badge, 0, 12);
        row.addView(help, new LinearLayout.LayoutParams(WRAP, WRAP));
        add(hero, row, 0);

        add(hero, heading(getString(R.string.app_name), 27, R.color.hero_text), 8);
        add(hero, text("分别设置每个应用能读取的设备和使用信息。", 14,
                R.color.hero_muted, false), 6);
    }

    private void buildSelector(LinearLayout content) {
        LinearLayout selector = card(content, 14);
        add(selector, heading("目标应用", 18, R.color.text_main), 0);
        add(selector, text("选择要编辑的应用。", 13, R.color.text_muted, false), 4);

        LinearLayout shortcuts = new LinearLayout(this);
        tiktok = button("TikTok", () -> select(TIKTOK));
        claude = button("Claude", () -> select(CLAUDE));
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, WRAP, 1);
        first.setMarginEnd(dp(8));
        shortcuts.addView(tiktok, first);
        shortcuts.addView(claude, new LinearLayout.LayoutParams(0, WRAP, 1));
        add(selector, shortcuts, 12);

        other = button("其他应用 · 输入包名", this::chooseOther);
        add(selector, other, 8);
        frameworkStatus = text("", 13, R.color.text_muted, false);
        frameworkStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(selector, frameworkStatus, 10);
        updateSelection();
    }

    private void buildProjectLinks(LinearLayout content) {
        LinearLayout project = card(content, 14);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading("关于", 18, R.color.text_main),
                new LinearLayout.LayoutParams(0, WRAP, 1));
        TextView version = text("版本 " + BuildConfig.VERSION_NAME, 12, R.color.accent, true);
        version.setBackground(rounded(R.color.selected, 12, 0));
        version.setPadding(dp(10), dp(5), dp(10), dp(5));
        header.addView(version, new LinearLayout.LayoutParams(WRAP, WRAP));
        add(project, header, 0);

        updateButton = button("检查更新", this::checkForUpdate);
        styleButton(updateButton, R.color.accent, R.color.on_accent, 0, 14);
        add(project, updateButton, 12);

        LinearLayout links = new LinearLayout(this);
        Button repository = button("GitHub 仓库", () -> openLink(REPOSITORY));
        Button channel = button("Telegram 频道", () -> openLink("https://t.me/mbazhuan"));
        styleButton(repository, R.color.field, R.color.accent, R.color.outline, 12);
        styleButton(channel, R.color.field, R.color.accent, R.color.outline, 12);
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, MATCH, 1);
        first.setMarginEnd(dp(8));
        links.addView(repository, first);
        links.addView(channel, new LinearLayout.LayoutParams(0, MATCH, 1));
        add(project, links, 8);
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this).setMessage("没有可打开此链接的应用。")
                    .setPositiveButton("知道了", null).show();
        }
    }

    private void checkForUpdate() {
        Button update = updateButton;
        update.setEnabled(false);
        update.setText("正在检查…");
        styleButton(update, R.color.field, R.color.text_muted, R.color.outline, 14);
        new Thread(() -> {
            String tag = null;
            String message;
            boolean newer = false;
            try {
                tag = latestReleaseTag();
                newer = tag != null && compareVersions(tag, BuildConfig.VERSION_NAME) > 0;
                message = tag == null ? "GitHub 暂无可用的正式版本。"
                        : newer ? "发现新版本 " + tag + "。"
                        : "当前版本 " + BuildConfig.VERSION_NAME + " 已是最新版本。";
            } catch (Exception e) {
                message = "检查更新失败，请稍后重试或前往 GitHub 查看。";
            }
            String result = message;
            boolean hasUpdate = newer;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                update.setEnabled(true);
                update.setText("检查更新");
                styleButton(update, R.color.accent, R.color.on_accent, 0, 14);
                AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                        .setTitle("检查更新").setMessage(result).setPositiveButton("知道了", null);
                if (hasUpdate) {
                    dialog.setNeutralButton("查看发布页", (view, which) -> openLink(REPOSITORY + "/releases/latest"));
                }
                dialog.show();
            });
        }, "release-check").start();
    }

    private static String latestReleaseTag() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        try {
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "DismissTag/" + BuildConfig.VERSION_NAME);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return null;
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("GitHub HTTP " + status);
            try (java.util.Scanner response = new java.util.Scanner(
                    connection.getInputStream(), StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
                return new JSONObject(response.next()).getString("tag_name");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static int compareVersions(String first, String second) {
        var left = RELEASE_VERSION.matcher(first);
        var right = RELEASE_VERSION.matcher(second);
        if (!left.matches() || !right.matches()) throw new IllegalArgumentException("Invalid release version");
        for (int part = 1; part <= 3; part++) {
            int order = Integer.compare(Integer.parseInt(left.group(part)), Integer.parseInt(right.group(part)));
            if (order != 0) return order;
        }
        return 0;
    }

    private void buildEditor(LinearLayout content) {
        editor = column();
        editor.setVisibility(View.GONE);
        add(content, editor, 0);

        LinearLayout profile = card(editor, 0);
        targetName = heading("", 21, R.color.text_main);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(targetName, new LinearLayout.LayoutParams(0, WRAP, 1));
        Button switchApp = button("更换应用", () -> {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(editor.getWindowToken(), 0);
            setEditing(false);
        });
        styleButton(switchApp, R.color.field, R.color.accent, R.color.outline, 12);
        header.addView(switchApp, new LinearLayout.LayoutParams(WRAP, WRAP));
        add(profile, header, 0);
        targetPackage = text("", 13, R.color.text_muted, false);
        targetPackage.setTextIsSelectable(true);
        add(profile, targetPackage, 3);
        enabled = toggle(profile, "启用此应用的配置", 10);
        enabled.setOnCheckedChangeListener((view, checked) -> markEdited());

        LinearLayout sim = card(editor, 10);
        add(sim, heading("SIM 与运营商", 17, R.color.text_main), 0);
        maskSim = toggle(sim, "隐藏真实 SIM 与运营商", 4);
        carrierGroup = column();
        add(sim, carrierGroup, 8);
        carrier = choice(carrierGroup, "显示给应用的运营商", 0, null,
                () -> Arrays.stream(CarrierProfile.values()).map(option -> option.country).collect(Collectors.toList()),
                code -> {
                    CarrierProfile option = CarrierProfile.forCountry(code);
                    return option.region + " · " + option.name + "（" + option.operator + "）";
                });
        carrier.setValue(CarrierProfile.US.country);
        add(carrierGroup, text("隐藏手机号和 SIM 标识，替换运营商及国家码，保留蜂窝连接状态。",
                13, R.color.text_muted, false), 4);
        maskSim.setOnCheckedChangeListener((view, checked) -> {
            carrierGroup.setVisibility(checked ? View.VISIBLE : View.GONE);
            markEdited();
        });

        LinearLayout region = card(editor, 10);
        add(region, heading("语言与时区", 17, R.color.text_main), 0);
        language = choice(region, "语言与地区", 12, "跟随系统", ProfileActivity::languageTags,
                tag -> choiceLabel(Locale.forLanguageTag(tag).getDisplayName(), tag));
        TimeZoneNames zoneNames = TimeZoneNames.getInstance(Locale.getDefault());
        timeZone = choice(region, "时区", 12, "跟随系统", ZoneId::getAvailableZoneIds,
                id -> choiceLabel(zoneNames.getExemplarLocationName(id), id));

        LinearLayout fingerprint = card(editor, 10);
        add(fingerprint, heading("设备与使用隐私", 17, R.color.text_main), 0);
        maskFingerprint = toggle(fingerprint, "隔离设备标识", 6);
        add(fingerprint, text("为此应用分配独立的 Android ID，隐藏部分广告和无线网络标识。",
                13, R.color.text_muted, false), 0);
        maskUsage = toggle(fingerprint, "隐藏系统使用记录", 8);
        add(fingerprint, text("屏蔽 Android 使用统计和事件读取。", 13, R.color.text_muted, false), 0);
        maskFingerprint.setOnCheckedChangeListener((view, checked) -> markEdited());
        maskUsage.setOnCheckedChangeListener((view, checked) -> markEdited());
    }

    private void buildFooter(LinearLayout page) {
        footer = column();
        footer.setBackgroundColor(getColor(R.color.surface));
        footer.setElevation(dp(6));
        footer.setPadding(dp(18), dp(10), dp(18), dp(10));
        footer.setVisibility(View.GONE);
        add(page, footer, 0);

        status = text("", 13, R.color.text_muted, false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(footer, status, 0);

        LinearLayout actions = new LinearLayout(this);
        appInfo = button("应用信息", this::openTargetSettings);
        styleButton(appInfo, R.color.field, R.color.accent, R.color.outline, 12);
        LinearLayout.LayoutParams infoLayout = new LinearLayout.LayoutParams(WRAP, WRAP);
        infoLayout.setMarginEnd(dp(8));
        actions.addView(appInfo, infoLayout);
        saveButton = button("保存配置", this::save);
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, WRAP, 1));
        add(footer, actions, 8);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("target", loadedPackage);
        state.putBoolean("available", available);
        state.putBoolean("enabled", enabled.isChecked());
        state.putBoolean("sim", maskSim.isChecked());
        state.putString("country", carrier.value);
        state.putString("language", language.value);
        state.putString("zone", timeZone.value);
        state.putBoolean("fingerprint", maskFingerprint.isChecked());
        state.putBoolean("usage", maskUsage.isChecked());
        state.putString("device_id", deviceId);
        state.putBoolean("dirty", dirty);
        state.putBoolean("saved", saved);
        state.putBoolean("warning", warning);
        state.putBoolean("editing", editing);
        state.putString("status", status.getText().toString());
        state.putInt("scroll", scroll.getScrollY());
    }

    private void chooseOther() {
        LinearLayout form = column();
        form.setPadding(dp(24), dp(10), dp(24), dp(4));
        EditText input = field(form, "应用包名", "例如 com.example.app");
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        if (loadedPackage != null && !TIKTOK.equals(loadedPackage) && !CLAUDE.equals(loadedPackage)) {
            input.setText(loadedPackage);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择其他应用")
                .setView(form)
                .setNegativeButton("取消", null)
                .setNeutralButton("TikTok 备用版", (view, which) -> select(TIKTOK_ALTERNATE))
                .setPositiveButton("打开配置", null)
                .create();
        dialog.show();
        View confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        confirm.setOnClickListener(view -> {
            String name = input.getText().toString().trim();
            if (!Profile.isTargetPackage(name)) {
                input.setError("请输入有效的应用包名");
                return;
            }
            dialog.dismiss();
            select(name);
        });
        input.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                confirm.performClick();
                return true;
            }
            return false;
        });
    }

    private void select(String name) {
        if (name.equals(loadedPackage) && available) {
            setEditing(true);
            return;
        }
        if (dirty) {
            new AlertDialog.Builder(this)
                    .setTitle("放弃未保存的修改？")
                    .setMessage("当前应用的修改尚未保存。")
                    .setNegativeButton("继续编辑", null)
                    .setPositiveButton("放弃修改", (dialog, which) -> load(name))
                    .show();
        } else {
            load(name);
        }
    }

    private void load(String name) {
        loadedPackage = name;
        available = profileApplication().isConnected();
        dirty = false;
        saved = false;
        deviceId = "";
        showTarget();
        readProfile();
        setEditing(true);
    }

    private void readProfile() {
        try {
            Profile profile = Profile.read(profileApplication().preferences(loadedPackage));
            deviceId = profile.deviceId;
            populate(profile.enabled, profile.maskSim, profile.carrier.country,
                    profile.language, profile.timeZone, profile.maskFingerprint, profile.maskUsage);
            showStatus(profile.enabled ? "此应用的配置已启用" : "此应用的配置已关闭", false);
        } catch (IllegalArgumentException | ClassCastException e) {
            populate(false, false, "", "", "", false, false);
            showStatus("此应用的配置无法读取。请重新选择并保存。", true);
        } catch (RuntimeException e) {
            available = false;
            populate(false, false, "", "", "", false, false);
            showStatus(profileApplication().isConnected() ? "远程配置暂时无法读取，请重新选择应用。"
                    : profileApplication().connection(), true);
        }
    }

    private void populate(boolean isEnabled, boolean hideSim, String iso, String locale,
                          String zone, boolean hideFingerprint, boolean hideUsage) {
        populating = true;
        enabled.setChecked(isEnabled);
        maskSim.setChecked(hideSim);
        carrierGroup.setVisibility(hideSim ? View.VISIBLE : View.GONE);
        carrier.setValue(CarrierProfile.forCountry(iso).country);
        language.setValue(locale);
        timeZone.setValue(zone);
        maskFingerprint.setChecked(hideFingerprint);
        maskUsage.setChecked(hideUsage);
        populating = false;
    }

    private void showTarget() {
        targetName.setText(CLAUDE.equals(loadedPackage) ? "Claude"
                : TIKTOK.equals(loadedPackage) ? "TikTok"
                : TIKTOK_ALTERNATE.equals(loadedPackage) ? "TikTok 备用版" : "自定义应用");
        targetPackage.setText(loadedPackage);
        updateSelection();
    }

    private void updateSelection() {
        styleSelection(tiktok, TIKTOK.equals(loadedPackage));
        styleSelection(claude, CLAUDE.equals(loadedPackage));
        styleSelection(other, loadedPackage != null && !TIKTOK.equals(loadedPackage)
                && !CLAUDE.equals(loadedPackage));
    }

    private void setEditing(boolean show) {
        editing = show;
        picker.setVisibility(show ? View.GONE : View.VISIBLE);
        editor.setVisibility(show ? View.VISIBLE : View.GONE);
        footer.setVisibility(show ? View.VISIBLE : View.GONE);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void markEdited() {
        if (!populating && loadedPackage != null && available) {
            dirty = true;
            saved = false;
            showStatus("有未保存的修改", false);
        }
    }

    private void save() {
        try {
            if (maskFingerprint.isChecked() && deviceId.isEmpty()) {
                deviceId = Profile.newDeviceId();
            }
            Profile profile = new Profile(enabled.isChecked(), maskSim.isChecked(),
                    carrier.value, language.value, timeZone.value,
                    maskFingerprint.isChecked(), maskUsage.isChecked(), deviceId);
            if (profile.enabled && !profile.applies()) {
                showStatus("请至少选择一项要隐藏或替换的信息。", true);
                return;
            }
            if (profile.save(profileApplication().preferences(loadedPackage))) {
                populate(profile.enabled, profile.maskSim, profile.carrier.country,
                        profile.language, profile.timeZone, profile.maskFingerprint, profile.maskUsage);
                dirty = false;
                saved = true;
                showStatus("已保存。请强行停止目标应用，再重新打开。", false);
            } else {
                showStatus("写入失败，请重试。", true);
            }
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (RuntimeException e) {
            showStatus("无法写入远程配置，请确认 LSPosed 已连接后重试。", true);
        }
    }

    private void openTargetSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", loadedPackage, null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showStatus("请在系统设置中手动打开目标应用的信息页。", true);
        }
    }

    private ProfileApplication profileApplication() {
        return (ProfileApplication) getApplication();
    }

    private void updateConnection() {
        ProfileApplication app = profileApplication();
        frameworkStatus.setText(app.connection());
        if (loadedPackage == null || available == app.isConnected()) {
            return;
        }
        available = app.isConnected();
        if (!available) {
            showStatus(app.connection(), true);
        } else if (dirty) {
            showStatus("有未保存的修改", false);
        } else {
            readProfile();
        }
    }

    private void showStatus(String message, boolean warn) {
        warning = warn;
        enabled.setEnabled(available);
        maskSim.setEnabled(available);
        carrier.setEnabled(available);
        language.setEnabled(available);
        timeZone.setEnabled(available);
        maskFingerprint.setEnabled(available);
        maskUsage.setEnabled(available);
        status.setText(message);
        status.setTextColor(getColor(warn ? R.color.warning_text : R.color.text_muted));
        status.setBackground(warn ? rounded(R.color.warning_bg, 10, 0) : null);
        status.setPadding(warn ? dp(10) : 0, warn ? dp(7) : 0, warn ? dp(10) : 0, warn ? dp(7) : 0);
        appInfo.setVisibility(available && !dirty && (saved || enabled.isChecked())
                ? View.VISIBLE : View.GONE);
        saveButton.setEnabled(available && dirty);
        saveButton.setText(saved ? "已保存" : "保存配置");
        styleButton(saveButton, saveButton.isEnabled() ? R.color.accent : R.color.field,
                saveButton.isEnabled() ? R.color.on_accent : R.color.text_muted, 0, 14);
    }

    private LinearLayout card(LinearLayout parent, int margin) {
        LinearLayout card = column();
        card.setBackground(rounded(R.color.surface, 20, R.color.outline));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        add(parent, card, margin);
        return card;
    }

    private LinearLayout column() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        return group;
    }

    private TextView heading(String title, int size, int color) {
        TextView view = text(title, size, color, true);
        if (Build.VERSION.SDK_INT >= 28) {
            view.setAccessibilityHeading(true);
        }
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        if (bold) {
            view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return view;
    }

    private Button button(String title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setStateListAnimator(null);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void styleSelection(Button view, boolean selected) {
        view.setSelected(selected);
        styleButton(view, selected ? R.color.selected : R.color.field,
                selected ? R.color.accent : R.color.text_main,
                selected ? R.color.accent : R.color.outline, 12);
    }

    private void styleButton(Button view, int fill, int ink, int stroke, int radius) {
        view.setTextSize(14);
        view.setTextColor(getColor(ink));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(
                (getColor(ink) & 0x00ffffff) | 0x26000000), rounded(fill, radius, stroke), null));
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
    }

    private Switch toggle(LinearLayout parent, String title, int margin) {
        Switch view = new Switch(this);
        view.setText(title);
        view.setTextSize(15);
        view.setTextColor(getColor(R.color.text_main));
        view.setSwitchPadding(dp(16));
        view.setMinHeight(dp(48));
        add(parent, view, margin);
        return view;
    }

    private EditText field(LinearLayout parent, String label, String hint) {
        EditText view = new EditText(this);
        view.setId(View.generateViewId());
        view.setSingleLine(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        view.setTextSize(15);
        view.setTextColor(getColor(R.color.text_main));
        view.setHintTextColor(getColor(R.color.text_muted));
        view.setHint(hint);
        view.setSelectAllOnFocus(true);
        view.setMinHeight(dp(48));
        view.setBackground(rounded(R.color.field, 12, R.color.outline));
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setOnFocusChangeListener((field, focused) ->
                field.setBackground(rounded(R.color.field, 12, focused ? R.color.accent : R.color.outline)));
        TextView caption = text(label, 14, R.color.text_main, true);
        caption.setLabelFor(view.getId());
        add(parent, caption, 0);
        add(parent, view, 5);
        return view;
    }

    private ChoiceField choice(LinearLayout parent, String title, int margin, String emptyLabel,
                               Supplier<Collection<String>> options, Function<String, String> label) {
        ChoiceField view = new ChoiceField(title, emptyLabel, options, label);
        view.setId(View.generateViewId());
        TextView caption = text(title, 14, R.color.text_main, true);
        caption.setLabelFor(view.getId());
        add(parent, caption, margin);
        add(parent, view, 5);
        return view;
    }

    private static Collection<String> languageTags() {
        List<String> tags = new ArrayList<>();
        for (Locale locale : Locale.getAvailableLocales()) {
            if (!locale.getLanguage().isEmpty()) {
                tags.add(locale.toLanguageTag());
            }
        }
        return tags;
    }

    private static String choiceLabel(String name, String code) {
        return name == null || name.isEmpty() || name.equals(code) ? code : name + "（" + code + "）";
    }

    private final class ChoiceField extends Button {
        private final String title;
        private final Supplier<Collection<String>> options;
        private final Function<String, String> label;
        private final String emptyLabel;
        private String value = "";

        ChoiceField(String title, String emptyLabel, Supplier<Collection<String>> options,
                    Function<String, String> label) {
            super(ProfileActivity.this);
            this.title = title;
            this.emptyLabel = emptyLabel;
            this.options = options;
            this.label = label;
            setAllCaps(false);
            setMinHeight(dp(48));
            setStateListAnimator(null);
            setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            styleButton(this, R.color.field, R.color.text_main, R.color.outline, 12);
            if (emptyLabel != null) {
                setValue("");
            }
            setOnClickListener(view -> showChoices());
        }

        void setValue(String value) {
            this.value = value;
            setText((value.isEmpty() ? emptyLabel : label.apply(value)) + "  ▾");
        }

        private void showChoices() {
            Map<String, String> labels = new HashMap<>();
            for (String option : options.get()) {
                labels.put(option, label.apply(option));
            }
            if (!value.isEmpty()) {
                labels.put(value, label.apply(value));
            }
            List<String> keys = new ArrayList<>(labels.keySet());
            Collator collator = Collator.getInstance();
            keys.sort((left, right) -> collator.compare(labels.get(left), labels.get(right)));
            if (emptyLabel != null) {
                keys.add(0, "");
                labels.put("", emptyLabel);
            }

            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(ProfileActivity.this)
                    .setTitle("选择" + title)
                    .setNegativeButton("取消", null);
            if (emptyLabel == null) {
                CharSequence[] items = keys.stream().map(labels::get).toArray(CharSequence[]::new);
                dialogBuilder.setSingleChoiceItems(items, keys.indexOf(value), (dialog, position) -> {
                    choose(keys.get(position));
                    dialog.dismiss();
                }).show();
                return;
            }

            LinearLayout content = column();
            SearchView search = new SearchView(ProfileActivity.this);
            search.setIconifiedByDefault(false);
            search.setQueryHint("搜索" + title);
            add(content, search, 0);
            ListView list = new ListView(ProfileActivity.this);
            int height = Math.min(dp(360), getResources().getDisplayMetrics().heightPixels / 2);
            content.addView(list, new LinearLayout.LayoutParams(MATCH, height));
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ProfileActivity.this,
                    android.R.layout.simple_list_item_single_choice);
            list.setAdapter(adapter);
            List<String> matches = new ArrayList<>();
            Consumer<String> updateResults = query -> {
                String term = query.trim().toLowerCase(Locale.ROOT);
                matches.clear();
                adapter.setNotifyOnChange(false);
                adapter.clear();
                for (String key : keys) {
                    String name = labels.get(key);
                    if (name.toLowerCase(Locale.ROOT).contains(term)) {
                        matches.add(key);
                        adapter.add(name);
                    }
                }
                list.clearChoices();
                adapter.notifyDataSetChanged();
                int selected = matches.indexOf(value);
                if (selected >= 0) {
                    list.setItemChecked(selected, true);
                }
                list.setSelection(term.isEmpty() && selected >= 0 ? selected : 0);
            };
            updateResults.accept("");
            search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextChange(String query) {
                    updateResults.accept(query);
                    return true;
                }

                @Override
                public boolean onQueryTextSubmit(String query) {
                    search.clearFocus();
                    return true;
                }
            });
            AlertDialog dialog = dialogBuilder.setView(content).create();
            list.setOnItemClickListener((parent, view, position, id) -> {
                choose(matches.get(position));
                dialog.dismiss();
            });
            dialog.show();
        }

        private void choose(String selection) {
            if (!selection.equals(value)) {
                setValue(selection);
                markEdited();
            }
        }
    }

    private GradientDrawable rounded(int fill, int radius, int outline) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(getColor(fill));
        background.setCornerRadius(dp(radius));
        if (outline != 0) {
            background.setStroke(dp(1), getColor(outline));
        }
        return background;
    }

    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(MATCH, WRAP);
        layout.topMargin = dp(margin);
        parent.addView(child, layout);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
