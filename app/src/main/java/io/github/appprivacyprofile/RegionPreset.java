package io.github.appprivacyprofile;

enum RegionPreset {
    US(CarrierProfile.US, "en-US", "America/New_York", CityPreset.NEW_YORK),
    JP(CarrierProfile.JP, "ja-JP", "Asia/Tokyo", CityPreset.TOKYO),
    TW(CarrierProfile.TW, "zh-Hant-TW", "Asia/Taipei", CityPreset.TAIPEI),
    HK(CarrierProfile.HK, "zh-Hant-HK", "Asia/Hong_Kong", CityPreset.HONG_KONG);

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
