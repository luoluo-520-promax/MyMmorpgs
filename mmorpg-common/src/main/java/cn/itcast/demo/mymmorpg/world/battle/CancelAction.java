package cn.itcast.demo.mymmorpg.world.battle;

/**
 * 取消层级动作标识：数值越大优先级越高。
 */
public enum CancelAction {
    DODGE(100),
    HEAVY_ATTACK(80),
    NORMAL_ATTACK(60),
    JUMP(50),
    NONE(0);

    private final int priority;

    CancelAction(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    public static CancelAction fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return CancelAction.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
