package io.github.appprivacyprofile;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Standalone regression checks; compile with android.jar, run without a device. */
public final class SimProfileChecks {
    static void check(boolean condition) { if (!condition) throw new AssertionError(); }
    static void rejects(Map<String, String> values) {
        try { new SimProfile(values); throw new AssertionError("Accepted invalid data"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) {
        SimProfile empty = new SimProfile(Map.of());
        check(empty.operator(CarrierProfile.US).equals("310260"));
        check(empty.get("imsi").isEmpty());
        SimProfile custom = new SimProfile(Map.of("name", "Test Carrier", "operator", "00101",
                "iso", "US", "imsi", "001010123456789", "iccid", "8900101234567890123",
                "number", "+12025550123", "gid1", "0a", "gid2", "FF"));
        custom.validate(CarrierProfile.US);
        check(custom.mcc(CarrierProfile.US).equals("001"));
        check(custom.mnc(CarrierProfile.US).equals("01"));
        check(custom.country(CarrierProfile.US).equals("us"));
        rejects(Map.of("operator", "1234"));
        rejects(Map.of("imsi", "123"));
        rejects(Map.of("iccid", "invalid"));
        rejects(Map.of("number", "+123 456"));
        rejects(Map.of("gid1", "ZZ"));
        rejects(Map.of("iso", "USA"));
        rejects(Map.of("name", "x".repeat(65)));
        try { new SimProfile(Map.of("imsi", "440100123456789")).validate(CarrierProfile.US);
            throw new AssertionError("Accepted inconsistent IMSI");
        } catch (IllegalArgumentException expected) { }
        Map<String, Object> storage = new HashMap<>();
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                SimProfileChecks.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, arguments) -> {
                    if (method.getName().startsWith("put")) { storage.put((String) arguments[0], arguments[1]); return proxy; }
                    if (method.getName().equals("commit")) return true;
                    return null;
                });
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SimProfileChecks.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, arguments) -> method.getName().equals("edit") ? editor
                        : storage.getOrDefault((String) arguments[0], arguments[1]));
        Profile profile = new Profile(true, true, "us", "", "", false, false, "");
        profile.sim = custom;
        check(profile.save(prefs));
        Profile restored = Profile.read(prefs);
        check(restored.sim.values.equals(custom.values));
        check(restored.enabled && restored.maskSim);
        storage.clear();
        check(Profile.read(prefs).sim.get("iccid").isEmpty());
        System.out.println("PASS: defaults, validation, leading zeros, profile persistence, legacy migration");
    }
}
