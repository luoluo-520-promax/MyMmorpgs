/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/PlayerEntityCacheService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：玩家实体二级缓存（Spring Cache + 分段锁防击穿），脏标记延迟刷盘。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 玩家实体二级缓存（Spring Cache + 分段锁防击穿），脏标记延迟刷盘

import cn.itcast.demo.mymmorpg.entity.Player;
import cn.itcast.demo.mymmorpg.persist.DirtyFieldTracker;
import cn.itcast.demo.mymmorpg.persist.PositionStreamWriter;
import cn.itcast.demo.mymmorpg.port.PlayerCachePort;
import cn.itcast.demo.mymmorpg.repository.PlayerRepository; // MySQL player 表 JPA 仓储
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache; // Spring Cache 抽象，底层可为 Caffeine 或 Redis
import org.springframework.cache.CacheManager; // 获取 entity:player 缓存区域
import org.springframework.cache.annotation.CacheEvict; // AOP 驱逐缓存条目
import org.springframework.cache.annotation.CachePut; // AOP 写入/更新缓存条目
import org.springframework.cache.annotation.Cacheable; // AOP 查库后回填缓存
import org.springframework.context.annotation.Lazy; // 打破 @Cacheable 自调用 AOP 失效
import org.springframework.lang.Nullable; // 玩家不存在时返回 null 而非 Optional
import org.springframework.stereotype.Service; // entity:player 二级缓存 Bean，场景/进度/定时刷盘模块读写
import org.springframework.transaction.annotation.Transactional; // saveAndRefresh、delete、flush 声明事务

import java.util.Objects; // requireNonNull 校验
import java.util.Optional; // findByIdAndAccountId 返回 Optional
import java.util.Set; // dirtyPlayerIds 集合类型
import java.util.concurrent.ConcurrentHashMap; // 线程安全脏 ID 集合
import java.util.concurrent.locks.ReentrantLock; // 分段锁抑制缓存击穿

/**
 * 玩家实体缓存服务：读路径优先 entity:player 缓存，写路径更新缓存并可选打脏标记异步落库。
 * 与 PlayerTimerPersistenceService 配合：高频字段变更只改缓存，定时 flushDirtyNow 写 MySQL。
 */
@Service // entity:player 缓存 + 64 段锁防击穿 + dirtyPlayerIds 延迟 flush
public class PlayerEntityCacheService implements PlayerCachePort {

    /** Spring Cache 区域名，与 dynamicCacheResolver 配合路由到 Redis/Caffeine */
    public static final String PLAYER_CACHE_AREA = "entity:player"; // Spring Cache 区域名，与 dynamicCacheResolver 配合路由到 Redis/Caffeine

    /** 分段锁数量，playerId 哈希取模后同段玩家互斥，不同段可并行查库 */
    private static final int LOCK_SEGMENTS = 64; // 分段锁数量，playerId 哈希取模后同段玩家互斥，不同段可并行查库

    /** 玩家表 JPA 仓储，缓存未命中或 flush 时访问 MySQL */
    private final PlayerRepository playerRepository; // 玩家表 JPA 仓储，缓存未命中或 flush 时访问 MySQL

    /** Spring 缓存管理器，getCache("entity:player") 获取底层 Cache 实例 */
    private final CacheManager cacheManager; // Spring 缓存管理器，getCache("entity:player") 获取底层 Cache 实例

    /** 自引用代理，使 loadById/putCache/evictCache 的 @Cacheable 等 AOP 生效 */
    private final PlayerEntityCacheService self; // 自引用代理，使 loadById/putCache/evictCache 的 @Cacheable 等 AOP 生效

    /** 64 把 ReentrantLock，按 playerId 分段，双重检查锁模式防击穿 */
    private final ReentrantLock[] locks = new ReentrantLock[LOCK_SEGMENTS]; // 64 把 ReentrantLock，按 playerId 分段，双重检查锁模式防击穿

    /** 已修改但未落库的 playerId 集合，PlayerTimerPersistenceService 周期性 flush */
    private final Set<Long> dirtyPlayerIds = ConcurrentHashMap.newKeySet(); // 已修改但未落库的 playerId 集合，PlayerTimerPersistenceService 周期性 flush

    /** 字段级脏标记：大世界移动只标 POSITION，避免全量刷盘 */
    private final DirtyFieldTracker fieldTracker = new DirtyFieldTracker();

    private final PositionStreamWriter positionStreamWriter;

