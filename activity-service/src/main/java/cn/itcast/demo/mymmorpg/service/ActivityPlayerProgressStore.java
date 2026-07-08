/**
 * 文件维护说明
 * 1) 文件路径：activity-service/src/main/java/cn/itcast/demo/mymmorpg/service/ActivityPlayerProgressStore.java
 * 2) 所属模块：activity-service / service
 * 3) 主要职责：封装玩家活动进度在 Redis 中的读写与 TTL 管理
 * 4) 系统位置：基础设施层，被 ActivityService 在列表/详情/领奖流程中使用
 * 5) 风险提示：写 Redis 失败时静默忽略，不阻断主流程
 */
package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.model.PlayerActivityProgress; // 玩家活动进度模型
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 与 Java 对象互转
import org.springframework.data.redis.core.StringRedisTemplate; // Spring Data Redis 字符串模板
import org.springframework.stereotype.Component; // 注册为 Spring 组件

import java.time.Duration; // 过期时间 Duration
import java.util.Optional; // 可选返回值

/**
 * 玩家活动进度在 Redis 中的读写封装。
 */
@Component // 注册为 Spring Bean
public class ActivityPlayerProgressStore { // 玩家活动进度 Redis 存取组件

    /** 进度 key 过期时间 120 天，避免 Redis 无限增长。 */
    private static final Duration TTL = Duration.ofDays(120); // Redis 键 TTL

    /** Spring Data Redis 字符串模板。 */
    private final StringRedisTemplate stringRedisTemplate; // Redis 字符串操作
    /** JSON 与 Java 对象互转器。 */
    private final ObjectMapper objectMapper; // Jackson 序列化器

    /**
     * 构造器注入 Redis 模板与 ObjectMapper。
     *
     * @param stringRedisTemplate Redis 字符串模板
     * @param objectMapper        JSON 序列化器
     */
    public ActivityPlayerProgressStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate; // 保存 Redis 模板
        this.objectMapper = objectMapper; // 保存 JSON 映射器
    } // 构造器结束

    /**
     * 生成 Redis 键：activity:prog:{玩家ID}:{活动ID}。
     *
     * @param playerId   玩家 ID
     * @param activityId 活动 ID
     * @return Redis 键字符串
     */
    public static String redisKey(long playerId, long activityId) {
        return "activity:prog:" + playerId + ":" + activityId; // 拼接标准键名
    } // redisKey 结束

    /**
     * 尝试加载进度；不存在或解析失败时返回 Optional.empty 或空对象。
     *
     * @param playerId   玩家 ID
     * @param activityId 活动 ID
     * @return 进度 Optional
     */
    public Optional<PlayerActivityProgress> load(long playerId, long activityId) {
        String json = stringRedisTemplate.opsForValue().get(redisKey(playerId, activityId)); // 从 Redis 读取 JSON
        if (json == null || json.isBlank()) { // 键不存在或为空
            return Optional.empty(); // 表示无进度
        } // if 结束
        try {
            return Optional.of(objectMapper.readValue(json, PlayerActivityProgress.class)); // 反序列化为进度对象
        } catch (Exception e) {
            return Optional.of(new PlayerActivityProgress()); // 脏数据时降级为空进度，避免整段逻辑崩溃
        } // try-catch 结束
    } // load 结束

    /**
     * 有则加载，无则 new 一个默认进度（内存中尚未写入 Redis）。
     *
     * @param playerId   玩家 ID
     * @param activityId 活动 ID
     * @return 非空进度对象
     */
    public PlayerActivityProgress loadOrCreate(long playerId, long activityId) {
        return load(playerId, activityId).orElseGet(PlayerActivityProgress::new); // 不存在则新建默认进度
    } // loadOrCreate 结束

    /**
     * 把进度对象序列化后写入 Redis，并设置 TTL。
     *
     * @param playerId   玩家 ID
     * @param activityId 活动 ID
     * @param progress   待保存的进度
     */
    public void save(long playerId, long activityId, PlayerActivityProgress progress) {
        try {
            String json = objectMapper.writeValueAsString(progress); // 序列化为 JSON 字符串
            stringRedisTemplate.opsForValue().set(redisKey(playerId, activityId), json, TTL); // 写入 Redis 并设置过期
        } catch (Exception ignored) {
            // 不阻断主流程：写 Redis 失败时仍让领奖等主逻辑尽量完成（由调用方决定）
        } // try-catch 结束
    } // save 结束
} // ActivityPlayerProgressStore 类结束
