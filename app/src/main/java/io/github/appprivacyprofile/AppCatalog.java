package io.github.appprivacyprofile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.util.Log;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.service.XposedService;

final class AppCatalog {
    final Map<String, Entry> byPackage = new HashMap<>();
    final List<Entry> installed = new ArrayList<>();
    final List<Entry> configured = new ArrayList<>();
    // A failed or unavailable scope query is distinct from an empty scope.
    private Set<String> scope;
    boolean configurationScanComplete;
    boolean installedListUnavailable;

    static AppCatalog load(PackageManager packages, LauncherApps launcher, XposedService service,
                           SharedPreferences savedApps) {
        AppCatalog result = new AppCatalog();
        if (service != null) {
            try {
                result.scope = new HashSet<>(service.getScope());
            } catch (RuntimeException ignored) {
                // Keep the package list usable when the framework cannot report scope.
            }
        }

        Map<String, ApplicationInfo> installed = new HashMap<>();
        for (ApplicationInfo app : packages.getInstalledApplications(0)) {
            addApp(installed, app);
        }
        for (ResolveInfo activity : packages.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            addApp(installed, activity.activityInfo.applicationInfo);
        }
        if (installed.isEmpty() && launcher != null) {
            try {
                for (LauncherActivityInfo activity : launcher.getActivityList(null, Process.myUserHandle())) {
                    addApp(installed, activity.getApplicationInfo());
                }
            } catch (SecurityException exception) {
                Log.w("AppPrivacyProfile", "启动器应用列表不可用", exception);
            }
        }
        result.installedListUnavailable = installed.isEmpty();
        Set<String> saved = savedApps.getAll().keySet();
        Set<String> candidates = new HashSet<>(installed.keySet());
        candidates.addAll(saved);
        if (result.scope != null) candidates.addAll(result.scope);

        result.configurationScanComplete = service != null;
        SharedPreferences.Editor index = savedApps.edit();
        boolean foundSavedApp = false;
        for (String name : candidates) {
            if (!Profile.isTargetPackage(name)) continue;
            ApplicationInfo app = installed.get(name);
            if (app == null) {
                try {
                    app = packages.getApplicationInfo(name, 0);
                } catch (PackageManager.NameNotFoundException ignored) {
                    // A package may be uninstalled or hidden by package visibility filtering.
                }
            }
            boolean configured = saved.contains(name);
            Boolean enabled = null;
            if (service != null) {
                try {
                    SharedPreferences remote = service.getRemotePreferences(Profile.preferencesName(name));
                    configured = remote.contains(Profile.KEY_ENABLED);
                    if (configured) {
                        if (!saved.contains(name)) {
                            index.putBoolean(name, true);
                            foundSavedApp = true;
                        }
                        enabled = remote.getBoolean(Profile.KEY_ENABLED, false);
                    }
                } catch (RuntimeException ignored) {
                    result.configurationScanComplete = false;
                }
            }
            boolean known = Boolean.TRUE.equals(result.inScope(name)) || configured;
            if (app == null && !known) continue;
            String label = app == null ? name : app.loadLabel(packages).toString();
            Entry entry = new Entry(name, label, app, configured, enabled);
            result.byPackage.put(name, entry);
            if (app != null || (result.installedListUnavailable && known)) result.installed.add(entry);
            if (configured) result.configured.add(entry);
        }
        if (foundSavedApp) index.apply();

        Collator collator = Collator.getInstance();
        Comparator<Entry> byLabel = (left, right) -> {
            int byName = collator.compare(left.label, right.label);
            return byName == 0 ? left.packageName.compareTo(right.packageName) : byName;
        };
        result.installed.sort(byLabel);
        result.configured.sort(byLabel);
        return result;
    }

    Boolean inScope(String packageName) {
        return scope == null ? null : scope.contains(packageName);
    }

    private static void addApp(Map<String, ApplicationInfo> apps, ApplicationInfo app) {
        if (app.uid % 100000 >= 10000 && Profile.isTargetPackage(app.packageName)) {
            apps.putIfAbsent(app.packageName, app);
        }
    }

    static final class Entry {
        final String packageName;
        final String label;
        final ApplicationInfo application;
        final boolean configured;
        final Boolean enabled;

        Entry(String packageName, String label, ApplicationInfo application, boolean configured, Boolean enabled) {
            this.packageName = packageName;
            this.label = label;
            this.application = application;
            this.configured = configured;
            this.enabled = enabled;
        }
    }
}
