package cn.itcast.demo.mymmorpg.sync;

/**
 * SceneMoveCmd 动作位掩码（32 位）：空中冲刺 / 壁踢 / 壁走等极限操作。
 */
public final class MoveFlags {

    public static final int NONE = 0;
    /** 空中冲刺 */
    public static final int MID_AIR_DASH = 1;
    /** 壁踢 */
    public static final int WALL_KICK = 1 << 1;
    /** 壁走 */
    public static final int WALL_RUN = 1 << 2;

    private MoveFlags() {
    }

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }
}
