package cn.itcast.demo.mymmorpg.world.reconnect;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 断线重连保护期：重连成功后原地无敌/隐身一段时间，避免加载期间被野怪打死。
 */
@Component
public class ReconnectProtectionService {

    public record Protection(long playerId, long untilMs, boolean invisible, boolean invulnerable) {
        public boolean active(long nowMs) {
            return nowMs < untilMs;
        }
    }

    private final ConcurrentHashMap<Long, Protection> protections = new ConcurrentHashMap<>();
    private volatile long protectionMs = 30_000L;

    public void configure(long protectionMs) {
        this.protectionMs = Math.max(0L, protectionMs);
    }

    public long protectionMs() {
        return protectionMs;
    }

    public Protection grant(long playerId, long nowMs) {
        if (protectionMs <= 0L) {
            return new Protection(playerId, 0L, false, false);
        }
        Protection p = new Protection(playerId, nowMs + protectionMs, true, true);
        protections.put(playerId, p);
        return p;
    }

    public boolean isProtected(long playerId, long nowMs) {
        Protection p = protections.get(playerId);
        if (p == null) {
            return false;
        }
        if (!p.active(nowMs)) {
            protections.remove(playerId, p);
            return false;
        }
        return true;
    }

    public Protection peek(long playerId, long nowMs) {
        Protection p = protections.get(playerId);
        if (p == null) {
            return null;
        }
        if (!p.active(nowMs)) {
            protections.remove(playerId, p);
            return null;
        }
        return p;
    }

    public void clear(long playerId) {
        protections.remove(playerId);
    }

    public Map<String, Object> toView(long playerId, long nowMs) {
        Protection p = peek(playerId, nowMs);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("playerId", playerId);
        m.put("active", p != null);
        if (p != null) {
            m.put("remainMs", Math.max(0L, p.untilMs() - nowMs));
            m.put("invisible", p.invisible());
            m.put("invulnerable", p.invulnerable());
        }
        return m;
    }
}
