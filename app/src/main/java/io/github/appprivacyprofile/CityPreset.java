package io.github.appprivacyprofile;

enum CityPreset {
    BEIJING("北京", "39.9042", "116.4074"),
    SHANGHAI("上海", "31.2304", "121.4737"),
    GUANGZHOU("广州", "23.1291", "113.2644"),
    SHENZHEN("深圳", "22.5431", "114.0579"),
    HANGZHOU("杭州", "30.2741", "120.1551"),
    CHENGDU("成都", "30.5728", "104.0668"),
    CHONGQING("重庆", "29.5630", "106.5516"),
    WUHAN("武汉", "30.5928", "114.3055"),
    HONG_KONG("香港", "22.3193", "114.1694"),
    TAIPEI("台北", "25.0330", "121.5654"),
    TOKYO("东京", "35.6762", "139.6503"),
    OSAKA("大阪", "34.6937", "135.5023"),
    SEOUL("首尔", "37.5665", "126.9780"),
    SINGAPORE("新加坡", "1.3521", "103.8198"),
    BANGKOK("曼谷", "13.7563", "100.5018"),
    CHIANG_MAI("清迈", "18.7883", "98.9853"),
    PHUKET("普吉市", "7.8804", "98.3923"),
    KUALA_LUMPUR("吉隆坡", "3.1390", "101.6869"),
    GEORGE_TOWN("槟城乔治市", "5.4141", "100.3288"),
    JAKARTA("雅加达", "-6.2088", "106.8456"),
    DENPASAR("巴厘岛登巴萨", "-8.6705", "115.2126"),
    MANILA("马尼拉", "14.5995", "120.9842"),
    CEBU_CITY("宿务市", "10.3157", "123.8854"),
    HANOI("河内", "21.0278", "105.8342"),
    HO_CHI_MINH_CITY("胡志明市", "10.8231", "106.6297"),
    DA_NANG("岘港", "16.0544", "108.2022"),
    PHNOM_PENH("金边", "11.5625", "104.9160"),
    DUBAI("迪拜", "25.2048", "55.2708"),
    NEW_YORK("纽约", "40.7128", "-74.0060"),
    LOS_ANGELES("洛杉矶", "34.0522", "-118.2437"),
    SAN_FRANCISCO("旧金山", "37.7749", "-122.4194"),
    SEATTLE("西雅图", "47.6062", "-122.3321"),
    TORONTO("多伦多", "43.6532", "-79.3832"),
    LONDON("伦敦", "51.5074", "-0.1278"),
    PARIS("巴黎", "48.8566", "2.3522"),
    BERLIN("柏林", "52.5200", "13.4050"),
    SYDNEY("悉尼", "-33.8688", "151.2093");

    final String label;
    final String latitude;
    final String longitude;

    CityPreset(String label, String latitude, String longitude) {
        this.label = label;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
