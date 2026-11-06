package io.github.appprivacyprofile;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.os.LocaleList;
import android.os.Parcel;
import android.provider.Settings;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import io.github.libxposed.api.XposedModule;

public final class PrivacyModule extends XposedModule {
    private static final String TAG = "AppPrivacyProfile";

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
            log(Log.WARN, TAG, "无法读取配置 " + packageName);
            return;
        }
        if (!profile.applies()) {
            return;
        }

        Locale locale = profile.language.isEmpty() ? null : Locale.forLanguageTag(profile.language);
        if (profile.maskSim) {
            maskSimIdentifiers();
            replaceCarrier(profile.carrier);
        }
        if (locale != null) {
            maskLanguage(locale);
        }
        if (!profile.timeZone.isEmpty()) {
            maskTimeZone(profile.timeZone);
        }
        if (profile.maskSim || locale != null) {
            maskResourceConfiguration(profile.maskSim ? profile.carrier : null, locale);
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
        if (profile.maskLocation) {
            maskLocation(Double.parseDouble(profile.latitude), Double.parseDouble(profile.longitude));
        }
        log(Log.INFO, TAG, "已加载配置 " + packageName);
    }

    private void maskSimIdentifiers() {
        ResultFilter hideString = result("");
        ResultFilter hideNullableString = result(null);
        hook(TelephonyManager.class, hideNullableString, "getSubscriberId", "getSimSerialNumber",
                "getLine1Number", "getLine1AlphaTag", "getMsisdn", "getGroupIdLevel1", "getGroupIdLevel2");
        hook(SubscriptionInfo.class, hideString, "getIccId", "getNumber", "getCardString");
        hook(SubscriptionManager.class, hideString, "getPhoneNumber");
        hook(frameworkClass("android.telephony.UiccCardInfo"), hideNullableString, "getIccId", "getEid");
        hook(frameworkClass("android.telephony.UiccPortInfo"), hideString, "getIccId");
        hook(frameworkClass("android.telephony.euicc.EuiccManager"), hideNullableString, "getEid");
    }

    private void replaceCarrier(CarrierProfile carrier) {
        ResultFilter countryCode = replaceText(carrier.country);
        ResultFilter operator = replaceText(carrier.operator);
        ResultFilter name = replaceText(carrier.name);
        ResultFilter carrierId = (chain, original) -> (int) original >= 0 ? carrier.id : original;
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
        hook(SubscriptionInfo.class, replaceText(carrier.mcc), "getMccString");
        hook(SubscriptionInfo.class, replaceText(carrier.mnc), "getMncString");
        hook(SubscriptionInfo.class, replaceCode(carrier.mcc), "getMcc");
        hook(SubscriptionInfo.class, replaceCode(carrier.mnc), "getMnc");
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
    }

    private void maskResourceConfiguration(CarrierProfile carrier, Locale locale) {
        LocaleList locales = locale == null ? null : new LocaleList(locale);
        int mcc = carrier == null ? 0 : Integer.parseInt(carrier.mcc);
        int mnc = carrier == null ? 0 : Integer.parseInt(carrier.mnc);
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
                    return profile.maskSim ? replaceSlots((String) original, profile.carrier.country) : original;
                case "gsm.sim.operator.alpha":
                case "gsm.operator.alpha":
                    return profile.maskSim ? replaceSlots((String) original, profile.carrier.name) : original;
                case "gsm.sim.operator.numeric":
                case "gsm.operator.numeric":
                    return profile.maskSim ? replaceSlots((String) original, profile.carrier.operator) : original;
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

    private void maskLocation(double latitude, double longitude) {
        ResultFilter replaceLocation = (chain, original) -> {
            if (!(original instanceof Location)) {
                return original;
            }
            Location source = (Location) original;
            Location fixed = new Location(source.getProvider());
            fixed.setLatitude(latitude);
            fixed.setLongitude(longitude);
            fixed.setTime(source.getTime());
            fixed.setElapsedRealtimeNanos(source.getElapsedRealtimeNanos());
            if (source.hasAccuracy()) {
                fixed.setAccuracy(source.getAccuracy());
            }
            return fixed;
        };
        // Framework and fused location services deliver Location objects through Parcel.
        Class<?> creator = Location.CREATOR.getClass();
        try {
            Method method = creator.getDeclaredMethod("createFromParcel", Parcel.class);
            hook(method).intercept(chain -> replaceLocation.filter(chain, chain.proceed()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            log(Log.WARN, TAG, "无法挂钩 " + creator.getName() + ".createFromParcel");
        }
        hook(LocationManager.class, replaceLocation, "getLastKnownLocation");
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
