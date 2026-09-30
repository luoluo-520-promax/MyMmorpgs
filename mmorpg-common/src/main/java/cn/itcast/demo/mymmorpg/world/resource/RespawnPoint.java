package cn.itcast.demo.mymmorpg.world.resource;

/**
 * 大世界资源刷新点：采集物 / 宝箱 / 解谜机关关联点。
 * <p>
 * syncMode：WORLD_SHARED=世界共享谁先拿谁得；PER_PLAYER=独立掉落每人各采各的。
 */
public record RespawnPoint(
        String pointId,
        int worldId,
        int sceneId,
        RespawnKind kind,
        float x,
        float y,
        float z,
        int templateId,
        int respawnSeconds,
        boolean oneShot,
        SyncMode syncMode) {

    public RespawnPoint(
            String pointId, int worldId, int sceneId, RespawnKind kind,
            float x, float y, float z, int templateId, int respawnSeconds, boolean oneShot) {
        this(pointId, worldId, sceneId, kind, x, y, z, templateId, respawnSeconds, oneShot, SyncMode.WORLD_SHARED);
    }

    public enum RespawnKind {
        GATHER,
        CHEST,
        MONSTER,
        PUZZLE
    }

    public enum SyncMode {
        /** 世界共享：全局 CD，玩家 B 客户端在 AOI/快照刷新后看到已采 */
        WORLD_SHARED,
        /** 独立掉落：按玩家维度 CD，互不影响 */
        PER_PLAYER
    }
}
