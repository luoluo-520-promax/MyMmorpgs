package cn.itcast.demo.mymmorpg.shop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 商城商品导入校验（对齐运营上架文档 5.3）。
 */
public final class ShopImportValidator {

    /** 直接氪金允许的价格档位（分）。 */
    public static final Set<Long> DIRECT_TOPUP_PRICE_WHITELIST = Set.of(
            600L, 3000L, 6800L, 12800L, 32800L
    );

    private ShopImportValidator() {
    }

    public static List<String> validate(List<ShopProductConfig> products) {
        List<String> errors = new ArrayList<>();
        if (products == null || products.isEmpty()) {
            errors.add("products 不能为空");
            return errors;
        }
        Set<Integer> ids = new HashSet<>();
        Set<String> skus = new HashSet<>();
        for (int i = 0; i < products.size(); i++) {
            ShopProductConfig p = products.get(i);
            String path = "products[" + i + "]";
            if (p == null) {
                errors.add(path + " 为空");
                continue;
            }
            if (p.productId <= 0) {
                errors.add(path + ".productId 必须 > 0");
            } else if (!ids.add(p.productId)) {
                errors.add(path + ".productId 重复: " + p.productId);
            }
            ShopProductType type = p.typeEnum();
            if (type == null) {
                errors.add(path + ".productType 非法: " + p.productType);
            }
            if (p.name == null || p.name.isBlank()) {
                errors.add(path + ".name 不能为空");
            }
            if (p.channelSku == null || p.channelSku.isBlank()) {
                errors.add(path + ".channelSku 不能为空");
            } else if (!skus.add(p.channelSku.trim())) {
                errors.add(path + ".channelSku 重复: " + p.channelSku);
            }
            if (p.price <= 0) {
                errors.add(path + ".price 必须 > 0");
            }
            if (p.rewards == null || p.rewards.isEmpty()) {
                errors.add(path + ".rewards 不能为空");
            } else {
                for (int j = 0; j < p.rewards.size(); j++) {
                    ShopRewardConfig r = p.rewards.get(j);
                    if (r == null || r.itemId <= 0 || r.count <= 0) {
                        errors.add(path + ".rewards[" + j + "] itemId/count 非法");
                    }
                }
            }
            ShopLimitType limit = p.limitEnum();
            if (limit == null) {
                errors.add(path + ".limitType 非法: " + p.limitType);
            } else if (limit != ShopLimitType.NONE && p.limitCount < 1) {
                errors.add(path + ".limitCount 在限购时必须 >= 1");
            }
            if (type == ShopProductType.DISCOUNT_PACK) {
                if (p.originalPrice <= p.price) {
                    errors.add(path + " DISCOUNT_PACK 要求 originalPrice > price");
                }
                Long dBegin = ShopProductConfig.parseTimeMs(p.discountBegin);
                Long dEnd = ShopProductConfig.parseTimeMs(p.discountEnd);
                Long sBegin = ShopProductConfig.parseTimeMs(p.saleBegin);
                Long sEnd = ShopProductConfig.parseTimeMs(p.saleEnd);
                if (dBegin == null && dEnd == null && sBegin == null && sEnd == null) {
                    errors.add(path + " DISCOUNT_PACK 需配置折扣或上下架时间窗");
                }
                if (dBegin != null && dEnd != null && dBegin >= dEnd) {
                    errors.add(path + " discountBegin 必须早于 discountEnd");
                }
            }
            if (type == ShopProductType.DIRECT_TOPUP && !DIRECT_TOPUP_PRICE_WHITELIST.contains(p.price)) {
                errors.add(path + " DIRECT_TOPUP price 不在白名单: " + p.price);
            }
            boolean skinTagged = (p.tags != null && p.tags.stream().anyMatch(t -> "skin".equalsIgnoreCase(t)))
                    || "skin".equalsIgnoreCase(p.tabId);
            if (skinTagged) {
                ShopLimitType limitType = p.limitEnum();
                if (limitType != ShopLimitType.LIFETIME || p.limitCount != 1) {
                    errors.add(path + " 皮肤商品建议 LIFETIME 限购 1 次（当前 limitType="
                            + p.limitType + ", limitCount=" + p.limitCount + "）");
                }
            }
        }
        return errors;
    }
}
