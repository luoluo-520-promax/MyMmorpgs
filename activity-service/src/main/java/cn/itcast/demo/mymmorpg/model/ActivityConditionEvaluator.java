package cn.itcast.demo.mymmorpg.model;

import java.util.List;

/**
 * 活动条件评估：根据玩家进度与活动配置判断通用条件是否满足。
 */
public final class ActivityConditionEvaluator {

    private ActivityConditionEvaluator() {
    }

    /**
     * 评估活动级条件列表，全部满足返回 true；空列表视为满足。
     */
    public static boolean allMet(List<ActivityConditionPayload> conditions, PlayerActivityProgress progress) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (ActivityConditionPayload c : conditions) {
            if (!singleMet(c, progress)) {
                return false;
            }
        }
        return true;
    }

    private static boolean singleMet(ActivityConditionPayload c, PlayerActivityProgress progress) {
        if (c == null || c.type == null || c.type.isBlank()) {
            return true;
        }
        return switch (c.type) {
            case "RECHARGE_MIN" -> progress.rechargeAmount >= c.intValue;
            case "TOKEN_MIN" -> progress.tokenAmount >= c.intValue;
            case "STAGE_MIN" -> progress.currentStage >= c.intValue;
            case "SIGN_DAY_MIN" -> progress.signDays.size() >= c.intValue;
            case "BATTLE_WIN_MIN" -> progress.battleWins >= c.intValue;
            default -> true;
        };
    }
}
