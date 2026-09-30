package cn.itcast.demo.mymmorpg.center;

/**
 * Center 返回的场景迁移计划。
 */
public record SceneMigrationPlan(
        int sceneId,
        int zoneId,
        String nodeId,
        String nodeHost,
        int nodePort,
        boolean local
) {
    public static SceneMigrationPlan localPlan(int sceneId, String nodeId, String host, int port) {
        return new SceneMigrationPlan(sceneId, sceneId, nodeId, host == null ? "" : host, port, true);
    }

    public static SceneMigrationPlan remotePlan(int sceneId, int zoneId, String nodeId, String host, int port) {
        return new SceneMigrationPlan(sceneId, zoneId, nodeId, host == null ? "" : host, port, false);
    }
}
