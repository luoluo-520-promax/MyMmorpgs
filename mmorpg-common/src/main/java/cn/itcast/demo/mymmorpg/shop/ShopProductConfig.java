package cn.itcast.demo.mymmorpg.shop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopProductConfig {
    public int productId;
    public String productType = "";
    public String name = "";
    public String desc = "";
    public String icon = "";
    public String channelSku = "";
    public String currency = "CNY";
    public long price;
    public long originalPrice;
    public Integer discountRate;
    public String discountBegin;
    public String discountEnd;
    public String saleBegin;
    public String saleEnd;
    public List<ShopRewardConfig> rewards = new ArrayList<>();
    public String limitType = "NONE";
    public int limitCount;
    /** 全服限量库存；0 表示不启用全局库存（仅玩家限购）。 */
    public int stockTotal;
    public List<String> tags = new ArrayList<>();
    public int sort;
    public String tabId = "";
    public boolean opened = true;
    public int version = 1;
    /** 仅白名单账号可见/可购（灰度）。 */
    public boolean whiteListOnly;
    /** DRAFT / PUBLISHED */
    public String publishStatus = "PUBLISHED";

    public ShopProductType typeEnum() {
        return ShopProductType.from(productType);
    }

    public ShopLimitType limitEnum() {
        return ShopLimitType.from(limitType);
    }

    public boolean isOnSale(long nowMs) {
        if (!opened) {
            return false;
        }
        Long begin = parseTimeMs(saleBegin);
        Long end = parseTimeMs(saleEnd);
        if (begin != null && nowMs < begin) {
            return false;
        }
        if (end != null && nowMs > end) {
            return false;
        }
        return true;
    }

    public boolean isDiscountWindowActive(long nowMs) {
        if (typeEnum() != ShopProductType.DISCOUNT_PACK) {
            return true;
        }
        Long begin = parseTimeMs(discountBegin);
        Long end = parseTimeMs(discountEnd);
        if (begin == null && end == null) {
            begin = parseTimeMs(saleBegin);
            end = parseTimeMs(saleEnd);
        }
        if (begin != null && nowMs < begin) {
            return false;
        }
        if (end != null && nowMs > end) {
            return false;
        }
        return true;
    }

    public long discountEndMs() {
        Long end = parseTimeMs(discountEnd);
        if (end == null) {
            end = parseTimeMs(saleEnd);
        }
        return end == null ? 0L : end;
    }

    public long saleEndMs() {
        Long end = parseTimeMs(saleEnd);
        return end == null ? 0L : end;
    }

    public static Long parseTimeMs(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        try {
            if (v.chars().allMatch(Character::isDigit)) {
                return Long.parseLong(v);
            }
            return OffsetDateTime.parse(v).toInstant().toEpochMilli();
        } catch (Exception ignore) {
            try {
                return Instant.parse(v).toEpochMilli();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
