package io.github.appprivacyprofile;

enum CarrierProfile {
    US("us", "美国", "T-Mobile", "310260", 1),
    JP("jp", "日本", "NTT DOCOMO", "44010", 850),
    TW("tw", "台湾", "中華電信", "46692", 1884),
    HK("hk", "香港", "csl", "45400", 1526),
    GB("gb", "英国", "EE", "23430", 2);

    final String country;
    final String region;
    final String name;
    final String operator;
    final String mcc;
    final String mnc;
    final int id;

    CarrierProfile(String country, String region, String name, String operator, int id) {
        this.country = country;
        this.region = region;
        this.name = name;
        this.operator = operator;
        mcc = operator.substring(0, 3);
        mnc = operator.substring(3);
        this.id = id;
    }

    static CarrierProfile forCountry(String country) {
        for (CarrierProfile carrier : values()) {
            if (carrier.country.equalsIgnoreCase(country)) {
                return carrier;
            }
        }
        return US;
    }
}
