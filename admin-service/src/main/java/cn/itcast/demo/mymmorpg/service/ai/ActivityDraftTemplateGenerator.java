package cn.itcast.demo.mymmorpg.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * 活动草案本地模板生成器：签到 / 累充 / 限时商店。
 * 无外部模型时也能产出符合 ActivityImportDocument 主路径的 JSON。
 */
public final class ActivityDraftTemplateGenerator {

    public static final String TEMPLATE_CHECKIN = "checkin";
    public static final String TEMPLATE_RECHARGE = "recharge";
    public static final String TEMPLATE_SHOP = "shop";

    private final ObjectMapper objectMapper;

    public ActivityDraftTemplateGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(String template, String prompt) {
        String resolved = resolveTemplate(template, prompt);
        return switch (resolved) {
            case TEMPLATE_RECHARGE -> buildRecharge(prompt);
            case TEMPLATE_SHOP -> buildShop(prompt);
            default -> buildCheckin(prompt);
        };
    }

    public String resolveTemplate(String template, String prompt) {
        if (template != null && !template.isBlank()) {
            return template.trim().toLowerCase(Locale.ROOT);
        }
        String text = prompt == null ? "" : prompt;
        if (containsAny(text, "累充", "充值", "首充", "recharge")) {
            return TEMPLATE_RECHARGE;
        }
        if (containsAny(text, "商店", "兑换", "限时商", "shop")) {
            return TEMPLATE_SHOP;
        }
        return TEMPLATE_CHECKIN;
    }

    private ObjectNode buildCheckin(String prompt) {
        long start = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli();
        String name = extractName(prompt, "限时签到活动");
        ObjectNode doc = baseDoc(2, name, start, end);
        doc.put("briefDesc", "每日签到领取奖励");
        doc.put("description", blankToDefault(prompt, "活动期间每日登录签到领取金币与道具，第 7 天额外奖励。"));
        doc.put("gameplay", "每日签到一次，累计天数解锁对应档位");
        doc.put("rules", "每档奖励仅可领取一次；需达到对应签到天数");

        ArrayNode conditions = doc.putArray("conditions");
        conditions.addObject().put("type", "SIGN_DAY_MIN").put("intValue", 1);

        ArrayNode tiers = doc.putArray("rewardTiers");
        for (int day = 1; day <= 7; day++) {
            ObjectNode tier = tiers.addObject();
            tier.put("index", day);
            tier.put("itemId", day == 7 ? 2001 : 1001);
            tier.put("count", day == 7 ? 1 : 100 * day);
            tier.put("signDay", day);
        }
        doc.put("rewardMethod", 1);
        putDisplay(doc, name, "签到有礼", "立即签到");
        return doc;
    }

    private ObjectNode buildRecharge(String prompt) {
        long start = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().plus(14, ChronoUnit.DAYS).toEpochMilli();
        String name = extractName(prompt, "累充好礼");
        ObjectNode doc = baseDoc(1, name, start, end);
        doc.put("briefDesc", "累计充值领取档位奖励");
        doc.put("description", blankToDefault(prompt, "活动期间累计充值达到指定金额可领取对应奖励。"));
        doc.put("gameplay", "累计充值达标后领取对应档位");
        doc.put("rules", "每档奖励仅可领取一次；按累计充值金额判断");

        ArrayNode conditions = doc.putArray("conditions");
        conditions.addObject().put("type", "RECHARGE_MIN").put("intValue", 6);

        ArrayNode tiers = doc.putArray("rewardTiers");
        int[] amounts = {6, 30, 98, 198};
        int[] items = {3001, 3002, 3003, 3004};
        for (int i = 0; i < amounts.length; i++) {
            ObjectNode tier = tiers.addObject();
            tier.put("index", i + 1);
            tier.put("itemId", items[i]);
            tier.put("count", 1);
            tier.put("targetRecharge", amounts[i]);
        }
        doc.put("rewardMethod", 1);
        putDisplay(doc, name, "累充领奖", "去充值");
        return doc;
    }

    private ObjectNode buildShop(String prompt) {
        long start = Instant.now().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        long end = Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli();
        String name = extractName(prompt, "限时兑换商店");
        ObjectNode doc = baseDoc(3, name, start, end);
        doc.put("briefDesc", "活动代币兑换限定道具");
        doc.put("description", blankToDefault(prompt, "活动期间获取代币，在限时商店兑换奖励。"));
        doc.put("gameplay", "完成任务获得代币，商店兑换");
        doc.put("rules", "商品有每日限购；代币活动结束后清零（以运营公告为准）");

        ArrayNode conditions = doc.putArray("conditions");
        conditions.addObject().put("type", "TOKEN_MIN").put("intValue", 1);

        ArrayNode stages = doc.putArray("stages");
        stages.addObject()
                .put("stageIndex", 1)
                .put("name", "兑换期")
                .put("description", "开放商店兑换")
                .put("unlockTime", start);

        ObjectNode costLimit = doc.putObject("costLimit");
        costLimit.put("costItemId", 0);
        costLimit.put("costItemCount", 0);
        costLimit.put("dailyLimit", 10);
        costLimit.put("totalLimit", 50);
        costLimit.put("minVipLevel", 0);
        costLimit.put("minPlayerLevel", 1);

        ArrayNode tiers = doc.putArray("rewardTiers");
        tiers.addObject()
                .put("index", 1)
                .put("itemId", 4001)
                .put("count", 10)
                .put("requiredStage", 1)
                .put("tokenAmount", 20);

        doc.put("rewardMethod", 1);
        ObjectNode token = doc.putObject("token");
        token.put("tokenId", 91001);
        token.put("tokenName", "活动币");
        token.put("initialAmount", 0);

        doc.put("shopId", 81001);
        ArrayNode products = doc.putArray("shopProducts");
        products.addObject()
                .put("productId", 1)
                .put("itemId", 5001)
                .put("count", 1)
                .put("tokenCost", 100)
                .put("dailyLimit", 1);
        products.addObject()
                .put("productId", 2)
                .put("itemId", 5002)
                .put("count", 5)
                .put("tokenCost", 50)
                .put("dailyLimit", 3);

        putDisplay(doc, name, "限时兑换", "前往兑换");
        return doc;
    }

    private ObjectNode baseDoc(int type, String name, long start, long end) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("type", type);
        doc.put("opened", true);
        doc.put("startTime", start);
        doc.put("endTime", end);
        doc.put("name", name);
        doc.put("configVersion", 1);
        doc.putObject("uiResources")
                .put("bannerUrl", "assets/ui/activity/ai_banner.png")
                .put("iconUrl", "assets/ui/activity/ai_icon.png")
                .put("backgroundUrl", "assets/ui/activity/ai_bg.png")
                .put("uiPrefabPath", "prefabs/activity/AiGeneratedPanel")
                .put("resourceVersion", 1);
        return doc;
    }

    private void putDisplay(ObjectNode doc, String title, String subtitle, String button) {
        ObjectNode display = doc.putObject("displayText");
        display.put("title", title);
        display.put("subtitle", subtitle);
        display.put("ruleText", "详见活动规则");
        display.put("buttonText", button);
        display.putObject("extras").put("source", "ai-template");
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
