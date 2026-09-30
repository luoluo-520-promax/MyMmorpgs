package cn.itcast.demo.mymmorpg.shop;

/**
 * 氪金货架商品类型（与活动代币商店解耦）。
 */
public enum ShopProductType {
    DIRECT_TOPUP,
    DISCOUNT_PACK,
    OTHER_PACK;

    public static ShopProductType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ShopProductType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
