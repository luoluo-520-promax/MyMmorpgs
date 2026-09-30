package cn.itcast.demo.mymmorpg.sync;

/**
 * 摄像机相对移动意图：按 W/A/S/D 映射为前/后/左/右。
 */
public enum MoveIntent {
    FORWARD,
    BACKWARD,
    LEFT,
    RIGHT;

    public static MoveIntent fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return FORWARD;
        }
        try {
            return MoveIntent.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FORWARD;
        }
    }
}
