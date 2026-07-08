/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerSessionService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：Redis 维护玩家在线会话标记，供跨节点查询与 GM 统计在线人数。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // Redis 维护玩家在线会话标记，供跨节点查询与 GM 统计在线人数

import org.springframework.data.redis.core.StringRedisTemplate; // 读写 player:online:{playerId} 键值
import org.springframework.stereotype.Service; // Redis 在线标记 Bean，选角 markOnline、登出 markOffline

import java.time.Duration; // 在线标记 TTL，防止异常断线后键永久残留

/**
 * 在线玩家会话（Redis）：键 player:online:{playerId}，值为通道/节点标识，24h 自动过期。
 * 与 PlayerPushRegistry 内存绑定互补——PushRegistry 只管本机连接，Redis 供集群感知在线态。
 */
@Service // player:online:{playerId} 集群级在线态，与 PushRegistry 本机连接映射互补
public class PlayerSessionService { // 在线玩家会话（Redis）：键 player:online:{playerId}，值为通道/节点标识，24h 自动过期

    /** Redis 键前缀，完整键 player:online:{playerId}，值为 sessionMarker（如节点 ID + channelId） */
    private static final String KEY_PREFIX = "player:online:"; // Redis 键前缀，完整键 player:online:{playerId}，值为 sessionMarker（如节点 ID + channelId）

    /** 在线标记 TTL：24 小时，覆盖一般挂机时长；登出时主动 delete，不依赖 TTL */
    private static final Duration TTL = Duration.ofHours(24); // 在线标记 TTL：24 小时，覆盖一般挂机时长；登出时主动 delete，不依赖 TTL

    /** 与 auth-service、网关共享的 Redis 集群客户端 */
    private final StringRedisTemplate redisTemplate; // 与 auth-service、网关共享的 Redis 集群客户端

    /**
     * 构造器：StringRedisTemplate 读写 player:online 键。
     */
    public PlayerSessionService(StringRedisTemplate redisTemplate) { // 构造器：StringRedisTemplate 读写 player:online 键
        this.redisTemplate = redisTemplate; // 集群级 player:online:{playerId} 在线标记
    }

    /**
     * 选角成功或重连后写入在线标记，供其他微服务或 GM 工具查询玩家是否在线。
     *
     * @param playerId       已选角色 ID
     * @param sessionMarker  会话标识（如 ws 节点 + sessionId），便于排查多开/顶号
     */
    public void markOnline(long playerId, String sessionMarker) { // 选角成功或重连后写入在线标记，供其他微服务或 GM 工具查询玩家是否在线
        redisTemplate.opsForValue().set(KEY_PREFIX + playerId, sessionMarker, TTL); // SETEX player:online:{playerId}
    }

    /**
     * 登出或断线清理时删除在线键，避免 Redis 仍显示玩家在线。
     *
     * @param playerId 待下线角色 ID
     */
    public void markOffline(long playerId) { // 登出或断线清理时删除在线键，避免 Redis 仍显示玩家在线
        redisTemplate.delete(KEY_PREFIX + playerId); // DEL player:online:{playerId}
    }

    /**
     * 判断 Redis 中是否存在该玩家的在线键（集群级在线，非本机 PushRegistry）。
     *
     * @param playerId 角色 ID
     * @return true 表示键存在，玩家被标记为在线
     */
    public boolean isOnline(long playerId) { // 判断 Redis 中是否存在该玩家的在线键（集群级在线，非本机 PushRegistry）
        return redisTemplate.hasKey(KEY_PREFIX + playerId); // EXISTS player:online:{playerId}
    }

    /**
     * 统计当前 Redis 中 player:online:* 键数量，供监控或 GM 面板展示在线人数。
     * 注意：keys 命令在大规模生产环境应改用 SCAN，此处为简化实现。
     */
    public long countOnline() { // 统计当前 Redis 中 player:online:* 键数量，供监控或 GM 面板展示在线人数
        var keys = redisTemplate.keys(KEY_PREFIX + "*"); // 扫描 player:online:* 键
        return keys == null ? 0 : keys.size(); // 键数量即集群在线角色数
    }
}
