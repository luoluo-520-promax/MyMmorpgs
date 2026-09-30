package cn.itcast.demo.mymmorpg.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * 氪金礼包 AI/本地草稿模板（shop_pack），产出 products.json 结构。
 */
public final class ShopPackDraftTemplateGenerator {

    public static final String TEMPLATE_SHOP_PACK = "shop_pack";
    public static final String TEMPLATE_TOPUP = "topup";
    public static final String TEMPLATE_DISCOUNT = "discount";
    public static final String TEMPLATE_FIRST = "first_charge";

    private final ObjectMapper objectMapper;

    public ShopPackDraftTemplateGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(String template, String prompt) {
        String resolved = resolveTemplate(template, prompt);
        return switch (resolved) {
            case TEMPLATE_TOPUP -> buildTopup(prompt);
            case TEMPLATE_DISCOUNT -> buildDiscount(prompt);
            default -> buildFirstCharge(prompt);
        };
    }

    public String resolveTemplate(String template, String prompt) {
        if (template != null && !template.isBlank()) {
            String t = template.trim().toLowerCase(Locale.ROOT);
            if (TEMPLATE_SHOP_PACK.equals(t)) {
                return resolveFromPrompt(prompt);
            }
            return t;
        }
        return resolveFromPrompt(prompt);
    }

    private String resolveFromPrompt(String prompt) {
        String text = prompt == null ? "" : prompt;
        if (containsAny(text, "打折", "特惠", "折扣", "discount", "周末")) {
            return TEMPLATE_DISCOUNT;
        }
        if (containsAny(text, "直充", "钻石", "topup", "充值档")) {
            return TEMPLATE_TOPUP;
        }
        return TEMPLATE_FIRST;
    }

    private ObjectNode buildTopup(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode products = root.putArray("products");
        ObjectNode p = products.addObject();
        p.put("productId", 10091);
        p.put("productType", "DIRECT_TOPUP");
        p.put("name", extractName(prompt, "60钻石"));
        p.put("desc", blankToDefault(prompt, "直充钻石档位"));
        p.put("channelSku", "mmorpg.diamond.60.draft");
        p.put("currency", "CNY");
        p.put("price", 600);
        p.put("originalPrice", 600);
        p.putArray("rewards").addObject().put("itemId", 9001).put("count", 60).put("bindType", 0);
        p.put("limitType", "NONE");
        p.put("limitCount", 0);
        p.put("tabId", "topup");
        p.putArray("tags").add("diamond");
        p.put("sort", 10);
        p.put("opened", true);
        p.put("version", 1);
        return root;
    }

    private ObjectNode buildDiscount(String prompt) {
        OffsetDateTime begin = OffsetDateTime.now(ZoneOffset.ofHours(8)).withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime end = begin.plusDays(3).withHour(23).withMinute(59).withSecond(59);
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode products = root.putArray("products");
        ObjectNode p = products.addObject();
        p.put("productId", 20091);
        p.put("productType", "DISCOUNT_PACK");
        p.put("name", extractName(prompt, "限时特惠礼包"));
        p.put("desc", blankToDefault(prompt, "限时打折氪金礼包"));
        p.put("channelSku", "mmorpg.pack.discount.draft");
        p.put("currency", "CNY");
        p.put("price", 1200);
        p.put("originalPrice", 3000);
        p.put("discountRate", 40);
        p.put("discountBegin", begin.toString());
        p.put("discountEnd", end.toString());
        ArrayNode rewards = p.putArray("rewards");
        rewards.addObject().put("itemId", 9001).put("count", 300).put("bindType", 0);
        rewards.addObject().put("itemId", 8001).put("count", 5).put("bindType", 1);
        p.put("limitType", "WEEKLY");
        p.put("limitCount", 1);
        p.put("tabId", "discount");
        p.putArray("tags").add("weekend");
        p.put("sort", 10);
        p.put("opened", true);
        p.put("version", 1);
        return root;
    }

    private ObjectNode buildFirstCharge(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode products = root.putArray("products");
        ObjectNode p = products.addObject();
        p.put("productId", 30091);
        p.put("productType", "OTHER_PACK");
        p.put("name", extractName(prompt, "首充超值礼包"));
        p.put("desc", blankToDefault(prompt, "终身限购一次的首充礼包"));
        p.put("channelSku", "mmorpg.pack.first.draft");
        p.put("currency", "CNY");
        p.put("price", 600);
        p.put("originalPrice", 600);
        ArrayNode rewards = p.putArray("rewards");
        rewards.addObject().put("itemId", 9001).put("count", 120).put("bindType", 0);
        rewards.addObject().put("itemId", 7001).put("count", 1).put("bindType", 1);
        p.put("limitType", "LIFETIME");
        p.put("limitCount", 1);
        p.put("tabId", "pack");
        p.putArray("tags").add("first_charge");
        p.put("sort", 10);
        p.put("opened", true);
        p.put("version", 1);
        return root;
    }

    private static String extractName(String prompt, String fallback) {
        if (prompt == null || prompt.isBlank()) {
            return fallback;
        }
        String trimmed = prompt.trim();
        if (trimmed.length() <= 24 && !trimmed.contains("，") && !trimmed.contains(",")) {
            return trimmed;
        }
        return fallback;
    }

    private static String blankToDefault(String prompt, String fallback) {
        return prompt == null || prompt.isBlank() ? fallback : prompt.trim();
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
