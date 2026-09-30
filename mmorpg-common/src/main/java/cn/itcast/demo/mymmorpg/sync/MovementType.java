package cn.itcast.demo.mymmorpg.sync;

/**
 * 大世界移动状态机类型（与 scene_protocol.MovementType 对齐）。
 */
public enum MovementType {
    WALK,
    SWIM,
    CLIMB,
    GLIDE,
    SWING,
    DASH,
    /** 钩锁抓取锚点 */
    GRAPPLE,
    /** 载具骑行 */
    RIDE,
    /** 空中冲刺 */
    AIR_DASH,
    /** 跑墙 */
    WALL_RUN,
    /** 弹射蘑菇 / 特殊地形弹射 */
    BOUNCE,
    /** 攀爬挂边（体力耗尽悬挂） */
    CLIMB_HANG,
    /** 攀爬翻越至顶部 */
    CLIMB_VAULT;

    public static MovementType fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return WALK;
        }
        String n = raw.trim().toUpperCase();
        if (n.startsWith("MOVE_")) {
            n = n.substring(5);
        }
        try {
            return MovementType.valueOf(n);
        } catch (IllegalArgumentException e) {
            return WALK;
        }
    }

    public MovementPredictionValidator.ActionType toActionType() {
        return switch (this) {
            case WALK -> MovementPredictionValidator.ActionType.MOVE;
            case DASH, AIR_DASH -> MovementPredictionValidator.ActionType.DASH;
            case SWIM -> MovementPredictionValidator.ActionType.SWIM;
            case CLIMB, CLIMB_HANG, CLIMB_VAULT, WALL_RUN -> MovementPredictionValidator.ActionType.CLIMB;
            case GLIDE, BOUNCE -> MovementPredictionValidator.ActionType.GLIDE;
            case SWING, GRAPPLE -> MovementPredictionValidator.ActionType.HOOK;
            case RIDE -> MovementPredictionValidator.ActionType.VEHICLE;
        };
    }
}
