package cn.itcast.demo.mymmorpg.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 活动导入文档规则校验：必填、枚举、时间窗、奖励经济性告警。
 */
public final class ActivityImportValidator {

    private static final Set<String> KNOWN_CONDITION_TYPES = Set.of(
            "TOKEN_MIN", "RECHARGE_MIN", "STAGE_MIN", "SIGN_DAY_MIN",
            "LEVEL_MIN", "VIP_MIN", "CUSTOM");

    private static final Set<Integer> KNOWN_REWARD_METHODS = Set.of(
            ActivityRewardMethod.DIRECT_TO_BAG,
            ActivityRewardMethod.MAIL,
            ActivityRewardMethod.RANDOM_ONE,
            ActivityRewardMethod.TOKEN_ONLY);

    /** 单档奖励数量超过该值时给出经济性警告。 */
    private static final int REWARD_COUNT_WARN = 10_000;
    /** 每日参与上限超过该值时给出警告。 */
    private static final int DAILY_LIMIT_WARN = 100;

    private ActivityImportValidator() {
    }

    public static ValidationOutcome validate(List<ActivityImportDocument> docs) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (docs == null || docs.isEmpty()) {
            errors.add("导入文档为空：至少需要 1 条活动配置");
            return new ValidationOutcome(errors, warnings);
        }
        for (int i = 0; i < docs.size(); i++) {
            validateOne(docs.get(i), "documents[" + i + "]", errors, warnings);
        }
        errors.addAll(ActivityScheduleConflictDetector.detect(docs));
        return new ValidationOutcome(errors, warnings);
    }

    public static ValidationOutcome validate(ActivityImportDocument doc) {
        return validate(doc == null ? List.of() : List.of(doc));
    }

    private static void validateOne(ActivityImportDocument doc, String path,
                                    List<String> errors, List<String> warnings) {
        if (doc == null) {
            errors.add(path + ": 文档为 null");
            return;
        }
        if (doc.type == null) {
            errors.add(path + ".type: 活动类型 type 不能为空");
        } else if (doc.type <= 0) {
            errors.add(path + ".type: 活动类型必须为正整数，当前=" + doc.type);
        }
        if (doc.name == null || doc.name.isBlank()) {
            errors.add(path + ".name: 活动名称不能为空");
        }
        if (doc.endTime < doc.startTime) {
            errors.add(path + ": endTime(" + doc.endTime + ") 不能早于 startTime(" + doc.startTime + ")");
        }
        if (!KNOWN_REWARD_METHODS.contains(doc.rewardMethod)) {
            errors.add(path + ".rewardMethod: 非法值 " + doc.rewardMethod
                    + "，允许 1/2/3/4（直发/邮件/随机/仅代币）");
        }
        validateConditions(doc.conditions, path + ".conditions", errors);
        validateStages(doc.stages, path + ".stages", errors);
        validateRewardTiers(doc.rewardTiers, path + ".rewardTiers", errors, warnings);
        validateShopProducts(doc.shopProducts, path + ".shopProducts", errors, warnings);
        if (doc.costLimit != null && doc.costLimit.dailyLimit > DAILY_LIMIT_WARN) {
            warnings.add(path + ".costLimit.dailyLimit=" + doc.costLimit.dailyLimit
                    + " 超过常见区间(" + DAILY_LIMIT_WARN + ")，请确认经济产出");
        }
        if (doc.shopId > 0 && (doc.shopProducts == null || doc.shopProducts.isEmpty())) {
            warnings.add(path + ": 已配置 shopId=" + doc.shopId + " 但 shopProducts 为空");
        }
    }

    private static void validateConditions(List<ActivityConditionPayload> conditions, String path,
                                           List<String> errors) {
        if (conditions == null) {
            return;
        }
        for (int i = 0; i < conditions.size(); i++) {
            ActivityConditionPayload c = conditions.get(i);
            if (c == null) {
                errors.add(path + "[" + i + "]: 条件为 null");
                continue;
            }
            if (c.type == null || c.type.isBlank()) {
                errors.add(path + "[" + i + "].type: 条件类型不能为空");
                continue;
            }
            String normalized = c.type.trim().toUpperCase(Locale.ROOT);
            if (!KNOWN_CONDITION_TYPES.contains(normalized)) {
                errors.add(path + "[" + i + "].type: 未知条件类型 '" + c.type
                        + "'，允许 " + KNOWN_CONDITION_TYPES);
            }
        }
    }

    private static void validateStages(List<ActivityStagePayload> stages, String path,
                                       List<String> errors) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < stages.size(); i++) {
            ActivityStagePayload s = stages.get(i);
            if (s == null) {
                errors.add(path + "[" + i + "]: 阶段为 null");
                continue;
            }
            if (s.stageIndex <= 0) {
                errors.add(path + "[" + i + "].stageIndex: 必须为正整数");
            } else if (!indexes.add(s.stageIndex)) {
                errors.add(path + "[" + i + "].stageIndex: 阶段序号重复 " + s.stageIndex);
            }
        }
    }

    private static void validateRewardTiers(List<RewardTierPayload> tiers, String path,
                                            List<String> errors, List<String> warnings) {
        if (tiers == null || tiers.isEmpty()) {
            warnings.add(path + ": 奖励档位为空，活动将无可领取奖励");
            return;
        }
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < tiers.size(); i++) {
            RewardTierPayload t = tiers.get(i);
            if (t == null) {
                errors.add(path + "[" + i + "]: 奖励档为 null");
                continue;
            }
            if (t.index <= 0) {
                errors.add(path + "[" + i + "].index: 档位编号必须为正整数");
            } else if (!indexes.add(t.index)) {
                errors.add(path + "[" + i + "].index: 档位编号重复 " + t.index);
            }
            if (t.itemId <= 0) {
                errors.add(path + "[" + i + "].itemId: 必须为正整数");
            }
            if (t.count <= 0) {
                errors.add(path + "[" + i + "].count: 必须为正整数");
            } else if (t.count > REWARD_COUNT_WARN) {
                warnings.add(path + "[" + i + "].count=" + t.count
                        + " 超过常见区间(" + REWARD_COUNT_WARN + ")，请确认经济产出上限");
            }
        }
    }

    private static void validateShopProducts(List<ActivityShopProductPayload> products, String path,
                                             List<String> errors, List<String> warnings) {
        if (products == null || products.isEmpty()) {
            return;
        }
        Set<Integer> productIds = new HashSet<>();
        for (int i = 0; i < products.size(); i++) {
            ActivityShopProductPayload p = products.get(i);
            if (p == null) {
                errors.add(path + "[" + i + "]: 商品为 null");
                continue;
            }
            if (p.productId <= 0) {
                errors.add(path + "[" + i + "].productId: 必须为正整数");
            } else if (!productIds.add(p.productId)) {
                errors.add(path + "[" + i + "].productId: 商品 ID 重复 " + p.productId);
            }
            if (p.itemId <= 0) {
                errors.add(path + "[" + i + "].itemId: 必须为正整数");
            }
            if (p.count <= 0) {
                errors.add(path + "[" + i + "].count: 必须为正整数");
            }
            if (p.tokenCost < 0) {
                errors.add(path + "[" + i + "].tokenCost: 不能为负数");
            }
            if (p.dailyLimit > DAILY_LIMIT_WARN) {
                warnings.add(path + "[" + i + "].dailyLimit=" + p.dailyLimit
                        + " 超过常见区间(" + DAILY_LIMIT_WARN + ")");
            }
        }
    }

    public record ValidationOutcome(List<String> errors, List<String> warnings) {
        public boolean isValid() {
            return errors == null || errors.isEmpty();
        }
    }
}
