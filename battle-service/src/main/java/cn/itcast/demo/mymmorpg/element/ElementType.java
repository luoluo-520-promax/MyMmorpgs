package cn.itcast.demo.mymmorpg.element;

/**
 * 战斗元素类型（与 BattleActionCsReq.element_type 对齐）。
 */
public enum ElementType {
    NONE(0),
    PYRO(1),
    HYDRO(2),
    ELECTRO(3),
    CRYO(4),
    ANEMO(5),
    GEO(6),
    DENDRO(7);

    private final int code;

    ElementType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ElementType fromCode(int code) {
        for (ElementType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return NONE;
    }
}
