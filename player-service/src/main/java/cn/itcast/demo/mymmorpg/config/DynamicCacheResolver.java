/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/DynamicCacheResolver.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：按 @Cacheable 方法第一个 String 参数动态选择 Redis cache 区域，实现 map_config 等业务隔离。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import org.springframework.cache.Cache; // 单个 cache 区域抽象，对应 Redis 键前缀
import org.springframework.cache.CacheManager; // RedisCacheManager，按 name 获取 Cache 实例
import org.springframework.cache.interceptor.CacheOperationInvocationContext; // 封装被 @Cacheable 拦截的方法与实参
import org.springframework.cache.interceptor.CacheResolver; // Spring Cache 扩展点：决定写入哪个 Cache
import org.springframework.lang.NonNull; // 与 Spring Cache 接口 @NonNull 契约一致
import org.springframework.stereotype.Component; // 供 @Cacheable(cacheResolver = "dynamicCacheResolver") 按名引用
import java.util.Collection; // resolveCaches 返回待操作的 Cache 集合（通常单元素）
import java.util.Collections; // singleton 包装唯一 Cache
import java.util.Objects; // requireNonNull 满足静态分析
/**
 * 动态缓存解析器：通过方法第一个参数作为缓存区域名，实现业务缓存隔离。
 * 用法示例：@Cacheable(cacheResolver = "dynamicCacheResolver") void load(String area, int id)
 */

@Component("dynamicCacheResolver") // Bean 名与 cacheResolver 属性值一致

public class DynamicCacheResolver implements CacheResolver { // DynamicCacheResolver 类型定义
    /** 由 CacheConfig 注册的 RedisCacheManager，默认 TTL 30 分钟 */

    private final CacheManager cacheManager; // DynamicCacheResolver 字段
    public DynamicCacheResolver(CacheManager cacheManager) { // 构造 DynamicCacheResolver，注入 CacheManager cacheManager
        this.cacheManager = cacheManager; // 构造器注入 cacheManager
    } // DynamicCacheResolver 方法体结束
    /**
     * 解析目标 Cache：第一个参数必须是已注册的 cache 名（如 map_config、monster_config）。
     */

    @Override // 实现接口/父类方法
    @NonNull // @NonNull 注解
    @SuppressWarnings("null") // CacheManager.getCache 在 Spring 6 上可返回 null，下方已显式校验
    public Collection<? extends Cache> resolveCaches(@NonNull CacheOperationInvocationContext<?> context) { // DynamicCacheResolver.resolveCaches：@NonNull CacheOperationInvocationContext<?> context
        Object[] args = context.getArgs(); // @Cacheable 方法实参数组
        if (args.length < 1 || !(args[0] instanceof String area) || area.isBlank()) { // DynamicCacheResolver 方法
            // 调用方未传 area 无法路由，快速失败便于开发期发现注解误用
            throw new IllegalArgumentException("dynamic cache area is required as first method argument"); // 配置/路由错误快速失败
        } // if 方法体结束
        Cache cache = cacheManager.getCache(area); // 按名查找 RedisCacheConfiguration 注册的区域
        if (cache == null) { // 引用为空，无法继续业务
            throw new IllegalArgumentException("cache area not found: " + area); // 未在 RedisCacheManager 注册
        } // resolveCaches 方法体结束
        return Collections.singleton(Objects.requireNonNull(cache)); // 单区域读写，与 @Cacheable 默认行为一致
    } // DynamicCacheResolver 类体结束
} // 编译单元结束
