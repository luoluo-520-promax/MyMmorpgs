package cn.itcast.demo.mymmorpg.model;

/** 公会职位：1 会长 / 2 副会 / 3 成员。 */
public enum GuildRole {
    LEADER(1),
    VICE(2),
    MEMBER(3);

    private final int code;

    GuildRole(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static GuildRole fromCode(int code) {
        for (GuildRole r : values()) {
            if (r.code == code) {
                return r;
            }
        }
        return MEMBER;
    }
}
