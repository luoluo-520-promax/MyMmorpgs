package cn.itcast.demo.mymmorpg.sync;

/**
 * 动作状态机（ASM）：空中/地面/攀爬动作分层，与 MovementType 正交。
 */
public enum ActionState {
    GROUND_IDLE,
    GROUND_RUN,
    AIR_NORMAL,
    AIR_HEAVY,
    CLIMB_ATTACK,
    SWIM_ATTACK;

    public static ActionState fromName(String raw) {
        if (raw == null || raw.isBlank()) {
            return GROUND_IDLE;
        }
        try {
            return ActionState.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GROUND_IDLE;
        }
    }

    public boolean isAirborne() {
        return this == AIR_NORMAL || this == AIR_HEAVY;
    }

    public boolean isGrounded() {
        return this == GROUND_IDLE || this == GROUND_RUN;
    }
}
