/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/service/AuthTokenService.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/service
 * 3) 主要职责：账号级单点登录 Token 签发与校验，Redis 双向映射实现后登录踢前登录。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.service; // 账号级 SSO Token：auth:account:* 与 auth:token:* 双向映射

import org.springframework.beans.factory.annotation.Value; // game.auth.max-online-accounts 与 token-ttl-hours
import org.springframework.data.redis.core.StringRedisTemplate; // 读写 auth:account:* 与 auth:token:* 键
import org.springframework.stereotype.Service; // AccountPlayerService 登录签发；LoginAdmissionManager 统计在线数

import java.time.Duration; // Token TTL，写入 Redis SETEX
import java.util.UUID; // 生成无横线 32 位随机 Token 字符串

/**
 * 账号级统一登录令牌服务（单点登录）。
 * <p>
 * Redis 维护两类键：
 * <ul>
 *     <li>auth:account:{accountId} → token</li>
 *     <li>auth:token:{token} → accountId</li>
 * </ul>
 * 同一账号仅允许一个有效 Token，后登录自动失效旧 Token。
 */
@Service // AccountPlayerService 登录成功时签发；LoginAdmissionManager 统计 auth:account:* 在线数
public class AuthTokenService { // 与 mmorpg-gateway AuthGlobalFilter 共享 auth:token:* 键空间

    /** Redis 账号→Token 映射前缀，完整键 auth:account:{accountId}，值为当前有效 token */
    private static final String KEY_ACCOUNT_PREFIX = "auth:account:"; // SETEX auth:account:{accountId} → token

    /** Redis Token→账号映射前缀，完整键 auth:token:{token}，值为 accountId 字符串 */
    private static final String KEY_TOKEN_PREFIX = "auth:token:"; // SETEX auth:token:{token} → accountId

    /** 与 auth-service / mmorpg-gateway 共享的 Redis 客户端 */
    private final StringRedisTemplate redisTemplate; // opsForValue GET/SET/DEL auth:account 与 auth:token

    /** game.auth.max-online-accounts：auth:account:* 键数量上限，超限拒绝新登录 */
    private final long maxOnlineAccounts; // LoginAdmissionManager.isServerOverloaded 对比阈值

    /** game.auth.token-ttl-hours：Token Redis SETEX 有效期，默认 24 小时 */
    private final Duration ttl; // issueTokenForAccount 写入双向映射的过期时间

    public AuthTokenService( // 账号级统一登录令牌服务（单点登录）
            StringRedisTemplate redisTemplate, // 读写 auth:account 与 auth:token 双向映射
            @Value("${game.auth.max-online-accounts:10000}") long maxOnlineAccounts, // 全服同时持有有效 Token 的账号数上限
            @Value("${game.auth.token-ttl-hours:24}") long tokenTtlHours) { // Redis Token 键 TTL（小时），过期后需重新登录
        this.redisTemplate = redisTemplate; // 与 mmorpg-gateway 共享 Redis 集群
        this.maxOnlineAccounts = maxOnlineAccounts; // LoginAdmissionManager 满载判断阈值
        this.ttl = Duration.ofHours(tokenTtlHours <= 0 ? 24 : tokenTtlHours); // 非法 TTL 回退 24h
    }

    /**
     * 为账号签发新 Token；若已有旧 Token 则先删除 auth:token:{oldToken}，实现单点登录。
     */
    public String issueTokenForAccount(long accountId) { // 为账号签发新 Token；若已有旧 Token 则先删除 auth:token:{oldToken}，实现单点登录
        String accountKey = KEY_ACCOUNT_PREFIX + accountId; // 拼 auth:account:{accountId}
        String oldToken = redisTemplate.opsForValue().get(accountKey); // GET 该账号当前有效 Token
        if (oldToken != null && !oldToken.isEmpty()) { // 该账号已有旧登录会话
            redisTemplate.delete(KEY_TOKEN_PREFIX + oldToken); // DEL auth:token:{oldToken}，踢掉前一次登录
        }
        String token = UUID.randomUUID().toString().replace("-", ""); // 生成 32 位 hex Token 写入 AccountLoginScRsp
        String tokenKey = KEY_TOKEN_PREFIX + token; // 拼 auth:token:{token}
        redisTemplate.opsForValue().set(accountKey, token, ttl); // SETEX auth:account:{accountId} → token
        redisTemplate.opsForValue().set(tokenKey, Long.toString(accountId), ttl); // SETEX auth:token:{token} → accountId
        return token; // 写入 AccountLoginScRsp.token 供客户端后续鉴权与 WebSocket 握手
    }

    /**
     * 根据 Token 反查 accountId，供跨服功能或 WebSocket 会话 accountId 校验。
     */
    public Long getAccountIdByToken(String token) { // 根据 Token 反查 accountId，供跨服功能或 WebSocket 会话 accountId 校验
        if (token == null || token.isEmpty()) { // 请求未携带 Token
            return null; // 无法识别 accountId
        }
        String v = redisTemplate.opsForValue().get(KEY_TOKEN_PREFIX + token); // GET auth:token:{token}
        if (v == null || v.isEmpty()) { // 键不存在或已 TTL 过期
            return null; // 已过期或从未登录
        }
        try { // 解析 Redis auth:token:{token} 值为 accountId Long
            return Long.parseLong(v); // 解析 accountId 字符串
        } catch (NumberFormatException e) { // Redis 值非数字视为无效 Token
            return null; // Redis 值损坏，视为无效 Token
        }
    }

    /**
     * 当前有效 Token 对应账号数是否已达或超过配置上限。
     */
    public boolean isServerOverloaded() { // 当前有效 Token 对应账号数是否已达或超过配置上限
        return countOnlineAccounts() >= maxOnlineAccounts; // auth:account:* 数量 ≥ game.auth.max-online-accounts
    }

    /**
     * 统计当前拥有有效 Token 的账号数量（auth:account:* 键个数）。
     */
    public long countOnlineAccounts() { // 统计当前拥有有效 Token 的账号数量（auth:account:* 键个数）
        var keys = redisTemplate.keys(KEY_ACCOUNT_PREFIX + "*"); // KEYS auth:account:* 扫描在线账号
        return keys == null ? 0 : keys.size(); // 键集合大小即当前持有有效 Token 的账号数
    }
}
