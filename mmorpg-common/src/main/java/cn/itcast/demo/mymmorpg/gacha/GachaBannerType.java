package cn.itcast.demo.mymmorpg.gacha;

public final class GachaBannerType {
    public static final int NEWBIE = 1;
    public static final int NORMAL = 2;
    public static final int AVATAR_UP = 11;
    public static final int WEAPON_UP = 12;

    private GachaBannerType() {
    }

    public static boolean isUpBanner(int type) {
        return type == AVATAR_UP || type == WEAPON_UP;
    }
}
