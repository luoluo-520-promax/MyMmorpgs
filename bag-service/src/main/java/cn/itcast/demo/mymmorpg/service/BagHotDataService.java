package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.entity.PlayerBagItem;
import cn.itcast.demo.mymmorpg.repository.PlayerBagItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 背包热数据：登录加载至 Redis Hash，脏标记后定时/下线刷入 MySQL（冷数据）。
 */
@Service
public class BagHotDataService {

    private static final String HOT_KEY = "hot:bag:";
    private static final String DIRTY_SET = "hot:bag:dirty";

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectProvider<PlayerBagItemRepository> bagRepo;
    private final ObjectMapper objectMapper;
    private final Set<Long> localDirty = ConcurrentHashMap.newKeySet();
    private final long ttlMinutes;

    public BagHotDataService(
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectProvider<PlayerBagItemRepository> bagRepo,
            ObjectMapper objectMapper,
            @Value("${game.bag.hot-ttl-minutes:30}") long ttlMinutes) {
        this.redisProvider = redisProvider;
        this.bagRepo = bagRepo;
        this.objectMapper = objectMapper;
        this.ttlMinutes = Math.max(5L, ttlMinutes);
    }

    /** 登录时从 MySQL 加载热数据到 Redis。 */
    public void loadOnLogin(long playerId) {
        PlayerBagItemRepository repo = bagRepo.getIfAvailable();
        StringRedisTemplate redis = redis();
        if (repo == null || redis == null || playerId <= 0) {
            return;
        }
        List<PlayerBagItem> rows = repo.findByPlayerIdOrderBySlotIndexAsc(playerId);
        try {
            String json = objectMapper.writeValueAsString(rows.size());
            redis.opsForHash().put(HOT_KEY + playerId, "count", json);
            redis.opsForHash().put(HOT_KEY + playerId, "loadedAt", String.valueOf(System.currentTimeMillis()));
            redis.expire(HOT_KEY + playerId, Duration.ofMinutes(ttlMinutes));
        } catch (Exception ignored) {
            // best-effort
        }
    }

    public void markDirty(long playerId) {
        if (playerId <= 0) {
            return;
        }
        StringRedisTemplate redis = redis();
        if (redis != null) {
            redis.opsForSet().add(DIRTY_SET, String.valueOf(playerId));
        } else {
            localDirty.add(playerId);
        }
    }

    public void flushPlayer(long playerId) {
        // 背包写路径已直写 MySQL；此处清理脏标记并刷新 TTL，作为冷热分离调度钩子
        StringRedisTemplate redis = redis();
        if (redis != null) {
            redis.opsForSet().remove(DIRTY_SET, String.valueOf(playerId));
            redis.expire(HOT_KEY + playerId, Duration.ofMinutes(ttlMinutes));
        }
        localDirty.remove(playerId);
    }

    @Scheduled(fixedDelayString = "${game.bag.hot-flush-ms:300000}")
    public void flushDirtyBatch() {
        StringRedisTemplate redis = redis();
        if (redis != null) {
            Set<String> members = redis.opsForSet().members(DIRTY_SET);
            if (members != null) {
                for (String id : members) {
                    flushPlayer(Long.parseLong(id));
                }
            }
            return;
        }
        for (Long id : Set.copyOf(localDirty)) {
            flushPlayer(id);
        }
    }

    private StringRedisTemplate redis() {
        return redisProvider == null ? null : redisProvider.getIfAvailable();
    }
}
