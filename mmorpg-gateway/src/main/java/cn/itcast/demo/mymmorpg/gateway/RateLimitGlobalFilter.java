/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/RateLimitGlobalFilter.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：基于 Redis 的全局与用户级 QPS 限流，超限返回 429。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import com.fasterxml.jackson.core.JsonProcessingException; // 429 响应 JSON 序列化失败时捕获
import com.fasterxml.jackson.databind.ObjectMapper; // 构造标准 JSON 错误体
import org.springframework.cloud.gateway.filter.GatewayFilterChain; // 限流通过后继续转发至后端
import org.springframework.cloud.gateway.filter.GlobalFilter; // 对所有进入网关的请求生效
import org.springframework.core.Ordered; // 限流在认证(-90)之后执行，以便按 accountId 限流
import org.springframework.data.redis.core.StringRedisTemplate; // INCR 实现分布式秒级计数器
import org.springframework.http.HttpStatus; // 超限时返回 429 TOO_MANY_REQUESTS
import org.springframework.http.MediaType; // 错误响应 Content-Type: application/json
import org.springframework.http.server.reactive.ServerHttpRequest; // 读取 X-Account-Id、X-Forwarded-For、remoteAddress
import org.springframework.stereotype.Component; // 自动注册到 Gateway 过滤器链
import org.springframework.util.AntPathMatcher; // 白名单路径 Ant 模式匹配
import org.springframework.web.server.ServerWebExchange; // 请求/响应上下文
import reactor.core.publisher.Mono; // filter 异步返回 Mono<Void>

import java.nio.charset.StandardCharsets; // JSON 降级编码
import java.time.Instant; // 取当前 Unix 秒作为 Redis 键的时间桶后缀
import java.util.List; // 白名单路径列表
import java.util.Map; // 429 响应 JSON 字段
import java.util.concurrent.TimeUnit; // Redis 键过期时间单位 SECONDS


@Component
@SuppressWarnings("null")
public class RateLimitGlobalFilter implements GlobalFilter, Ordered { // 全局限流过滤器，order=-80 位于认证之后

    /** 与 AuthGlobalFilter 一致，读取认证过滤器注入的账号 ID 作为用户限流维度 */
    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";

    /** 反向代理（Nginx/Ingress）写入的客户端真实 IP 链，取第一个 IP 作为未登录用户的限流键 */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /** 全网关秒级计数 Redis 键前缀，完整键 gateway:rl:total:{epochSecond}，值为该秒请求总数 */
    private static final String KEY_TOTAL_PREFIX = "gateway:rl:total:";

    /** 单用户秒级计数 Redis 键前缀，完整键 gateway:rl:user:{aid:123|ip:1.2.3.4}:{epochSecond} */
    private static final String KEY_USER_PREFIX = "gateway:rl:user:";

    /** Redis 客户端，执行 increment + expire 实现固定窗口计数 */
    private final StringRedisTemplate redisTemplate;

    /** 绑定 game.gateway.rate-limit.*：enabled、totalPerSecond、userPerSecond、whitelist */
    private final GatewayRateLimitProperties rateLimitProperties;

    /** 序列化 429 响应 JSON */
    private final ObjectMapper objectMapper;

    /** 白名单路径匹配，默认放行 /actuator/** 健康检查与监控端点 */
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public RateLimitGlobalFilter(
            StringRedisTemplate redisTemplate,
            GatewayRateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 限流主流程：开关 -> 白名单 -> 全站 QPS -> 单用户 QPS -> 放行或 429。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // game.gateway.rate-limit.enabled=false 时关闭限流，压测或故障时可快速放开
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        // 监控端点等路径不计入限流，避免健康检查被误杀
        if (isWhitelisted(path, rateLimitProperties.getWhitelist())) {
            return chain.filter(exchange);
        }
        // 以 UTC 秒为时间桶，同一秒内所有请求共享同一个 Redis 计数键
        long second = Instant.now().getEpochSecond();
        // 全网关每秒请求数超过 totalPerSecond（默认 2000）则拒绝
        if (isTotalLimited(second)) {
            return writeTooManyRequests(exchange, "网关请求过于频繁");
        }
        // 单账号或单 IP 每秒超过 userPerSecond（默认 30）则拒绝
        if (isUserLimited(exchange.getRequest(), second)) {
            return writeTooManyRequests(exchange, "您的请求过于频繁，请稍后再试");
        }
        return chain.filter(exchange);
    }

