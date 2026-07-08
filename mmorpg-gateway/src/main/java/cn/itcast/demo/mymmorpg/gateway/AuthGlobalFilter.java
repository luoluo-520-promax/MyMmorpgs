/**
 * 文件维护说明
 * 1) 文件路径：mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/AuthGlobalFilter.java
 * 2) 所属模块：mmorpg-gateway / main/java/cn/itcast/demo/mymmorpg/gateway
 * 3) 主要职责：全局认证过滤器，校验 Token 并将 accountId 透传给下游微服务。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.gateway;

import com.fasterxml.jackson.core.JsonProcessingException; // Jackson 序列化异常，writeValueAsBytes 失败时捕获
import com.fasterxml.jackson.databind.ObjectMapper; // 将 401 响应体序列化为 JSON 字节数组
import org.springframework.cloud.gateway.filter.GatewayFilterChain; // 过滤器链，调用 chain.filter 将请求继续转发
import org.springframework.cloud.gateway.filter.GlobalFilter; // 全局过滤器接口，对所有路由生效
import org.springframework.core.Ordered; // 定义过滤器执行顺序，数值越小越先执行
import org.springframework.data.redis.core.StringRedisTemplate; // 从 Redis 读取 auth:token:{token} -> accountId 映射
import org.springframework.http.HttpHeaders; // 读取 Authorization / X-Auth-Token 请求头
import org.springframework.http.HttpStatus; // 认证失败时返回 401 UNAUTHORIZED
import org.springframework.http.MediaType; // 设置响应 Content-Type 为 application/json
import org.springframework.http.server.reactive.ServerHttpRequest; // 响应式请求对象，用于 mutate 追加 X-Account-Id 头
import org.springframework.stereotype.Component; // 注册为 Spring Bean，由 Gateway 自动纳入过滤器链
import org.springframework.util.AntPathMatcher; // 支持 /ws、/actuator/** 等 Ant 风格白名单路径匹配
import org.springframework.web.server.ServerWebExchange; // 封装当前 HTTP 请求与响应的上下文
import reactor.core.publisher.Mono; // 响应式返回类型，filter 方法返回 Mono<Void> 表示异步完成

import java.nio.charset.StandardCharsets; // Jackson 失败时的降级 JSON 使用 UTF-8 编码
import java.util.List; // 白名单路径模式列表
import java.util.Map; // 构造 {"code":401,"message":"...","path":"...","timestamp":...} 响应体


@Component // Spring 容器管理，构造器注入 redisTemplate、authProperties、objectMapper
@SuppressWarnings("null") // Reactor/Gateway API 的 @NonNull 注解与静态分析不完全一致，抑制误报警告
public class AuthGlobalFilter implements GlobalFilter, Ordered { // 实现 GlobalFilter 参与网关请求链；Ordered 控制相对 TraceId(-100) 更晚执行

    /** Redis 中存储登录 Token 的键前缀，完整键为 auth:token:{tokenValue}，值为 accountId 字符串 */
    private static final String AUTH_TOKEN_PREFIX = "auth:token:";

    /** 自定义 Token 请求头名，当 Authorization 头不存在时从此头读取 Token */
    private static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    /** 认证通过后写入下游请求的账号 ID 头，后端服务据此识别当前用户，无需再次查 Redis */
    private static final String HEADER_ACCOUNT_ID = "X-Account-Id";

    /** 与 auth-service 共享的 Redis 客户端，用于校验 Token 是否仍有效 */
    private final StringRedisTemplate redisTemplate;

    /** 绑定 game.gateway.auth.* 配置：enabled 开关与白名单路径列表 */
    private final GatewayAuthProperties authProperties;

    /** Spring 容器中的共享 ObjectMapper，保证 JSON 字段命名与全局配置一致 */
    private final ObjectMapper objectMapper;

    /** 白名单路径匹配器，pattern 如 /ws 可匹配精确路径，/public/** 可匹配子路径 */
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * 构造器注入：Gateway 启动时 Spring 自动装配 Redis、配置属性与 JSON 序列化器。
     */
    public AuthGlobalFilter(
            StringRedisTemplate redisTemplate,
            GatewayAuthProperties authProperties,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 网关核心认证逻辑：开关检查 -> 白名单放行 -> 解析 Token -> Redis 校验 -> 注入 X-Account-Id -> 继续链路。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // game.gateway.auth.enabled=false 时整个认证过滤器短路，便于本地调试或紧急降级
        if (!authProperties.isEnabled()) {
            return chain.filter(exchange);
        }
        // 取 URI 路径部分（不含 query），用于白名单匹配，例如 /api/player/1
        String path = exchange.getRequest().getURI().getPath();
        // WebSocket 握手 /ws 等路径无需 Token，直接转发给 ws-service
        if (isWhitelisted(path, authProperties.getWhitelist())) {
            return chain.filter(exchange);
        }
        // 优先从 Authorization: Bearer xxx 解析，否则读 X-Auth-Token 头
        String token = resolveToken(exchange.getRequest().getHeaders());

        // 客户端未携带任何 Token，返回 401 及中文提示「缺少认证令牌」
        if (token == null || token.isBlank()) {
            return writeUnauthorized(exchange, "缺少认证令牌");
        }
        // 用 Token 拼 Redis 键 auth:token:{token}，查不到说明已过期或从未登录
        String accountId = redisTemplate.opsForValue().get(AUTH_TOKEN_PREFIX + token);

        // Redis 无对应 accountId，Token 无效或已登出，返回「认证令牌无效或已过期」
        if (accountId == null || accountId.isBlank()) {
            return writeUnauthorized(exchange, "认证令牌无效或已过期");
        }
        // 基于原请求克隆并追加 X-Account-Id，下游微服务从该头获取当前账号，无需重复鉴权
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER_ACCOUNT_ID, accountId)
                .build();
        // 用携带新请求头的新 exchange 继续过滤器链及路由转发
        return chain.filter(exchange.mutate().request(request).build());
    }

    /**
     * 执行顺序 -90：在 TraceIdGlobalFilter(-100) 之后、RateLimitGlobalFilter(-80) 之前，
     * 确保限流能读到 X-Account-Id，观测过滤器能关联 traceId。
     */
    @Override
    public int getOrder() {
        return -90;
    }

    /**
     * 判断请求路径是否命中白名单中的任一 Ant 模式。
     *
     * @param path      当前请求路径，如 /ws
     * @param whitelist 配置中的模式列表，如 ["/ws", "/actuator/**"]
     * @return true 表示跳过认证
     */
    private boolean isWhitelisted(String path, List<String> whitelist) {
        // 未配置白名单时不放行任何路径，全部走认证
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        // 逐个模式尝试匹配，任一命中即放行
        for (String pattern : whitelist) {
            if (pattern != null && antPathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 HTTP 头解析 Token：标准 OAuth2 Bearer 格式优先，否则读自定义 X-Auth-Token。
     *
     * @param headers 当前请求的 HTTP 头集合
     * @return 纯 Token 字符串（不含 Bearer 前缀），无 Token 时返回 null
     */
    private String resolveToken(HttpHeaders headers) {
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        // regionMatches 忽略大小写比较前 7 字符是否为 "Bearer "，兼容 bearer/Bearer
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7).trim(); // 去掉 "Bearer " 前缀并去除首尾空白
        }
        return headers.getFirst(HEADER_AUTH_TOKEN); // 移动端或 WebSocket 可能只传 X-Auth-Token
    }

    /**
     * 构造 401 JSON 响应并写入响应体，不继续调用 chain.filter。
     *
     * @param exchange 当前请求上下文，用于取 path 和写 response
     * @param message  返回给客户端的中文错误说明
     */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED); // HTTP 状态码 401
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body;
        try {
            // 统一错误 JSON 结构：code、message、path、timestamp，便于前端与日志聚合
            body = objectMapper.writeValueAsBytes(Map.of(
                    "code", HttpStatus.UNAUTHORIZED.value(),
                    "message", message,
                    "path", exchange.getRequest().getURI().getPath(),
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (JsonProcessingException e) {
            // ObjectMapper 异常极少发生，降级为最小 JSON 避免客户端收到空 body
            body = ("{\"code\":401,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        // 将 JSON 字节包装为 DataBuffer，通过响应式 writeWith 写回客户端
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(body)));
    }
}
