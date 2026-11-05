package io.github.appprivacyprofile;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.json.JSONObject;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ProfileApplication extends Application {
    private XposedService service;
    private Runnable observer;
    private String connection = "等待 Modern LSPosed 连接（API 101+）";

    @Override
    public void onCreate() {
        super.onCreate();
        Handler main = new Handler(Looper.getMainLooper());
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override
            public void onServiceBind(XposedService bound) {
                main.post(() -> connect(bound));
            }

            @Override
            public void onServiceDied(XposedService dead) {
                main.post(() -> {
                    if (service == dead) {
                        service = null;
                        connection = "LSPosed 已断开，等待重新连接";
                        notifyObserver();
                    }
                });
            }
        });
    }

    private void connect(XposedService bound) {
        try {
            int api = bound.getApiVersion();
            if (api >= 101 && (bound.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) != 0) {
                service = bound;
                connection = "已连接 " + bound.getFrameworkName() + " · API " + api;
                importLegacyBackup();
            } else if (service == null) {
                connection = "当前框架 API " + api + "，需要 API 101+ 和远程配置支持";
            }
        } catch (RuntimeException e) {
            if (service == bound || service == null) {
                service = null;
                connection = "LSPosed 连接失败，请重新打开配置页";
            }
        }
        notifyObserver();
    }

    private void importLegacyBackup() {
        AtomicFile backup = new AtomicFile(new File(getFilesDir(), "legacy-profile-backup.json"));
        if (!backup.getBaseFile().isFile()) {
            return;
        }
        try {
            JSONObject profiles = new JSONObject(new String(backup.readFully(), StandardCharsets.UTF_8));
            for (Iterator<String> keys = profiles.keys(); keys.hasNext();) {
                String packageName = keys.next();
                if (!Profile.isTargetPackage(packageName)) {
                    continue;
                }
                SharedPreferences remote = preferences(packageName);
                if (remote.contains("enabled")) {
                    continue;
                }
                JSONObject legacy = profiles.getJSONObject(packageName);
                Profile profile = new Profile(legacy.optBoolean("enabled"), legacy.optBoolean("mask_sim"),
                        legacy.optString("country"), legacy.optString("language"), legacy.optString("time_zone"),
                        false, false, "");
                if (!profile.save(remote)) {
                    connection += " · 旧配置导入待重试";
                    return;
                }
            }
            backup.delete();
            connection += " · 已导入旧配置";
        } catch (Exception e) {
            Log.w("AppPrivacyProfile", "旧配置导入失败", e);
            connection += " · 旧配置导入待重试";
        }
    }

    void setObserver(Runnable listener) {
        observer = listener;
        notifyObserver();
    }

    private void notifyObserver() {
        if (observer != null) {
            observer.run();
        }
    }

    String connection() {
        return connection;
    }

    boolean isConnected() {
        return service != null;
    }

    SharedPreferences preferences(String packageName) {
        if (service == null) {
            throw new IllegalStateException(connection);
        }
        return service.getRemotePreferences(Profile.preferencesName(packageName));
    }
}
