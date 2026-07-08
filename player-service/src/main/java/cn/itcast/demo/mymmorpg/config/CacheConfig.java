/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/CacheConfig.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：启用 @Cacheable/@Async，注册 RedisCacheManager 缓存 map_config、monster_config 等静态表。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.cache.annotation.EnableCaching; // 激活 @Cacheable/@CacheEvict AOP 代理
import org.springframework.scheduling.annotation.EnableAsync; // 激活 @Async，PlayerDataAsyncPreloadService 等异步预加载
import org.springframework.context.annotation.Bean; // 注册 RedisCacheManager 单例
import org.springframework.context.annotation.Configuration; // 缓存基础设施集中配置
import org.springframework.data.redis.cache.RedisCacheConfiguration; // 单 cache 区域序列化、TTL、null 策略
import org.springframework.data.redis.cache.RedisCacheManager; // 基于 Redis 的 CacheManager 实现
import org.springframework.data.redis.connection.RedisConnectionFactory; // Lettuce 连接，来自 spring.data.redis
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer; // 值 JSON 序列化，支持复杂实体
import org.springframework.data.redis.serializer.RedisSerializationContext; // 键值 SerializationPair 构建器
import org.springframework.data.redis.serializer.StringRedisSerializer; // 键 UTF-8 字符串，便于 redis-cli 可读
import java.time.Duration; // 默认 entry TTL 30 分钟
import java.util.Objects; // requireNonNull 满足 builder API
/**
 * Redis 缓存 map_config / monster_config，减轻数据库读压力；与 DynamicCacheResolver 配合按 area 分区。
 */

@Configuration // 配置类集中装配 Bean
@EnableCaching // Config 类加载后即注册 CacheInterceptor
@EnableAsync // 与 EventBus 预加载、聊天异步落库共用线程池（默认 SimpleAsyncTaskExecutor）

public class CacheConfig { // CacheConfig 类型定义
    /**
     * 全局 Redis 缓存管理器：键 String、值 Jackson JSON、禁止缓存 null、参与 Spring 事务同步。
     */

    @Bean // 注入名 redisCacheManager，DynamicCacheResolver 与 @Cacheable 默认使用
    RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) { // CacheConfig.redisCacheManager：RedisConnectionFactory connectionFactory
        var json = new GenericJackson2JsonRedisSerializer(); // 值带 @class 类型信息，反序列化回实体
        Duration ttl = Objects.requireNonNull(Duration.ofMinutes(30)); // 静态配置表变更不频繁，30min 折中
        var config = RedisCacheConfiguration.defaultCacheConfig() // CacheConfig 逻辑
                .entryTtl(ttl) // 每个 cache entry 独立过期
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())) // cache 键 UTF-8 字符串，redis-cli 可读
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(json)) // cache 值 Jackson JSON，反序列化回实体
                .disableCachingNullValues(); // 避免缓存穿透占位 null 污染 Redis
        return RedisCacheManager.builder(Objects.requireNonNull(connectionFactory)) // 绑定 Lettuce 连接工厂
                .cacheDefaults(config) // 未显式命名的 cache 区域沿用此默认
                .transactionAware() // @Transactional 提交后再写入缓存，回滚时不脏写
                .build(); // 供 @Cacheable 与 DynamicCacheResolver 注入使用
    } // redisCacheManager 方法体结束
} // CacheConfig 类体结束