    /**
     * 构造器：仓储、缓存管理器与 @Lazy 自引用。
     */
    public PlayerEntityCacheService( // 构造器：仓储、缓存管理器与 @Lazy 自引用
            PlayerRepository playerRepository, // player 表 JPA 查库与 save
            CacheManager cacheManager, // 获取 entity:player 缓存区域
            @Lazy // 延迟代理，避免构造期循环依赖
            PlayerEntityCacheService self) { // @Cacheable 自调用 AOP 代理
        this(playerRepository, cacheManager, self, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PlayerEntityCacheService(
            PlayerRepository playerRepository,
            CacheManager cacheManager,
            @Lazy PlayerEntityCacheService self,
            ObjectProvider<PositionStreamWriter> positionStreamWriter) {
        this.playerRepository = playerRepository;
        this.cacheManager = cacheManager;
        this.self = self;
        this.positionStreamWriter = positionStreamWriter == null
                ? new PositionStreamWriter()
                : (positionStreamWriter.getIfAvailable() != null
                ? positionStreamWriter.getIfAvailable() : new PositionStreamWriter());
        for (int i = 0; i < LOCK_SEGMENTS; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    /**
     * 按 playerId 查玩家：先读缓存，未命中则分段锁 + 双重检查后 loadById 查库并回填。
     *
     * @param playerId 角色 ID
     * @return Player 实体，不存在时 null
     */
    @Nullable // 按 playerId 查玩家：先读缓存，未命中则分段锁 + 双重检查后 loadById 查库并回填
    public Player findById(long playerId) { // 按 playerId 查玩家：先读缓存，未命中则分段锁 + 双重检查后 loadById 查库并回填
        if (playerId <= 0) { // playerId 非法
            return null; // 无玩家实体
        }
        Cache cache = cacheManager.getCache(PLAYER_CACHE_AREA); // 取 entity:player 缓存
        if (cache != null) { // 缓存区域已配置
            Cache.ValueWrapper hit = cache.get(playerId); // 先读缓存
            if (hit != null) { // 缓存命中
                return (Player) hit.get(); // 直接取 Player 实体
            }
        }
        ReentrantLock lock = lockOf(playerId); // 按 playerId 取分段锁
        lock.lock(); // 持锁防并发击穿
        try { // 持锁期间查库并回填，finally 保证分段锁释放
            if (cache != null) { // 双重检查：持锁期间可能已回填
                Cache.ValueWrapper secondHit = cache.get(playerId); // 再次读缓存
                if (secondHit != null) { // 其他线程已回填
                    return (Player) secondHit.get(); // 取缓存 Player
                }
            }
            return self.loadById(PLAYER_CACHE_AREA, playerId); // @Cacheable 查库并回填
        } finally { // 无论查库成功或异常均释放分段锁
            lock.unlock(); // 释放分段锁
        }
    }

    /**
     * 查库并回填缓存，@Cacheable AOP 拦截成功后写入 entity:player:{playerId}。
     */
    @Nullable // 查库并回填缓存，@Cacheable AOP 拦截成功后写入 entity:player:{playerId}
    @Cacheable(cacheResolver = "dynamicCacheResolver", key = "#playerId", unless = "#result == null") // 查库并回填缓存，@Cacheable AOP 拦截成功后写入 entity:player:{playerId}
    public Player loadById(String cacheArea, long playerId) { // 查库并回填缓存，@Cacheable AOP 拦截成功后写入 entity:player:{playerId}
        return playerRepository.findById(playerId).orElse(null); // SELECT player WHERE id=?
    }

    /**
     * 同步写库并刷新缓存，用于必须立即持久化的场景（如创角、删角）。
     */
    @Transactional // 声明数据库事务边界，保证 MMORPG 写操作原子性
    public Player saveAndRefresh(Player player) { // 同步写库并刷新缓存，用于必须立即持久化的场景（如创角、删角）
        Player saved = playerRepository.save(Objects.requireNonNull(player)); // INSERT/UPDATE player 表
        return Objects.requireNonNull(self.putCache(PLAYER_CACHE_AREA, saved)); // @CachePut 刷新 entity:player
    }

    /**
     * 仅更新 entity:player 缓存并打脏标记，真正落库交给 PlayerTimerPersistenceService 定时 flush。
     * 用于大世界移动、加经验等高频写，降低 MySQL QPS。
     */
    public Player saveCacheAndMarkDirty(Player player) { // 仅更新 entity:player 缓存并打脏标记，真正落库交给 PlayerTimerPersistenceService 定时 flush
        Player updated = Objects.requireNonNull(self.putCache(PLAYER_CACHE_AREA, Objects.requireNonNull(player))); // 更新缓存
        Long playerId = updated.getId(); // 脏标记主键
        if (playerId != null && playerId > 0) { // 有效 playerId
            dirtyPlayerIds.add(playerId); // 加入脏集合，定时器 flush
            fieldTracker.mark(playerId, DirtyFieldTracker.Field.FULL);
        }
        return updated; // 含最新字段的 Player
    }

    /**
     * 大世界移动：位置写入 Redis Stream，仅标记 POSITION 脏字段，降低 MySQL 写放大。
     */
    public void markPositionDirty(long playerId, int sceneId, int lineId, float x, float y, float z) {
        if (playerId <= 0) {
            return;
        }
        dirtyPlayerIds.add(playerId);
        fieldTracker.mark(playerId, DirtyFieldTracker.Field.POSITION);
        positionStreamWriter.append(playerId, sceneId, lineId, x, y, z, System.currentTimeMillis());
    }

    public DirtyFieldTracker fieldTracker() {
        return fieldTracker;
    }

    public PositionStreamWriter positionStreamWriter() {
        return positionStreamWriter;
    }

    /**
     * @CachePut 强制更新缓存条目，key 为 result.id。
     */
    @CachePut(cacheResolver = "dynamicCacheResolver", key = "#result.id", unless = "#result == null") // Spring 缓存/AOP 注解，配合 entity 二级缓存使用
    public Player putCache(String cacheArea, Player player) { // 方法 putCache：处理 MMORPG 客户端协议或内部编排
        return Objects.requireNonNull(player); // @CachePut 将 Player 实体 JSON 写入 entity:player:{id} 缓存区
    }

    /**
     * 删库并驱逐缓存，用于删角等场景。
     */
    @Transactional // 声明数据库事务边界，保证 MMORPG 写操作原子性
    public void deleteByIdAndEvict(long playerId) { // 删库并驱逐缓存，用于删角等场景
        playerRepository.deleteById(playerId); // DELETE player WHERE id=?
        self.evictCache(PLAYER_CACHE_AREA, playerId); // @CacheEvict 移除缓存条目
    }

    /**
     * @CacheEvict 从 entity:player 移除指定 playerId 条目。
     */
    @CacheEvict(cacheResolver = "dynamicCacheResolver", key = "#playerId") // Spring 缓存/AOP 注解，配合 entity 二级缓存使用
    public void evictCache(String cacheArea, long playerId) { // 方法 evictCache：处理 MMORPG 客户端协议或内部编排
        // @CacheEvict AOP 在方法退出前移除 entity:player:{playerId}，方法体无需手写逻辑
    }

    /**
     * 判断玩家是否存在（走缓存，不存在时不查库若已缓存 null——当前实现未缓存 null，会查库）。
     */
    public boolean existsById(long playerId) { // 判断玩家是否存在（走缓存，不存在时不查库若已缓存 null——当前实现未缓存 null，会查库）
        return findById(playerId) != null; // 缓存或库中存在即 true
    }

    /**
     * 按 playerId + accountId 联合查库，用于选角归属校验（不走缓存，保证 accountId 准确）。
     */
    public Optional<Player> findByIdAndAccountId(long playerId, long accountId) { // 按 playerId + accountId 联合查库，用于选角归属校验（不走缓存，保证 accountId 准确）
        return playerRepository.findByIdAndAccountId(playerId, accountId); // 联合校验角色归属
    }

    /**
     * 脏数据刷盘：仅当 playerId 在 dirtyPlayerIds 中时才写 MySQL，成功后清除脏标记。
     * 由 PlayerTimerPersistenceService.onTimerTick 周期性调用。
     *
     * @return true 表示本次确实执行了 save
     */
    @Transactional // 声明数据库事务边界，保证 MMORPG 写操作原子性
    public boolean flushDirtyNow(long playerId) { // 脏数据刷盘：仅当 playerId 在 dirtyPlayerIds 中时才写 MySQL，成功后清除脏标记
        if (playerId <= 0 || !dirtyPlayerIds.contains(playerId)) { // 无脏标记或 playerId 非法
            return false; // 跳过写库
        }
        Player player = findById(playerId); // 从缓存读最新 Player
        if (player == null) { // 缓存与脏标记不一致
            dirtyPlayerIds.remove(playerId); // 清理脏标记，避免无限 retry
            return false; // 无实体可写
        }
        playerRepository.save(player); // UPDATE player 表
        dirtyPlayerIds.remove(playerId); // flush 成功清除脏标记
        fieldTracker.clear(playerId);
        return true; // 本次确实写库
    }

    /**
     * 登出 stopPlayerTimer 在 flush 后清除脏标记，防止重复写库。
     */
    public void clearDirtyFlag(long playerId) { // 登出 stopPlayerTimer 在 flush 后清除脏标记，防止重复写库
        if (playerId > 0) { // 有效 playerId
            dirtyPlayerIds.remove(playerId); // 登出后不再 flush
            fieldTracker.clear(playerId);
        }
    }

    /**
     * 按 playerId 哈希取模选择分段锁，同一玩家始终映射到同一把锁。
     */
    private ReentrantLock lockOf(long playerId) { // 按 playerId 哈希取模选择分段锁，同一玩家始终映射到同一把锁
        return locks[(int) (Math.abs(playerId) % LOCK_SEGMENTS)]; // 同 playerId 固定同一把锁
    }
}
