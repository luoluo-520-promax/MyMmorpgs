package cn.itcast.demo.mymmorpg.element;

/**
 * 元素反应类型编码（协议 reaction_type / client_predicted_reaction）。
 */
public enum ReactionType {
    NONE(0, 1.0),
    OVERLOAD(1, 2.0),       // 火+雷
    FREEZE(2, 1.0),         // 冰+水（控制为主）
    SUPERCONDUCT(3, 1.5),   // 冰+雷
    VAPORIZE(4, 2.0),       // 火+水蒸发
    MELT(5, 2.0),           // 火+冰融化
    ELECTRO_CHARGED(6, 1.5),// 雷+水
    SWIRL(7, 1.2),          // 风扩散
    CRYSTALLIZE(8, 1.0),    // 岩结晶
    BLOOM(9, 1.5);          // 草+水绽放

    private final int code;
    private final double multiplier;

    ReactionType(int code, double multiplier) {
        this.code = code;
        this.multiplier = multiplier;
    }

    public int getCode() {
        return code;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public static ReactionType fromCode(int code) {
        for (ReactionType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return NONE;
    }
}
