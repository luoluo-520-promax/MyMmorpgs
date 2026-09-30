package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.rpc.PlayerOnlineKeys;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 跨服务查询玩家是否在线（读 Redis {@code player:online:{id}}）。
 */
@Service
public class PlayerPresenceQuery {

    private final ObjectProvider<StringRedisTemplate> redisTemplate;

    public PlayerPresenceQuery(ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isOnline(long playerId) {
        if (playerId <= 0) {
            return false;
        }
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(PlayerOnlineKeys.key(playerId)));
    }
}
