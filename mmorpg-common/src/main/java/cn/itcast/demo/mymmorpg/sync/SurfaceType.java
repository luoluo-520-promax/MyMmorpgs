package cn.itcast.demo.mymmorpg.sync;

/**
 * 地表材质类型：用于脚印/车辙等微观物理反馈。
 */
public enum SurfaceType {
    PLAIN,
    GRASS,
    SNOW,
    MUD,
    SAND,
    STONE;

    public static SurfaceType fromName(String name) {
        if (name == null || name.isBlank()) {
            return PLAIN;
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PLAIN;
        }
    }
}
