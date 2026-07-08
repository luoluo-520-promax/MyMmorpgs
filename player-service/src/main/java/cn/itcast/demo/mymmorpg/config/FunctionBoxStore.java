/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/config/FunctionBoxStore.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/config
 * 3) 主要职责：将玩家已解锁功能 ID 集合持久化到 Redis funcbox:{playerId}，避免改动 player 表 schema。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */

package cn.itcast.demo.mymmorpg.config; // player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关

import cn.itcast.demo.mymmorpg.model.FunctionBox; // 内存中维护 opened 功能 id 集合，线程外由 FunctionService 串行访问
import com.fasterxml.jackson.core.type.TypeReference; // 反序列化 Redis JSON 为 Set<Integer>
import com.fasterxml.jackson.databind.ObjectMapper; // 与全局 Jackson 配置一致，序列化功能 id 集合
import org.springframework.data.redis.core.StringRedisTemplate; // String 值存 JSON 数组，键 funcbox:{playerId}
import org.springframework.stereotype.Service; // FunctionService 构造器注入，与 FunctionConfigService 协作
import java.time.Duration; // Redis TTL：长期未登录玩家功能盒自动过期，减轻内存
import java.util.HashSet; // save 时对 snapshot 做防御性拷贝
import java.util.Set; // JSON 形态为 [100,101,102]
/**
 * FunctionBox 持久化：Redis 存储每个玩家已开启的功能集合，避免改动数据库 schema。
 */

@Service // Bean 名 functionBoxStore

public class FunctionBoxStore { // FunctionBoxStore 类型定义
    /** Redis 键前缀，完整键 funcbox:{playerId}，与 auth:token: 等键空间隔离 */

    private static final String KEY_PREFIX = "funcbox:"; // FunctionBoxStore 字段
    /** 30 天 TTL：玩家每次 save 刷新过期时间，冷数据自动回收 */

    private static final Duration TTL = Duration.ofDays(30); // FunctionBoxStore 方法
    /** 与 auth-service、缓存共用 StringRedisTemplate，连接由 spring.data.redis 配置 */

    private final StringRedisTemplate redis; // FunctionBoxStore 字段
    /** 共享 ObjectMapper，保证 Set 序列化字段顺序与测试一致 */

    private final ObjectMapper objectMapper; // FunctionBoxStore 字段
    public FunctionBoxStore(StringRedisTemplate redis, ObjectMapper objectMapper) { // 构造 FunctionBoxStore，注入 StringRedisTemplate redis, ObjectMapper objectMapper
        this.redis = redis; // 读写 funcbox:{playerId} 的 Redis 客户端
        this.objectMapper = objectMapper; // 功能 id 集合与 JSON 数组互转
    } // FunctionBoxStore 方法体结束
    /**
     * 从 Redis 加载玩家功能盒；键不存在或 JSON 损坏时返回空盒，不阻断登录主流程。
     */

    public FunctionBox load(long playerId) { // FunctionBoxStore.load：long playerId
        FunctionBox box = new FunctionBox(); // 默认无任何功能开启
        String json = redis.opsForValue().get(KEY_PREFIX + playerId); // GET funcbox:123
        if (json == null || json.isBlank()) { // 新玩家或 TTL 过期
            return box; // 空 FunctionBox，FunctionService 按配置重新判定解锁
        } // load 方法体结束
        try { // 代码块开始
            Set<Integer> ids = objectMapper.readValue(json, new TypeReference<Set<Integer>>() { // FunctionBoxStore.readValue：json, new TypeReference<Set<Integer>>(
            }); // 例：[100,101]
            box.replaceAll(ids); // 覆盖内存 opened 集合
        } catch (Exception ignored) { // FunctionBoxStore.catch：Exception ignored
            // 脏数据或版本不兼容：降级为空盒，FunctionService 可后续按配置重新 unlock
        } // catch 方法体结束
        return box; // 反序列化成功或脏数据降级后供 FunctionService 使用
    } // readValue 方法体结束

    /**
     * 将功能盒快照写入 Redis 并刷新 TTL；序列化失败抛 IllegalStateException 供上层事务回滚。
     */
    public void save(long playerId, FunctionBox box) { // FunctionBoxStore.save：long playerId, FunctionBox box
        try { // 代码块开始
            Set<Integer> ids = new HashSet<>(box.snapshot()); // 拷贝避免外部修改 opened 集合
            String json = objectMapper.writeValueAsString(ids); // 紧凑 JSON 数组
            redis.opsForValue().set(KEY_PREFIX + playerId, json, TTL); // SETEX 30d
        } catch (Exception e) { // FunctionBoxStore.catch：Exception e
            throw new IllegalStateException("FunctionBox 写入 Redis 失败 playerId=" + playerId, e); // 配置/路由错误快速失败
        } // catch 方法体结束
    } // 块 代码块结束
} // save 方法体结束
