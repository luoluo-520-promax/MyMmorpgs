package cn.itcast.demo.mymmorpg.rpc;

import cn.itcast.demo.mymmorpg.world.puzzle.PhysicsAuthorityService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RSocket 请求桥接：解析二进制请求并委托 PhysicsAuthorityService（异步线程池）。
 */
@Component
public class InternalPhysicsRpcBridge {

    private final PhysicsAuthorityService physicsAuthority;

    public InternalPhysicsRpcBridge(PhysicsAuthorityService physicsAuthority) {
        this.physicsAuthority = physicsAuthority;
    }

    public String handle(String raw) {
        try {
            String[] parts = raw.split("\\|");
            long playerId = Long.parseLong(parts[0]);
            String hash = parts.length > 1 ? parts[1] : "";
            float vx = parts.length > 2 ? Float.parseFloat(parts[2]) : 0f;
            float vy = parts.length > 3 ? Float.parseFloat(parts[3]) : 0f;
            float vz = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float gravity = parts.length > 5 ? Float.parseFloat(parts[5]) : 1f;
            boolean onIce = parts.length > 6 && Boolean.parseBoolean(parts[6]);
            Map<String, Object> result = physicsAuthority.validateHashAsync(
                    playerId, hash, vx, vy, vz, gravity, 0f, 1f, 0f, onIce);
            boolean ok = Boolean.TRUE.equals(result.get("ok"));
            boolean soft = Boolean.TRUE.equals(result.get("softPullback"));
            return "ok=" + ok + ",softPullback=" + soft + ",async=" + result.get("async");
        } catch (Exception e) {
            return "ok=false,error=" + e.getMessage();
        }
    }
}
