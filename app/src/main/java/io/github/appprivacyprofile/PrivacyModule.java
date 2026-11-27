package io.github.appprivacyprofile;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Process;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

public final class PrivacyModule extends XposedModule {
    private static final String TAG = "AppPrivacyProfile";
    private String processName = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam process) {
        if (!process.isSystemServer()) {
            processName = process.getProcessName();
            Log.i(TAG, "模块已进入进程 " + processName + " pid=" + Process.myPid());
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam app) {
        String packageName = app.getPackageName();
        if (!app.isFirstPackage() || app.getApplicationInfo().uid % 100000 < 10000
                || !Profile.isTargetPackage(packageName)) {
            return;
        }

        Profile profile;
        try {
            profile = Profile.read(getRemotePreferences(Profile.preferencesName(packageName)));
        } catch (RuntimeException e) {
            Log.w(TAG, "无法读取配置 " + packageName + " " + processName + " " + e.getClass().getSimpleName());
            return;
        }
        if (!profile.applies()) {
            if (profile.maskLocation) {
                Log.i(TAG, "定位未启用，应用总开关已关闭 " + packageName);
            }
            return;
        }

        if (profile.maskLocation) {
            double latitude = Double.parseDouble(profile.latitude);
            double longitude = Double.parseDouble(profile.longitude);
            maskLocation(packageName, latitude, longitude);
            if ("com.autonavi.minimap".equals(packageName)) {
                maskAmapLocation(app.getClassLoader(), latitude, longitude);
                maskAmapGpsOverlay(app.getClassLoader(), latitude, longitude);
            }
        }
        Locale locale = profile.language.isEmpty() ? null : Locale.forLanguageTag(profile.language);
        if (profile.maskSim) {
            maskSimIdentifiers(profile.sim);
            replaceCarrier(profile.carrier, profile.sim);
        }
        if (locale != null) {
            maskLanguage(locale);
        }
        if (!profile.timeZone.isEmpty()) {
            maskTimeZone(profile.timeZone);
        }
        if (profile.maskSim || locale != null) {
            maskResourceConfiguration(profile, locale);
        }
        if (profile.maskSim || locale != null || !profile.timeZone.isEmpty()) {
            maskSystemProperties(profile, locale);
        }
        if (profile.maskFingerprint) {
            maskFingerprint(profile.deviceId, app.getClassLoader());
        }
        if (profile.maskUsage) {
            maskUsage();
        }
        log(Log.INFO, TAG, "已加载配置 " + packageName);
    }

    private void maskSimIdentifiers(SimProfile sim) {
        hook(TelephonyManager.class, result(sim.get("imsi")), "getSubscriberId");
        hook(TelephonyManager.class, result(sim.get("iccid")), "getSimSerialNumber");
        hook(TelephonyManager.class, result(sim.get("number")), "getLine1Number", "getMsisdn");
        hook(TelephonyManager.class, result(null), "getLine1AlphaTag");
        hook(TelephonyManager.class, result(sim.get("gid1")), "getGroupIdLevel1");
        hook(TelephonyManager.class, result(sim.get("gid2")), "getGroupIdLevel2");
        hook(SubscriptionInfo.class, result(sim.get("iccid")), "getIccId", "getCardString");
        hook(SubscriptionInfo.class, result(sim.get("number")), "getNumber");
        hook(SubscriptionManager.class, result(sim.get("number")), "getPhoneNumber");
        hook(frameworkClass("android.telephony.UiccCardInfo"), result(sim.get("iccid")), "getIccId");
        hook(frameworkClass("android.telephony.UiccPortInfo"), result(sim.get("iccid")), "getIccId");
        hook(frameworkClass("android.telephony.UiccCardInfo"), result(null), "getEid");
        hook(frameworkClass("android.telephony.euicc.EuiccManager"), result(null), "getEid");
    }

    private void replaceCarrier(CarrierProfile carrier, SimProfile sim) {
        ResultFilter countryCode = replaceText(sim.country(carrier));
        ResultFilter operator = replaceText(sim.operator(carrier));
        ResultFilter name = replaceText(sim.name(carrier));
        ResultFilter carrierId = (chain, original) -> (int) original >= 0 ? (sim.get("operator").isEmpty() ? carrier.id : -1) : original;
        hook(TelephonyManager.class, countryCode, "getSimCountryIso", "getSimCountryIsoForPhone",
                "getNetworkCountryIso", "getNetworkCountryIsoForPhone");
        hook(TelephonyManager.class, operator, "getSimOperator", "getSimOperatorNumeric",
                "getSimOperatorNumericForPhone", "getNetworkOperator", "getNetworkOperatorForPhone");
        hook(TelephonyManager.class, name, "getSimOperatorName", "getSimOperatorNameForPhone",
                "getNetworkOperatorName",
                "getNetworkOperatorNameForPhone", "getSimCarrierIdName", "getSimSpecificCarrierIdName");
        hook(TelephonyManager.class, carrierId, "getSimCarrierId", "getSimSpecificCarrierId",
                "getCarrierIdFromSimMccMnc");

        hook(SubscriptionInfo.class, countryCode, "getCountryIso");
        hook(SubscriptionInfo.class, name, "getCarrierName", "getDisplayName");
        hook(SubscriptionInfo.class, replaceText(sim.mcc(carrier)), "getMccString");
        hook(SubscriptionInfo.class, replaceText(sim.mnc(carrier)), "getMncString");
        hook(SubscriptionInfo.class, replaceCode(sim.mcc(carrier)), "getMcc");
        hook(SubscriptionInfo.class, replaceCode(sim.mnc(carrier)), "getMnc");
        hook(SubscriptionInfo.class, carrierId, "getCarrierId");

        hook(ServiceState.class, operator, "getOperatorNumeric", "getVoiceOperatorNumeric", "getDataOperatorNumeric");
        hook(ServiceState.class, name, "getOperatorAlphaLong", "getOperatorAlphaShort",
                "getVoiceOperatorAlphaLong", "getVoiceOperatorAlphaShort", "getDataOperatorAlphaLong",
                "getDataOperatorAlphaShort", "getOperatorAlphaLongRaw", "getOperatorAlphaShortRaw");
        hook(frameworkClass("android.telephony.NetworkRegistrationInfo"), operator, "getRegisteredPlmn");
    }

    private static ResultFilter replaceText(String replacement) {
        return (chain, original) -> original instanceof CharSequence && ((CharSequence) original).length() > 0
                ? replacement : original;
    }

    private static ResultFilter replaceCode(String replacement) {
        int code = Integer.parseInt(replacement);
        return (chain, original) -> (int) original != 0 ? code : original;
    }

    private void maskLanguage(Locale locale) {
        LocaleList locales = new LocaleList(locale);
        hook(Locale.class, result(locale), "getDefault");
        hook(LocaleList.class, result(locales), "getDefault", "getAdjustedDefault");
        hook(android.icu.util.ULocale.class, result(android.icu.util.ULocale.forLocale(locale)), "getDefault");
        hook(frameworkClass("android.app.LocaleManager"), result(locales), "getSystemLocales");

        hook(System.class, (chain, original) -> {
            if (!(chain.getArg(0) instanceof String)) {
                return original;
            }
            switch ((String) chain.getArg(0)) {
                case "user.language": return locale.getLanguage();
                case "user.country":
                case "user.region": return locale.getCountry();
                case "user.script": return locale.getScript();
                case "user.variant": return locale.getVariant();
                default: return original;
            }
        }, "getProperty");
    }

    private void maskTimeZone(String id) {
        TimeZone javaZone = TimeZone.getTimeZone(id);
        android.icu.util.TimeZone icuZone = android.icu.util.TimeZone.getTimeZone(id);
        hook(ZoneId.class, result(ZoneId.of(id)), "systemDefault");
        hook(TimeZone.class, (chain, original) -> javaZone.clone(), "getDefault", "getDefaultRef");
        hook(android.icu.util.TimeZone.class, (chain, original) -> icuZone.clone(), "getDefault");

        try {
            System.loadLibrary("appprivacy_timezone");
            int result = configureNativeTimeZone(id);
            int propertyHookCount = result >>> 1;
            boolean localTimeConfigured = (result & 1) != 0;
            log(localTimeConfigured && propertyHookCount == 3 ? Log.INFO : Log.WARN, TAG,
                    "原生时区 " + id + "，本地时间" + (localTimeConfigured ? "已设置" : "设置失败")
                            + "，已挂钩 " + propertyHookCount + "/3 个属性接口");
        } catch (LinkageError | RuntimeException e) {
            log(Log.WARN, TAG, "无法加载原生时区支持 " + e.getClass().getSimpleName());
        }
    }

    private static synchronized native int configureNativeTimeZone(String id);

    private void maskResourceConfiguration(Profile profile, Locale locale) {
        CarrierProfile carrier = profile.maskSim ? profile.carrier : null;
        LocaleList locales = locale == null ? null : new LocaleList(locale);
        int mcc = carrier == null ? 0 : Integer.parseInt(profile.sim.mcc(carrier));
        int mnc = carrier == null ? 0 : Integer.parseInt(profile.sim.mnc(carrier));
        hook(Resources.class, (chain, value) -> {
            if (!(value instanceof Configuration)) {
                return value;
            }
            Configuration original = (Configuration) value;
            boolean hasCarrier = carrier != null && (original.mcc != 0 || original.mnc != 0);
            boolean hasOtherLocales = locales != null && !locales.equals(original.getLocales());
            if (!hasCarrier && !hasOtherLocales) {
                return original;
            }
            Configuration masked = new Configuration(original);
            if (hasCarrier) {
                masked.mcc = original.mcc == 0 ? 0 : mcc;
                masked.mnc = original.mnc == 0 ? 0 : mnc;
            }
            if (hasOtherLocales) {
                masked.setLocales(locales);
            }
            return masked;
        }, "getConfiguration");
    }

    private void maskSystemProperties(Profile profile, Locale locale) {
        hook(frameworkClass("android.os.SystemProperties"), (chain, original) -> {
            if (!(chain.getArg(0) instanceof String) || !(original instanceof String)) {
                return original;
            }
            switch ((String) chain.getArg(0)) {
                case "gsm.sim.operator.iso-country":
                case "gsm.operator.iso-country":
                    return profile.maskSim ? replaceSlots((String) original, profile.sim.country(profile.carrier)) : original;
                case "gsm.sim.operator.alpha":
                case "gsm.operator.alpha":
                    return profile.maskSim ? replaceSlots((String) original, profile.sim.name(profile.carrier)) : original;
                case "gsm.sim.operator.numeric":
                case "gsm.operator.numeric":
                    return profile.maskSim ? replaceSlots((String) original, profile.sim.operator(profile.carrier)) : original;
                case "persist.sys.locale":
                    return locale != null ? profile.language : original;
                case "persist.sys.language":
                    return locale != null ? locale.getLanguage() : original;
                case "persist.sys.country":
                    return locale != null ? locale.getCountry() : original;
                case "persist.sys.timezone":
                    return profile.timeZone.isEmpty() ? original : profile.timeZone;
                default:
                    return original;
            }
        }, "get");
    }

    private static String replaceSlots(String original, String replacement) {
        String[] slots = original.split(",", -1);
        for (int i = 0; i < slots.length; i++) {
            if (!slots[i].isEmpty()) {
                slots[i] = replacement;
            }
        }
        return String.join(",", slots);
    }

    private void maskFingerprint(String deviceId, ClassLoader appLoader) {
        if (!deviceId.isEmpty()) {
            hook(Settings.Secure.class, (chain, original) ->
                    original instanceof String && Settings.Secure.ANDROID_ID.equals(chain.getArg(1))
                            ? deviceId : original, "getString", "getStringForUser");
        }
        hook(WifiInfo.class, result("<unknown ssid>"), "getSSID");
        hook(WifiInfo.class, result("02:00:00:00:00:00"), "getBSSID", "getMacAddress");
        hook(WifiInfo.class, result(null), "getPasspointFqdn", "getPasspointProviderFriendlyName");
        hook(BluetoothAdapter.class, result("02:00:00:00:00:00"), "getAddress");
        hook(BluetoothAdapter.class, result(null), "getName");
        Class<?> adInfo = findClass("com.google.android.gms.ads.identifier.AdvertisingIdClient$Info", appLoader);
        hook(adInfo, result("00000000-0000-0000-0000-000000000000"), "getId");
        hook(adInfo, result(true), "isLimitAdTrackingEnabled");
    }

    private void maskUsage() {
        hook(UsageStatsManager.class, (chain, original) -> original == null ? null : new ArrayList<>(),
                "queryUsageStats", "queryEventStats", "queryConfigurations");
        hook(UsageStatsManager.class, (chain, original) -> original == null ? null : new HashMap<>(),
                "queryAndAggregateUsageStats");
        try {
            Constructor<UsageEvents> emptyEvents = UsageEvents.class.getDeclaredConstructor();
            emptyEvents.setAccessible(true);
            emptyEvents.newInstance();
            hook(UsageStatsManager.class, (chain, original) -> original == null ? null : emptyEvents.newInstance(),
                    "queryEvents", "queryEventsForSelf");
        } catch (ReflectiveOperationException | RuntimeException e) {
            hook(UsageEvents.class, result(false), "hasNextEvent", "getNextEvent");
            log(Log.INFO, TAG, "使用事件已切换为屏蔽迭代读取");
        }
    }

    private void maskLocation(String packageName, double latitude, double longitude) {
        String process = (processName.isEmpty() ? packageName : processName) + " pid=" + Process.myPid();
        Log.i(TAG, "已读取虚拟定位配置 " + process);

        LocationReplacement cached = new LocationReplacement(process, "缓存定位", latitude, longitude);
        cached.install(LocationManager.class, "getLastKnownLocation",
                chain -> cached.replace((Location) chain.proceed()), String.class);

        if (Build.VERSION.SDK_INT >= 30) {
            LocationReplacement current = new LocationReplacement(process, "单次定位回调", latitude, longitude);
            current.install(frameworkClass("android.location.LocationManager$GetCurrentLocationTransport"),
                    "onLocation", chain -> chain.proceed(new Object[]{current.replace((Location) chain.getArg(0))}),
                    Location.class);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            LocationReplacement updates = new LocationReplacement(process, "持续定位回调", latitude, longitude);
            updates.install(frameworkClass("android.location.LocationManager$LocationListenerTransport"),
                    "onLocationChanged", chain -> {
                        @SuppressWarnings("unchecked")
                        List<Location> locations = (List<Location>) chain.getArg(0);
                        if (locations == null || locations.isEmpty()) {
                            updates.reportEmpty();
                            return chain.proceed();
                        }
                        List<Location> replacements = new ArrayList<>(locations.size());
                        for (Location location : locations) {
                            replacements.add(updates.replace(location));
                        }
                        return chain.proceed(new Object[]{replacements, chain.getArg(1)});
                    }, List.class, frameworkClass("android.os.IRemoteCallback"));
        }

        // Keep covering Parcelable deliveries, including providers outside LocationManager.
        LocationReplacement parcel = new LocationReplacement(process, "Parcel 定位", latitude, longitude);
        parcel.install(Location.CREATOR.getClass(), "createFromParcel",
                chain -> parcel.replace((Location) chain.proceed()), Parcel.class);
    }

    private void maskAmapLocation(ClassLoader loader, double latitude, double longitude) {
        String prefix = "com.amap.location.sdkh.";
        Class<?> callbacks = findClass(prefix + "AmapLocationService$NativeLocationCallback", loader);
        Class<?> location = findClass(prefix + "base.type.location.AmapLocation", loader);
        Class<?> matched = findClass(prefix + "base.type.location.AmapLocationMatch", loader);
        if (callbacks == null || location == null) {
            Log.i(TAG, "高德定位 SDK 回调不可用 " + processName);
            return;
        }
        try {
            Method valid = location.getMethod("isLocationCorrect", location);
            Method setLatitude = location.getMethod("setLatitude", double.class);
            Method setLongitude = location.getMethod("setLongitude", double.class);
            Method setMapCoordinates = location.getMethod("setCoorCanUseInMap", boolean.class);
            Method getCode = location.getMethod("getLocCode");
            Method setCode = location.getMethod("setLocCode", int.class);
            Method getContent = location.getMethod("getOptContent");
            Method setContent = location.getMethod("setOptContent", String.class);
            Field mapLatitude = location.getField("gcjLatitude");
            Field mapLongitude = location.getField("gcjLongitude");
            Object empty = location.getConstructor().newInstance();
            double emptyMapLatitude = mapLatitude.getDouble(empty);
            double emptyMapLongitude = mapLongitude.getDouble(empty);
            AmapPosition replacement = (source, copy) -> {
                if (!Boolean.TRUE.equals(valid.invoke(null, source))) return source;
                Object fixed = copy.newInstance(source);
                setCode.invoke(fixed, getCode.invoke(source));
                setContent.invoke(fixed, getContent.invoke(source));
                setLatitude.invoke(fixed, latitude);
                setLongitude.invoke(fixed, longitude);
                setMapCoordinates.invoke(fixed, false);
                // Discard the SDK's cached GCJ-02 pair so it offsets the configured WGS-84 point.
                mapLatitude.setDouble(fixed, emptyMapLatitude);
                mapLongitude.setDouble(fixed, emptyMapLongitude);
                return fixed;
            };
            installAmapCallback(callbacks, "onBaseLocationChanged", location, replacement);
            installAmapCallback(callbacks, "onNetworkLocationChanged", location, replacement);
            if (matched != null) installAmapCallback(callbacks, "onMatchLocationChanged", matched, replacement);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.w(TAG, "高德定位 SDK 类型不兼容 " + processName + " " + e.getClass().getSimpleName());
        }
    }

    private void installAmapCallback(Class<?> callbacks, String name, Class<?> position, AmapPosition replacement) {
        String diagnostic = name + " " + processName + " pid=" + Process.myPid();
        try {
            Constructor<?> copy = position.getConstructor(position);
            Method method = callbacks.getDeclaredMethod(name, position);
            AtomicBoolean reported = new AtomicBoolean();
            AtomicBoolean reportedFailure = new AtomicBoolean();
            hook(method).intercept(chain -> {
                Object source = chain.getArg(0);
                if (source == null) return chain.proceed();
                Object fixed;
                try {
                    fixed = replacement.replace(source, copy);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    if (reportedFailure.compareAndSet(false, true)) {
                        Log.w(TAG, "高德定位坐标替换失败 " + diagnostic + " " + e.getClass().getSimpleName());
                    }
                    return chain.proceed();
                }
                if (fixed == source) return chain.proceed();
                if (reported.compareAndSet(false, true)) Log.i(TAG, "已替换高德定位坐标 " + diagnostic);
                return chain.proceed(new Object[]{fixed});
            });
            Log.i(TAG, "已挂钩高德定位回调 " + diagnostic);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.w(TAG, "无法挂钩高德定位回调 " + diagnostic + " " + e.getClass().getSimpleName());
        }
    }

    private interface AmapPosition {
        Object replace(Object source, Constructor<?> copy) throws ReflectiveOperationException;
    }

    private void maskAmapGpsOverlay(ClassLoader loader, double latitude, double longitude) {
        Class<?> business = findClass("com.autonavi.jni.vmap.business.VMapBusinessManager", loader);
        Class<?> offset = findClass("com.amap.location.sdkh.base.tools.geo.OffsetUtils", loader);
        if (business == null || offset == null) return;

        try {
            Method convert = offset.getMethod("getOffsetLatlng", double.class, double.class);
            Method update = business.getDeclaredMethod("updateBusinessLocation",
                    double.class, double.class, float.class, int.class);
            Method automatic = business.getDeclaredMethod("enableAutomaticGetLocation", boolean.class);
            AtomicBoolean seeded = new AtomicBoolean();
            hook(update).intercept(chain -> {
                double[] coordinates;
                try {
                    coordinates = (double[]) convert.invoke(null, latitude, longitude);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    Log.w(TAG, "无法转换高德蓝点坐标 " + processName + " " + e.getClass().getSimpleName());
                    return chain.proceed();
                }
                if (coordinates == null || coordinates.length != 2) return chain.proceed();
                Object result = chain.proceed(new Object[]{coordinates[1], coordinates[0], chain.getArg(2), chain.getArg(3)});
                if (seeded.compareAndSet(false, true)) Log.i(TAG, "已提交高德蓝点坐标 " + processName);
                return result;
            });
            // The native GPS overlay can read positioning directly after its first explicit update.
            hook(automatic).intercept(chain -> {
                if (!seeded.get() || !Boolean.TRUE.equals(chain.getArg(0))) return chain.proceed();
                Log.i(TAG, "高德蓝点沿用设定坐标 " + processName);
                return chain.proceed(new Object[]{false});
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Log.w(TAG, "无法挂钩高德蓝点 " + processName + " " + e.getClass().getSimpleName());
        }
    }

    private final class LocationReplacement {
        private final String diagnostic;
        private final double latitude;
        private final double longitude;
        private final AtomicBoolean reportedEmpty = new AtomicBoolean();
        private final AtomicBoolean reportedReplacement = new AtomicBoolean();

        LocationReplacement(String process, String path, double latitude, double longitude) {
            this.diagnostic = path + " " + process;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        Location replace(Location source) {
            if (source == null) {
                reportEmpty();
                return null;
            }
            Location fixed = new Location(source.getProvider());
            fixed.setLatitude(latitude);
            fixed.setLongitude(longitude);
            fixed.setTime(source.getTime());
            fixed.setElapsedRealtimeNanos(source.getElapsedRealtimeNanos());
            if (source.hasAccuracy()) {
                fixed.setAccuracy(source.getAccuracy());
            }
            if (Build.VERSION.SDK_INT >= 31) {
                fixed.setMock(source.isMock());
            }
            if (reportedReplacement.compareAndSet(false, true)) {
                Log.i(TAG, "已替换坐标 " + diagnostic);
            }
            return fixed;
        }

        void reportEmpty() {
            if (reportedEmpty.compareAndSet(false, true)) {
                Log.i(TAG, "未收到定位结果 " + diagnostic);
            }
        }

        void install(Class<?> owner, String name, Hooker hooker, Class<?>... parameters) {
            if (owner == null) {
                Log.w(TAG, "定位入口不存在 " + diagnostic);
                return;
            }
            try {
                hook(owner.getDeclaredMethod(name, parameters)).intercept(hooker);
                Log.i(TAG, "已挂钩 " + diagnostic);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Log.w(TAG, "无法挂钩 " + diagnostic + " " + e.getClass().getSimpleName());
            }
        }
    }

    private interface ResultFilter {
        Object filter(Chain chain, Object original) throws Throwable;
    }

    private static ResultFilter result(Object replacement) {
        return (chain, original) -> replacement;
    }

    private static Class<?> frameworkClass(String name) {
        return findClass(name, null);
    }

    private static Class<?> findClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private void hook(Class<?> owner, ResultFilter filter, String... methods) {
        if (owner == null) {
            return;
        }
        Method[] declared;
        try {
            declared = owner.getDeclaredMethods();
        } catch (RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "无法枚举方法 " + owner.getName());
            return;
        }
        for (Method method : declared) {
            for (String name : methods) {
                if (method.getName().equals(name)) {
                    try {
                        hook(method).intercept(chain -> filter.filter(chain, chain.proceed()));
                    } catch (RuntimeException | LinkageError e) {
                        log(Log.WARN, TAG, "无法挂钩 " + owner.getName() + "." + name);
                    }
                    break;
                }
            }
        }
    }
}
