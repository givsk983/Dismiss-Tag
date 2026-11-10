package io.github.appprivacyprofile;

enum RegionPreset {
    US(CarrierProfile.US, "en-US", "America/New_York", CityPreset.NEW_YORK),
    JP(CarrierProfile.JP, "ja-JP", "Asia/Tokyo", CityPreset.TOKYO),
    TW(CarrierProfile.TW, "zh-Hant-TW", "Asia/Taipei", CityPreset.TAIPEI),
    HK(CarrierProfile.HK, "zh-Hant-HK", "Asia/Hong_Kong", CityPreset.HONG_KONG),
    SINGAPORE(CarrierProfile.SG, "en-SG", "Asia/Singapore", CityPreset.SINGAPORE),
    BANGKOK(CarrierProfile.TH, "th-TH", "Asia/Bangkok", CityPreset.BANGKOK),
    CHIANG_MAI(CarrierProfile.TH, "th-TH", "Asia/Bangkok", CityPreset.CHIANG_MAI),
    PHUKET(CarrierProfile.TH, "th-TH", "Asia/Bangkok", CityPreset.PHUKET),
    KUALA_LUMPUR(CarrierProfile.MY, "ms-MY", "Asia/Kuala_Lumpur", CityPreset.KUALA_LUMPUR),
    GEORGE_TOWN(CarrierProfile.MY, "ms-MY", "Asia/Kuala_Lumpur", CityPreset.GEORGE_TOWN),
    JAKARTA(CarrierProfile.ID, "id-ID", "Asia/Jakarta", CityPreset.JAKARTA),
    DENPASAR(CarrierProfile.ID, "id-ID", "Asia/Makassar", CityPreset.DENPASAR),
    MANILA(CarrierProfile.PH, "en-PH", "Asia/Manila", CityPreset.MANILA),
    CEBU_CITY(CarrierProfile.PH, "en-PH", "Asia/Manila", CityPreset.CEBU_CITY),
    HANOI(CarrierProfile.VN, "vi-VN", "Asia/Ho_Chi_Minh", CityPreset.HANOI),
    HO_CHI_MINH_CITY(CarrierProfile.VN, "vi-VN", "Asia/Ho_Chi_Minh", CityPreset.HO_CHI_MINH_CITY),
    DA_NANG(CarrierProfile.VN, "vi-VN", "Asia/Ho_Chi_Minh", CityPreset.DA_NANG),
    PHNOM_PENH(CarrierProfile.KH, "km-KH", "Asia/Phnom_Penh", CityPreset.PHNOM_PENH);

    final CarrierProfile carrier;
    final String language;
    final String timeZone;
    final CityPreset city;

    RegionPreset(CarrierProfile carrier, String language, String timeZone, CityPreset city) {
        this.carrier = carrier;
        this.language = language;
        this.timeZone = timeZone;
        this.city = city;
    }

    String label() {
        return carrier.region + " · " + carrier.name + " · " + city.label;
    }
}
