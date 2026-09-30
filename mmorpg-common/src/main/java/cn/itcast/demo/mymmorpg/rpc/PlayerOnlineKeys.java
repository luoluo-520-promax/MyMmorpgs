package cn.itcast.demo.mymmorpg.rpc;

/**
 * 玩家在线会话 Redis 键约定（player-service 写入，hall/运维只读）。
 * <p>
 * 新模型以 Hash {@code online:user:{playerId}} 为权威；
 * 兼容键 {@code player:online:{playerId}} 仍写入 session_id，供旧查询。
 */
public final class PlayerOnlineKeys {

    /** 兼容旧版：值为 session_id 字符串 */
    public static final String KEY_PREFIX = "player:online:";
    /** 全局在线玩家 ID 集合（SCARD 统计） */
    public static final String KEY_ONLINE_SET = "player:online:ids";
    /** 精细在线态 Hash：session_id/node_id/scene_id/login_time/... */
    public static final String ONLINE_USER_PREFIX = "online:user:";
    /** 节点维度在线集合：故障迁移时按 node 扫描 */
    public static final String ONLINE_NODE_PREFIX = "online:node:";
    /** 跨服在线 Bitmap（按 playerId 低位偏移，快速判定） */
    public static final String ONLINE_BITMAP = "online:presence:bitmap";
    /** 在线变更 Pub/Sub 频道 */
    public static final String ONLINE_CHANNEL = "online:presence:changed";

    private PlayerOnlineKeys() {
    }

    public static String key(long playerId) {
        return KEY_PREFIX + playerId;
    }

    public static String userHash(long playerId) {
        return ONLINE_USER_PREFIX + playerId;
    }

    public static String nodeSet(String nodeId) {
        return ONLINE_NODE_PREFIX + (nodeId == null || nodeId.isBlank() ? "local" : nodeId);
    }
}
