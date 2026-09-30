package cn.itcast.demo.mymmorpg.element;

/**
 * 元素反应四层管线输出：附着 → 触发 → 反应 → 倍率放大，并携带回滚校验结果。
 */
public record ReactionResult(
        int baseDamage,
        int finalDamage,
        ReactionType reaction,
        ElementType remainingAura,
        boolean rollback,
        int clientPredictedDamage) {

    public static ReactionResult noReaction(int damage) {
        return new ReactionResult(damage, damage, ReactionType.NONE, ElementType.NONE, false, damage);
    }
}
