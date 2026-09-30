package cn.itcast.demo.mymmorpg.shop;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ShopImportValidatorTest {

    @Test
    public void acceptsSampleCatalog() {
        ShopProductConfig topup = base(10001, "DIRECT_TOPUP", 600);
        topup.originalPrice = 600;
        topup.tabId = "topup";

        ShopProductConfig discount = base(20001, "DISCOUNT_PACK", 1200);
        discount.originalPrice = 3000;
        discount.discountBegin = "2026-08-01T00:00:00+08:00";
        discount.discountEnd = "2026-08-03T23:59:59+08:00";
        discount.limitType = "WEEKLY";
        discount.limitCount = 1;

        ShopProductConfig pack = base(30001, "OTHER_PACK", 600);
        pack.originalPrice = 600;
        pack.limitType = "LIFETIME";
        pack.limitCount = 1;
        pack.tags = List.of("first_charge");

        assertThat(ShopImportValidator.validate(List.of(topup, discount, pack))).isEmpty();
    }

    @Test
    public void rejectsDiscountWithoutMarkup() {
        ShopProductConfig discount = base(20001, "DISCOUNT_PACK", 3000);
        discount.originalPrice = 3000;
        discount.discountBegin = "2026-08-01T00:00:00+08:00";
        discount.discountEnd = "2026-08-03T23:59:59+08:00";
        assertThat(ShopImportValidator.validate(List.of(discount)))
                .anyMatch(e -> e.contains("originalPrice > price"));
    }

    @Test
    public void rejectsUnknownTopupPrice() {
        ShopProductConfig topup = base(10001, "DIRECT_TOPUP", 999);
        topup.originalPrice = 999;
        assertThat(ShopImportValidator.validate(List.of(topup)))
                .anyMatch(e -> e.contains("白名单"));
    }

    @Test
    public void rejectsSkinProductWithoutLifetimeLimit() {
        ShopProductConfig skin = base(520001, "OTHER_PACK", 1800);
        skin.originalPrice = 1800;
        skin.tabId = "skin";
        skin.tags = List.of("skin");
        skin.limitType = "NONE";
        skin.limitCount = 0;
        assertThat(ShopImportValidator.validate(List.of(skin)))
                .anyMatch(e -> e.contains("LIFETIME"));
    }

    @Test
    public void acceptsSkinProductWithLifetimeLimit() {
        ShopProductConfig skin = base(520001, "OTHER_PACK", 1800);
        skin.originalPrice = 1800;
        skin.tabId = "skin";
        skin.tags = List.of("skin");
        skin.limitType = "LIFETIME";
        skin.limitCount = 1;
        assertThat(ShopImportValidator.validate(List.of(skin))).isEmpty();
    }

    private static ShopProductConfig base(int id, String type, long price) {
        ShopProductConfig p = new ShopProductConfig();
        p.productId = id;
        p.productType = type;
        p.name = "t" + id;
        p.channelSku = "sku." + id;
        p.price = price;
        ShopRewardConfig r = new ShopRewardConfig();
        r.itemId = 9001;
        r.count = 1;
        p.rewards = List.of(r);
        p.limitType = "NONE";
        return p;
    }
}