    /** 顺序 -80：在 AuthGlobalFilter(-90) 之后，此时 X-Account-Id 已写入请求头 */
    @Override
    public int getOrder() {
        return -80;
    }

    /** 与 AuthGlobalFilter 相同逻辑：Ant 模式匹配白名单路径 */
    private boolean isWhitelisted(String path, List<String> whitelist) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (pattern != null && antPathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 全站秒级限流：Redis INCR gateway:rl:total:{second}，首次写入时设置 2 秒 TTL 自动清理旧桶。
     *
     * @param second 当前 Unix 秒时间戳
     * @return true 表示已超过 totalPerSecond 阈值，应返回 429
     */
    private boolean isTotalLimited(long second) {
        int limit = rateLimitProperties.getTotalPerSecond(); // 默认 2000 QPS
        // limit<=0 表示不限制全站流量，直接放行
        if (limit <= 0) {
            return false;
        }
        String key = KEY_TOTAL_PREFIX + second;
        Long current = redisTemplate.opsForValue().increment(key); // 原子自增，多实例网关共享同一计数
        // current==1 说明是该秒第一个请求，设置过期避免 Redis 键无限堆积
        if (current != null && current == 1L) {
            redisTemplate.expire(key, 2, TimeUnit.SECONDS); // 2 秒 TTL 覆盖跨秒边界
        }
        return current != null && current > limit; // 第 limit+1 次请求起被拒绝
    }

    /**
     * 单用户秒级限流：优先按 accountId，未登录则按客户端 IP。
     *
     * @param request 当前 HTTP 请求，含 X-Account-Id 与代理头
     * @param second  当前 Unix 秒
     * @return true 表示该用户/IP 在本秒已超过 userPerSecond
     */
    private boolean isUserLimited(ServerHttpRequest request, long second) {
        int limit = rateLimitProperties.getUserPerSecond(); // 默认 30 QPS/用户
        if (limit <= 0) {
            return false; // 0 或负数表示关闭用户级限流
        }
        String userKey = resolveUserKey(request); // 如 aid:10001 或 ip:192.168.1.1
        String key = KEY_USER_PREFIX + userKey + ":" + second;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, 2, TimeUnit.SECONDS);
        }
        return current != null && current > limit;
    }

    /**
     * 解析限流维度键：已认证用户用 aid:{accountId}，否则用 ip:{clientIp}，无法解析时用 unknown。
     */
    private String resolveUserKey(ServerHttpRequest request) {
        String accountId = request.getHeaders().getFirst(HEADER_ACCOUNT_ID);
        if (accountId != null && !accountId.isBlank()) {
            return "aid:" + accountId; // 登录用户按账号限流，防止单账号刷接口
        }
        String forwarded = request.getHeaders().getFirst(HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For 可能为 "client, proxy1, proxy2"，取第一个为真实客户端 IP
            int index = forwarded.indexOf(',');
            String ip = index > 0 ? forwarded.substring(0, index) : forwarded;
            return "ip:" + ip.trim();
        }
        // 直连网关时从 TCP 连接远端地址取 IP
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return "ip:" + request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown"; // 极端情况下仍参与限流，避免匿名无限请求
    }

    /** 写入 429 JSON 响应，结构与 401 类似：code、message、path、timestamp */
    private Mono<Void> writeTooManyRequests(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(Map.of(
                    "code", HttpStatus.TOO_MANY_REQUESTS.value(),
                    "message", message,
                    "path", exchange.getRequest().getURI().getPath(),
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (JsonProcessingException e) {
            body = ("{\"code\":429,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
