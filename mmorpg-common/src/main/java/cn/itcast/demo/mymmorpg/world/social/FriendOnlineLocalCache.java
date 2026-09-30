package cn.itcast.demo.mymmorpg.world.social;

import cn.itcast.demo.mymmorpg.rpc.PlayerOnlineKeys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 好友在线本地缓存 + Redis Pub/Sub 同步，降低跨服频繁查询。
 */
@Component
public class FriendOnlineLocalCache implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(FriendOnlineLocalCache.class);

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<RedisMessageListenerContainer> containerProvider;
    private final ConcurrentHashMap<Long, Boolean> cache = new ConcurrentHashMap<>();

    public FriendOnlineLocalCache(ObjectProvider<StringRedisTemplate> redisProvider,
                                  ObjectProvider<RedisMessageListenerContainer> containerProvider) {
        this.redisProvider = redisProvider;
        this.containerProvider = containerProvider;
    }

    @PostConstruct
    public void subscribe() {
        RedisMessageListenerContainer container = containerProvider == null ? null : containerProvider.getIfAvailable();
        if (container == null) {
            return;
        }
        try {
            container.addMessageListener(this, new ChannelTopic(PlayerOnlineKeys.ONLINE_CHANNEL));
        } catch (Exception e) {
            log.debug("friend online pubsub not available: {}", e.getMessage());
        }
    }

    public boolean isOnline(long playerId) {
        Boolean cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }
        StringRedisTemplate redis = redis();
        if (redis == null) {
            return false;
        }
        try {
            Boolean bit = redis.opsForValue().getBit(PlayerOnlineKeys.ONLINE_BITMAP, playerId & 0xFFFFFF);
            boolean online = Boolean.TRUE.equals(bit)
                    || Boolean.TRUE.equals(redis.hasKey(PlayerOnlineKeys.userHash(playerId)))
                    || Boolean.TRUE.equals(redis.hasKey(PlayerOnlineKeys.key(playerId)));
            cache.put(playerId, online);
            return online;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<Long, Boolean> snapshot() {
        return Map.copyOf(cache);
    }

    public void put(long playerId, boolean online) {
        cache.put(playerId, online);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        int idx = body.indexOf(':');
        if (idx <= 0) {
            return;
        }
        try {
            long playerId = Long.parseLong(body.substring(0, idx));
            boolean online = "1".equals(body.substring(idx + 1));
            cache.put(playerId, online);
        } catch (NumberFormatException ignored) {
            // ignore
        }
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
