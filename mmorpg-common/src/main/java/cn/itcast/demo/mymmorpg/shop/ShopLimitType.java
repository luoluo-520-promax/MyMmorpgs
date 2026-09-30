package cn.itcast.demo.mymmorpg.shop;

public enum ShopLimitType {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    LIFETIME,
    ACCOUNT;

    public static ShopLimitType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return ShopLimitType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
