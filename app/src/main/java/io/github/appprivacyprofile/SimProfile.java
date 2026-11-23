package io.github.appprivacyprofile;

import android.content.SharedPreferences;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Optional per-app SIM values. Empty identifiers remain hidden. */
final class SimProfile {
    static final String[] KEYS = {"name", "operator", "iso", "imsi", "iccid", "number", "gid1", "gid2"};
    final Map<String, String> values = new LinkedHashMap<>();

    SimProfile(Map<String, String> input) {
        for (String key : KEYS) values.put(key, input.getOrDefault(key, "").trim());
        values.put("iso", get("iso").toLowerCase(Locale.ROOT));
        check("operator", "[0-9]{5,6}", "MCC/MNC 必须为 5 或 6 位数字");
        check("iso", "[a-z]{2}", "国家码必须为两位英文字母");
        check("imsi", "[0-9]{14,15}", "IMSI 必须为 14 或 15 位数字");
        check("iccid", "[0-9]{18,22}", "ICCID 必须为 18 至 22 位数字");
        check("number", "[+]?[0-9]{3,15}", "手机号请输入可选的 + 和 3 至 15 位数字");
        check("gid1", "[0-9a-fA-F]{1,16}", "GID1 请输入最多 16 位十六进制字符");
        check("gid2", "[0-9a-fA-F]{1,16}", "GID2 请输入最多 16 位十六进制字符");
        if (get("name").length() > 64) throw new IllegalArgumentException("运营商名称最多 64 个字符");
    }

    static SimProfile read(SharedPreferences prefs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : KEYS) values.put(key, prefs.getString("sim_" + key, ""));
        return new SimProfile(values);
    }

    void validate(CarrierProfile carrier) {
        if (!get("imsi").isEmpty() && !get("imsi").startsWith(operator(carrier))) {
            throw new IllegalArgumentException("IMSI 前缀必须与当前 MCC/MNC 运营商代码一致。");
        }
    }

    void write(SharedPreferences.Editor editor) {
        for (String key : KEYS) editor.putString("sim_" + key, get(key));
    }

    String get(String key) { return values.get(key); }
    String name(CarrierProfile carrier) { return fallback("name", carrier.name); }
    String operator(CarrierProfile carrier) { return fallback("operator", carrier.operator); }
    String country(CarrierProfile carrier) { return fallback("iso", carrier.country); }
    String mcc(CarrierProfile carrier) { return operator(carrier).substring(0, 3); }
    String mnc(CarrierProfile carrier) { return operator(carrier).substring(3); }
    private String fallback(String key, String preset) { return get(key).isEmpty() ? preset : get(key); }
    private void check(String key, String pattern, String message) {
        if (!get(key).isEmpty() && !get(key).matches(pattern)) throw new IllegalArgumentException(message);
    }
}
