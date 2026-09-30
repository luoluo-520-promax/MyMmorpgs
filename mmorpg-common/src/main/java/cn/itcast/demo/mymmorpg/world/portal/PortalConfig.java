package cn.itcast.demo.mymmorpg.world.portal;

import java.util.Objects;

/**
 * 场景门配置：接近边界时触发目标 Scene 槽位预租，实现客户端无感切换。
 */
public record PortalConfig(
        String portalId,
        int fromSceneId,
        int toSceneId,
        int toLineId,
        float x,
        float y,
        float z,
        float triggerRadius,
        float preloadRadius,
        float entryX,
        float entryY,
        float entryZ,
        String targetNodeHint) {

    public PortalConfig {
        Objects.requireNonNull(portalId, "portalId");
        triggerRadius = triggerRadius <= 0 ? 8f : triggerRadius;
        preloadRadius = preloadRadius <= 0 ? Math.max(triggerRadius * 3f, 40f) : preloadRadius;
        toLineId = toLineId <= 0 ? 1 : toLineId;
    }

    public double distanceSq(float px, float py, float pz) {
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean inPreloadRange(float px, float py, float pz) {
        double r = preloadRadius;
        return distanceSq(px, py, pz) <= r * r;
    }

    public boolean inTriggerRange(float px, float py, float pz) {
        double r = triggerRadius;
        return distanceSq(px, py, pz) <= r * r;
    }
}
