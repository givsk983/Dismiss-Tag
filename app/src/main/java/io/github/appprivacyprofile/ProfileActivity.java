package io.github.appprivacyprofile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.icu.text.TimeZoneNames;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

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
    private static final String FRAMEWORK_HELP =
            "请在支持 Modern API 101 或更新版本的 LSPosed 管理器启用模块，勾选目标应用，再重新打开此配置页。";
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    private ScrollView scroll;
    private LinearLayout picker;
    private LinearLayout editor;
    private LinearLayout footer;
    private LinearLayout carrierGroup;
    private LinearLayout locationFields;
    private Button chooseApp;
    private LinearLayout configuredApps;
    private Button backToApps;
    private Button regionPicker;
    private OnBackInvokedCallback editorBack;
    private Button saveButton;
    private Button appInfo;
    private Button updateButton;
    private TextView targetName;
    private TextView targetPackage;
    private TextView targetScope;
    private TextView status;
    private TextView frameworkStatus;
    private TextView appListStatus;
    private Switch enabled;
    private Switch maskSim;
    private Switch maskFingerprint;
    private Switch maskUsage;
    private Switch maskLocation;
    private Button cityPicker;
    private EditText latitude;
    private EditText longitude;
    private final Map<String, EditText> simFields = new java.util.LinkedHashMap<>();
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
    private boolean resumed;
    private AppCatalog catalog;
    private int catalogRequest;
    private Runnable refreshOpenPicker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout page = column();
        page.setBackgroundColor(getColor(R.color.page));
        backToApps = button("‹  返回应用列表", this::returnToPicker);
        backToApps.setContentDescription("返回应用列表");
        styleButton(backToApps, R.color.field, R.color.accent, R.color.outline, 12);
        backToApps.setVisibility(View.GONE);
        LinearLayout.LayoutParams backLayout = new LinearLayout.LayoutParams(WRAP, WRAP);
        backLayout.setMarginStart(dp(18));
        backLayout.topMargin = dp(6);
        page.addView(backToApps, backLayout);
        if (Build.VERSION.SDK_INT >= 33) {
            editorBack = this::returnToPicker;
        }
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
                    state.getString("zone", ""), state.getBoolean("fingerprint"), state.getBoolean("usage"),
                    state.getBoolean("mask_location"), state.getString("latitude", ""),
                    state.getString("longitude", ""));
            for (String key : SimProfile.KEYS) simFields.get(key).setText(state.getString("sim_" + key, ""));
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

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refreshCatalog();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (editing) {
            returnToPicker();
        } else {
            super.onBackPressed();
        }
    }

    private void buildHero(LinearLayout content) {
        LinearLayout hero = column();
        hero.setBackgroundResource(R.drawable.bg_hero);
        hero.setPadding(dp(18), dp(12), dp(18), dp(20));
        add(content, hero, 0);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("坐 忘 道  ·  身 份 法 帖", 13, R.color.hero_badge, true);
        row.addView(brand, new LinearLayout.LayoutParams(0, WRAP, 1));
        Button help = button("设置帮助", () -> new AlertDialog.Builder(this)
                .setTitle("如何让配置生效")
                .setMessage("1. " + FRAMEWORK_HELP
                        + "\n\n2. 选择目标应用，启用配置并保存。"
                        + "\n\n3. 在系统应用信息中强行停止目标应用，再重新打开。")
                .setPositiveButton("知道了", null)
                .show());
        styleButton(help, R.color.hero_top, R.color.hero_badge, R.color.hero_rule, 8);
        row.addView(help, new LinearLayout.LayoutParams(WRAP, WRAP));
        add(hero, row, 0);

        LinearLayout identity = new LinearLayout(this);
        identity.setGravity(Gravity.CENTER_VERTICAL);
        ImageView tile = new ImageView(this);
        tile.setImageResource(R.drawable.ic_privacy);
        tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        identity.addView(tile, new LinearLayout.LayoutParams(dp(70), dp(70)));
        LinearLayout copy = column();
        LinearLayout.LayoutParams copyLayout = new LinearLayout.LayoutParams(0, WRAP, 1);
        copyLayout.setMarginStart(dp(14));
        identity.addView(copy, copyLayout);
        TextView title = heading(getString(R.string.app_name), 30, R.color.hero_text);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        add(copy, title, 0);
        add(copy, text("以假修真 · 随应用易形换面", 14, R.color.hero_muted, false), 5);
        add(hero, identity, 12);
        TextView motto = text("真亦假时假亦真", 19, R.color.hero_text, true);
        motto.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        motto.setLetterSpacing(0.18f);
        add(hero, motto, 18);
        TextView tiles = text("红 中  /  白 板  /  发 财", 12, R.color.hero_badge, true);
        tiles.setLetterSpacing(0.12f);
        add(hero, tiles, 6);
    }

    private void buildSelector(LinearLayout content) {
        LinearLayout selector = card(content, 14);
        add(selector, heading("入局 · 选择应用", 18, R.color.text_main), 0);
        frameworkStatus = text("", 13, R.color.text_muted, false);
        frameworkStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(selector, frameworkStatus, 5);
        chooseApp = button("从已安装应用选择", this::chooseInstalledApp);
        chooseApp.setEnabled(false);
        styleButton(chooseApp, R.color.accent, R.color.on_accent, 0, 12);
        add(selector, chooseApp, 10);
        appListStatus = text("", 13, R.color.text_muted, false);
        appListStatus.setVisibility(View.GONE);
        add(selector, appListStatus, 6);

        LinearLayout configured = card(content, 10);
        add(configured, heading("已配置", 18, R.color.text_main), 0);
        configuredApps = column();
        add(configured, configuredApps, 6);
        renderAppCatalog();
    }

    private void buildProjectLinks(LinearLayout content) {
        LinearLayout project = card(content, 14);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(heading("道牒 · 关于", 18, R.color.text_main),
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
        add(profile, targetName, 0);
        targetPackage = text("", 13, R.color.text_muted, false);
        targetPackage.setTextIsSelectable(true);
        add(profile, targetPackage, 3);
        targetScope = text("", 13, R.color.text_muted, true);
        add(profile, targetScope, 4);
        enabled = toggle(profile, "启用此应用的配置", 10);
        enabled.setOnCheckedChangeListener((view, checked) -> markEdited());
        regionPicker = button("一键应用地区预设", this::chooseRegion);
        styleButton(regionPicker, R.color.field, R.color.accent, R.color.outline, 12);
        add(profile, regionPicker, 8);
        add(profile, text("同时设置运营商、语言、时区和位置，选中后保存当前配置。",
                13, R.color.text_muted, false), 4);

        LinearLayout sim = card(editor, 10);
        add(sim, heading("壹 · 易名换姓", 17, R.color.text_main), 0);
        maskSim = toggle(sim, "伪装 SIM 与运营商", 4);
        carrierGroup = column();
        add(sim, carrierGroup, 8);
        carrier = choice(carrierGroup, "显示给应用的运营商", 0, null,
                () -> Arrays.stream(CarrierProfile.values()).map(option -> option.country).collect(Collectors.toList()),
                code -> {
                    CarrierProfile option = CarrierProfile.forCountry(code);
                    return option.region + " · " + option.name + "（" + option.operator + "）";
                });
        carrier.setValue(CarrierProfile.US.country);
        add(carrierGroup, text("运营商字段留空时使用上方预设；身份字段留空时隐藏。配置按应用保存，不改变实体 SIM。",
                13, R.color.text_muted, false), 8);
        Button details = button("展开 / 收起 SIM 详细身份", () -> {});
        LinearLayout advanced = column();
        advanced.setVisibility(View.GONE);
        details.setOnClickListener(v -> advanced.setVisibility(
                advanced.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        styleButton(details, R.color.selected, R.color.accent, R.color.outline, 6);
        add(carrierGroup, details, 10);
        add(carrierGroup, advanced, 0);
        String[] labels = {"运营商名称 / SPN", "运营商代码 / MCC + MNC", "SIM 国家码 / ISO",
                "用户识别码 / IMSI", "卡片序列号 / ICCID", "手机号码 / MSISDN", "用户组 / GID1", "用户组 / GID2"};
        String[] hints = {"留空使用预设名称", "5 或 6 位数字，例如 310260", "两位字母，例如 us",
                "14 或 15 位数字；留空隐藏", "18 至 22 位数字；留空隐藏", "+12025550123；留空隐藏", "十六进制；留空隐藏", "十六进制；留空隐藏"};
        for (int i = 0; i < SimProfile.KEYS.length; i++) {
            EditText input = field(advanced, labels[i], hints[i], 10);
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence t, int start, int count, int after) {}
                public void onTextChanged(CharSequence t, int start, int before, int count) { markEdited(); }
                public void afterTextChanged(Editable t) {}
            });
            simFields.put(SimProfile.KEYS[i], input);
        }
        add(advanced, text("IMSI 的 MCC/MNC 前缀需与运营商一致。号码、IMSI 和 ICCID 由你指定；不会自动生成真实号码。系统权限限制仍然适用，双卡使用同一份伪装值。",
                12, R.color.text_muted, false), 10);
        maskSim.setOnCheckedChangeListener((view, checked) -> {
            carrierGroup.setVisibility(checked ? View.VISIBLE : View.GONE);
            markEdited();
        });

        LinearLayout region = card(editor, 10);
        add(region, heading("贰 · 移星换斗 · 语言与时区", 17, R.color.text_main), 0);
        language = choice(region, "语言与地区", 12, "跟随系统", ProfileActivity::languageTags,
                tag -> choiceLabel(Locale.forLanguageTag(tag).getDisplayName(), tag));
        TimeZoneNames zoneNames = TimeZoneNames.getInstance(Locale.getDefault());
        timeZone = choice(region, "时区", 12, "跟随系统", ZoneId::getAvailableZoneIds,
                id -> choiceLabel(zoneNames.getExemplarLocationName(id), id));

        LinearLayout location = card(editor, 10);
        add(location, heading("叁 · 移形换位 · 定位", 17, R.color.text_main), 0);
        maskLocation = toggle(location, "启用虚拟定位", 4);
        locationFields = column();
        locationFields.setVisibility(View.GONE);
        add(location, locationFields, 8);
        cityPicker = button("选择热门城市", this::chooseCity);
        styleButton(cityPicker, R.color.field, R.color.accent, R.color.outline, 12);
        add(locationFields, cityPicker, 0);
        latitude = coordinateField(locationFields, "纬度（−90 到 90）", "例如 37.7749", 10);
        longitude = coordinateField(locationFields, "经度（−180 到 180）", "例如 -122.4194", 10);
        add(locationFields, text("用固定坐标替换目标应用收到的 Android 定位结果。",
                13, R.color.text_muted, false), 8);
        maskLocation.setOnCheckedChangeListener((view, checked) -> {
            locationFields.setVisibility(checked ? View.VISIBLE : View.GONE);
            markEdited();
        });

        LinearLayout fingerprint = card(editor, 10);
        add(fingerprint, heading("肆 · 隐去来处 · 设备隐私", 17, R.color.text_main), 0);
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
        for (String key : SimProfile.KEYS) state.putString("sim_" + key, simFields.get(key).getText().toString());
        state.putString("country", carrier.value);
        state.putString("language", language.value);
        state.putString("zone", timeZone.value);
        state.putBoolean("fingerprint", maskFingerprint.isChecked());
        state.putBoolean("usage", maskUsage.isChecked());
        state.putBoolean("mask_location", maskLocation.isChecked());
        state.putString("latitude", latitude.getText().toString());
        state.putString("longitude", longitude.getText().toString());
        state.putString("device_id", deviceId);
        state.putBoolean("dirty", dirty);
        state.putBoolean("saved", saved);
        state.putBoolean("warning", warning);
        state.putBoolean("editing", editing);
        state.putString("status", status.getText().toString());
        state.putInt("scroll", scroll.getScrollY());
    }

    private void chooseInstalledApp() {
        LinearLayout content = column();
        SearchView search = new SearchView(this);
        search.setIconifiedByDefault(false);
        search.setQueryHint("搜索应用名称");
        add(content, search, 0);

        FrameLayout results = new FrameLayout(this);
        int height = Math.min(dp(420), getResources().getDisplayMetrics().heightPixels / 2);
        content.addView(results, new LinearLayout.LayoutParams(MATCH, height));
        ListView list = new ListView(this);
        results.addView(list, new FrameLayout.LayoutParams(MATCH, MATCH));
        TextView empty = text("没有匹配的应用", 14, R.color.text_muted, false);
        empty.setGravity(Gravity.CENTER);
        results.addView(empty, new FrameLayout.LayoutParams(MATCH, MATCH));
        list.setEmptyView(empty);

        ArrayAdapter<AppCatalog.Entry> adapter = new ArrayAdapter<AppCatalog.Entry>(this, 0) {
            @Override
            public View getView(int position, View recycled, ViewGroup parent) {
                AppRow row = recycled instanceof AppRow ? (AppRow) recycled : new AppRow();
                row.bind(getItem(position));
                return row;
            }
        };
        list.setAdapter(adapter);
        Runnable updateResults = () -> {
            String query = search.getQuery().toString().trim().toLowerCase(Locale.ROOT);
            adapter.setNotifyOnChange(false);
            adapter.clear();
            for (AppCatalog.Entry app : catalog.installed) {
                if (app.label.toLowerCase(Locale.ROOT).contains(query)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                    adapter.add(app);
                }
            }
            adapter.notifyDataSetChanged();
        };
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String query) {
                updateResults.run();
                list.setSelection(0);
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                search.clearFocus();
                return true;
            }
        });
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppCatalog.Entry app = adapter.getItem(position);
            dialog.dismiss();
            select(app.packageName);
        });
        dialog.setOnDismissListener(ignored -> refreshOpenPicker = null);
        refreshOpenPicker = updateResults;
        updateResults.run();
        content.setFocusableInTouchMode(true);
        content.requestFocus();
        dialog.show();
    }

    private void refreshCatalog() {
        int request = ++catalogRequest;
        profileApplication().loadAppCatalog(result -> {
            if (request != catalogRequest || isFinishing() || isDestroyed()) return;
            catalog = result;
            renderAppCatalog();
            if (loadedPackage != null) showTarget();
            if (refreshOpenPicker != null) refreshOpenPicker.run();
        });
    }

    private void renderAppCatalog() {
        configuredApps.removeAllViews();
        if (catalog == null) {
            add(configuredApps, text("正在读取应用列表…", 13, R.color.text_muted, false), 0);
            return;
        }
        chooseApp.setEnabled(!catalog.installed.isEmpty());
        appListStatus.setVisibility(catalog.installedListUnavailable ? View.VISIBLE : View.GONE);
        appListStatus.setText("系统未提供安装列表，请检查本应用的应用列表访问权限。");
        if (catalog.installed.isEmpty()) {
            chooseApp.setText("暂时没有可选择的应用");
        } else {
            chooseApp.setText(catalog.installedListUnavailable ? "从已知应用选择" : "从已安装应用选择");
        }
        for (AppCatalog.Entry app : catalog.configured) {
            AppRow row = new AppRow();
            row.bind(app);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(getColor(R.color.selected)),
                    rounded(R.color.field, 10, R.color.outline), null));
            row.setFocusable(true);
            row.setOnClickListener(view -> select(app.packageName));
            add(configuredApps, row, 6);
        }
        if (catalog.configured.isEmpty()) {
            String message = !profileApplication().isConnected() ? "连接 LSPosed 后可查找以前保存的配置。"
                    : catalog.configurationScanComplete ? "还没有已保存的应用配置。"
                    : "部分配置暂时无法读取。";
            add(configuredApps, text(message, 13, R.color.text_muted, false), 0);
        }
    }

    private String scopeLabel(Boolean scoped) {
        return scoped == null ? "LSPosed 勾选状态未知" : scoped ? "LSPosed 已勾选" : "LSPosed 未勾选";
    }

    private final class AppRow extends LinearLayout {
        private final ImageView icon;
        private final TextView name;
        private final TextView packageName;
        private final TextView details;

        AppRow() {
            super(ProfileActivity.this);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(12), dp(9), dp(12), dp(9));
            icon = new ImageView(ProfileActivity.this);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout labels = column();
            LinearLayout.LayoutParams labelLayout = new LinearLayout.LayoutParams(0, WRAP, 1);
            labelLayout.setMarginStart(dp(12));
            addView(labels, labelLayout);
            name = text("", 15, R.color.text_main, true);
            packageName = text("", 12, R.color.text_muted, false);
            packageName.setSingleLine(true);
            packageName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            details = text("", 12, R.color.text_muted, false);
            add(labels, name, 0);
            add(labels, packageName, 2);
            add(labels, details, 3);
        }

        void bind(AppCatalog.Entry app) {
            icon.setImageDrawable(app.application == null ? getPackageManager().getDefaultActivityIcon()
                    : app.application.loadIcon(getPackageManager()));
            name.setText(app.label);
            packageName.setText(app.packageName);
            packageName.setVisibility(app.label.equals(app.packageName) ? View.GONE : View.VISIBLE);
            Boolean scoped = catalog.inScope(app.packageName);
            String configuration = app.configured ? (Boolean.FALSE.equals(app.enabled)
                    ? " · 配置已关闭" : " · 已配置") : "";
            String installation = app.application == null ? " · 安装状态未确认" : "";
            details.setText(scopeLabel(scoped) + configuration + installation);
            details.setTextColor(getColor(Boolean.TRUE.equals(scoped) ? R.color.accent : R.color.text_muted));
        }
    }

    private void chooseRegion() {
        RegionPreset[] regions = RegionPreset.values();
        CharSequence[] labels = Arrays.stream(regions).map(RegionPreset::label).toArray(CharSequence[]::new);
        new AlertDialog.Builder(this)
                .setTitle("选择地区并保存")
                .setItems(labels, (dialog, position) -> {
                    if (!available) {
                        showStatus(profileApplication().connection(), true);
                        return;
                    }
                    RegionPreset region = regions[position];
                    populate(true, true, region.carrier.country, region.language, region.timeZone,
                            maskFingerprint.isChecked(), maskUsage.isChecked(), true,
                            region.city.latitude, region.city.longitude);
                    markEdited();
                    save();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void chooseCity() {
        CityPreset[] cities = CityPreset.values();
        CharSequence[] names = new CharSequence[cities.length];
        for (int i = 0; i < cities.length; i++) {
            names[i] = cities[i].label;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择热门城市")
                .setItems(names, (dialog, position) -> {
                    CityPreset city = cities[position];
                    latitude.setText(city.latitude);
                    longitude.setText(city.longitude);
                })
                .setNegativeButton("取消", null)
                .show();
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
            SharedPreferences preferences = profileApplication().preferences(loadedPackage);
            Profile profile = Profile.read(preferences);
            deviceId = profile.deviceId;
            populate(profile.enabled, profile.maskSim, profile.carrier.country,
                    profile.language, profile.timeZone, profile.maskFingerprint, profile.maskUsage,
                    profile.maskLocation, profile.latitude, profile.longitude);
            populateSim(profile.sim);
            String message = preferences.contains(Profile.KEY_ENABLED)
                    ? (profile.enabled ? "此应用的配置已启用" : "此应用的配置已关闭")
                    : "此应用尚未保存配置";
            showStatus(message, false);
        } catch (IllegalArgumentException | ClassCastException e) {
            populate(false, false, "", "", "", false, false, false, "", "");
            showStatus("此应用的配置无法读取。请重新选择并保存。", true);
        } catch (RuntimeException e) {
            available = false;
            populate(false, false, "", "", "", false, false, false, "", "");
            showStatus(profileApplication().isConnected() ? "远程配置暂时无法读取，请重新选择应用。"
                    : profileApplication().connection(), true);
        }
    }

    private void populate(boolean isEnabled, boolean hideSim, String iso, String locale,
                          String zone, boolean hideFingerprint, boolean hideUsage,
                          boolean useLocation, String latitudeText, String longitudeText) {
        populating = true;
        for (EditText field : simFields.values()) field.setText("");
        enabled.setChecked(isEnabled);
        maskSim.setChecked(hideSim);
        carrierGroup.setVisibility(hideSim ? View.VISIBLE : View.GONE);
        carrier.setValue(CarrierProfile.forCountry(iso).country);
        language.setValue(locale);
        timeZone.setValue(zone);
        maskFingerprint.setChecked(hideFingerprint);
        maskUsage.setChecked(hideUsage);
        maskLocation.setChecked(useLocation);
        locationFields.setVisibility(useLocation ? View.VISIBLE : View.GONE);
        latitude.setText(latitudeText);
        longitude.setText(longitudeText);
        populating = false;
    }

    private void populateSim(SimProfile sim) {
        populating = true;
        for (String key : SimProfile.KEYS) simFields.get(key).setText(sim.get(key));
        populating = false;
    }

    private void showTarget() {
        AppCatalog.Entry app = catalog == null ? null : catalog.byPackage.get(loadedPackage);
        targetName.setText(app == null ? loadedPackage : app.label);
        targetPackage.setText(loadedPackage);
        targetPackage.setVisibility(app == null || app.label.equals(loadedPackage) ? View.GONE : View.VISIBLE);
        Boolean scoped = catalog == null ? null : catalog.inScope(loadedPackage);
        targetScope.setText(Boolean.FALSE.equals(scoped) ? "LSPosed 未勾选，请在管理器中勾选此应用"
                : scopeLabel(scoped));
        targetScope.setTextColor(getColor(Boolean.FALSE.equals(scoped) ? R.color.warning_text : R.color.text_muted));
    }

    private void setEditing(boolean show) {
        if (Build.VERSION.SDK_INT >= 33 && editing != show) {
            OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
            if (show) {
                dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, editorBack);
            } else {
                dispatcher.unregisterOnBackInvokedCallback(editorBack);
            }
        }
        editing = show;
        backToApps.setVisibility(show ? View.VISIBLE : View.GONE);
        picker.setVisibility(show ? View.GONE : View.VISIBLE);
        editor.setVisibility(show ? View.VISIBLE : View.GONE);
        footer.setVisibility(show ? View.VISIBLE : View.GONE);
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private void returnToPicker() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(editor.getWindowToken(), 0);
        editor.clearFocus();
        setEditing(false);
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
                    maskFingerprint.isChecked(), maskUsage.isChecked(), deviceId, maskLocation.isChecked(),
                    latitude.getText().toString(), longitude.getText().toString());
            Map<String, String> simValues = new HashMap<>();
            for (String key : SimProfile.KEYS) simValues.put(key, simFields.get(key).getText().toString());
            profile.sim = new SimProfile(simValues);
            if (profile.maskSim) profile.sim.validate(profile.carrier);
            if (profile.enabled && !profile.applies()) {
                showStatus("请至少选择一项要隐藏或替换的信息。", true);
                return;
            }
            if (profile.save(profileApplication().preferences(loadedPackage))) {
                profileApplication().rememberConfiguredApp(loadedPackage);
                refreshCatalog();
                populate(profile.enabled, profile.maskSim, profile.carrier.country,
                        profile.language, profile.timeZone, profile.maskFingerprint, profile.maskUsage,
                        profile.maskLocation, profile.latitude, profile.longitude);
                populateSim(profile.sim);
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
        if (resumed) refreshCatalog();
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
        regionPicker.setEnabled(available);
        maskSim.setEnabled(available);
        carrier.setEnabled(available);
        for (EditText field : simFields.values()) field.setEnabled(available);
        language.setEnabled(available);
        timeZone.setEnabled(available);
        maskFingerprint.setEnabled(available);
        maskUsage.setEnabled(available);
        maskLocation.setEnabled(available);
        cityPicker.setEnabled(available);
        latitude.setEnabled(available);
        longitude.setEnabled(available);
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
        card.setBackground(rounded(R.color.surface, 6, R.color.outline));
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
        view.setTypeface(Typeface.create("serif", Typeface.BOLD));
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

    private EditText field(LinearLayout parent, String label, String hint, int margin) {
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
        add(parent, caption, margin);
        add(parent, view, 5);
        return view;
    }

    private EditText coordinateField(LinearLayout parent, String label, String hint, int margin) {
        EditText view = field(parent, label, hint, margin);
        view.setKeyListener(DigitsKeyListener.getInstance(Locale.US, true, true));
        view.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                markEdited();
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
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
