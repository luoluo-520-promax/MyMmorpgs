/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/GameDataWarmupStarter.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：Spring 应用就绪监听器，触发 GameDataWarmupService 异步预热配置表缓存。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // ApplicationReady 后触发 map_config/item_config 等配置表预热

import org.slf4j.Logger; // 记录预热启动与初始状态日志
import org.slf4j.LoggerFactory; // 创建 GameDataWarmupStarter 专用 Logger
import org.springframework.boot.context.event.ApplicationReadyEvent; // Spring Boot 完全就绪事件（HTTP 端口已监听）
import org.springframework.context.ApplicationListener; // 监听 ApplicationReadyEvent 回调
import org.springframework.core.Ordered; // 控制多个 ApplicationListener 相对执行顺序
import org.springframework.lang.NonNull; // 标注 onApplicationEvent 的 event 参数非空
import org.springframework.stereotype.Component; // Spring 容器管理，自动注册 ApplicationListener

/**
 * 应用就绪后触发异步游戏数据预热。
 */
@Component // Spring 容器单例，监听 ApplicationReadyEvent
public class GameDataWarmupStarter implements ApplicationListener<ApplicationReadyEvent>, Ordered { // 就绪后触发配置表并行预热

    private static final Logger log = LoggerFactory.getLogger(GameDataWarmupStarter.class); // 预热启动日志

    /** 配置表预热协调器，并行加载地图/怪物/技能等至 ConfigQueryService 缓存 */
    private final GameDataWarmupService warmupService; // warmupAsync 六路 CompletableFuture 并行预热

    public GameDataWarmupStarter(GameDataWarmupService warmupService) { // 应用就绪后触发异步游戏数据预热
        this.warmupService = warmupService; // ApplicationReady 时调用 warmupAsync，不阻塞主线程
    }

    /** Spring Boot 就绪回调：容器启动完成、可接受请求前触发预热 */
    @Override // ApplicationListener 接口实现
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) { // HTTP/WebSocket 端口已监听
        warmupService.warmupAsync(); // 异步并行预热 map_config/monster_config/skill_config 等至 ConfigQueryService
        log.info("game data warmup state={}", warmupService.state()); // 记录初始状态，多为 RUNNING
    }

    /** 监听器顺序：数值越小越先执行，10 略晚于 Redis/JPA 基础设施初始化 */
    @Override // Ordered 接口实现
    public int getOrder() { // 应用就绪后触发异步游戏数据预热
        return 10; // 确保 StringRedisTemplate 与 JPA EntityManager 已就绪再预热配置表
    }
}
